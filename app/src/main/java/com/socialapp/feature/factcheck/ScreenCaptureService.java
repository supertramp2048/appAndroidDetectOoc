package com.socialapp.feature.factcheck;

import android.app.ActivityOptions;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.gson.Gson;
import com.socialapp.MyApplication;
import com.socialapp.R;
import com.socialapp.core.network.FactCheckApiService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ScreenCaptureService extends Service {

    private static final String CHANNEL_ID = "ScreenCaptureChannel";
    public static final String ACTION_INIT_MEDIA_PROJECTION = "ACTION_INIT_MEDIA_PROJECTION";
    public static final String ACTION_EXECUTE_CAPTURE = "ACTION_EXECUTE_CAPTURE";
    public static final String ACTION_SHOW_CAPTION_FOR_IMAGE = "ACTION_SHOW_CAPTION_FOR_IMAGE";
    public static final String ACTION_UPLOAD_GALLERY = "ACTION_UPLOAD_GALLERY";
    public static final String ACTION_CROP_CANCELED = "ACTION_CROP_CANCELED";
    public static final String EXTRA_IMAGE_PATH = "IMAGE_PATH";

    private static volatile boolean isProjectionInitialized = false;

    public static boolean isProjectionReady() {
        return isProjectionInitialized;
    }

    private WindowManager windowManager;
    private View floatingWidget;
    private SelectionOverlayView selectionOverlay;
    private MediaProjection mediaProjection;
    private ImageReader imageReader;
    private VirtualDisplay virtualDisplay;
    private int screenWidth, screenHeight, screenDensity;
    private FactCheckApiService apiService;
    private boolean isUploading = false;
    private boolean pendingStop = false;

    private final MediaProjection.Callback mediaProjectionCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            releaseProjectionResources();
            isProjectionInitialized = false;
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        apiService = MyApplication.getAppContainer(this).getFactCheckApiService();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Screen Capture Service Channel",
                    NotificationManager.IMPORTANCE_HIGH
            );
            serviceChannel.setDescription("Screen capture status and controls");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private void showFloatingIcon() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Chưa cấp quyền hiển thị trên ứng dụng khác", Toast.LENGTH_LONG).show();
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception ignored) {}
            stopSelf();
            return;
        }

        if (windowManager == null) {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        }

        if (floatingWidget != null) {
            floatingWidget.setVisibility(View.VISIBLE);
            return;
        }

        try {
            floatingWidget = LayoutInflater.from(this).inflate(R.layout.floating_widget, null);

            int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                    WindowManager.LayoutParams.TYPE_PHONE;

            final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutFlag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT
            );

            params.gravity = Gravity.TOP | Gravity.START;
            params.x = 200;
            params.y = 200;

            windowManager.addView(floatingWidget, params);

            ImageView captureIcon = floatingWidget.findViewById(R.id.capture_icon);
            ImageView closeIcon = floatingWidget.findViewById(R.id.close_icon);

            closeIcon.setOnClickListener(v -> {
                if (isUploading) {
                    pendingStop = true;
                    if (floatingWidget != null) {
                        floatingWidget.setVisibility(View.GONE);
                    }
                    Toast.makeText(this, "Sẽ tắt sau khi có kết quả tra cứu...", Toast.LENGTH_SHORT).show();
                } else {
                    stopSelf();
                }
            });
            captureIcon.setOnClickListener(v -> toggleSelectionOverlay());

            floatingWidget.setOnTouchListener(new View.OnTouchListener() {
                private int initialX, initialY;
                private float initialTouchX, initialTouchY;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialX = params.x;
                            initialY = params.y;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            params.x = initialX + (int) (event.getRawX() - initialTouchX);
                            params.y = initialY + (int) (event.getRawY() - initialTouchY);
                            windowManager.updateViewLayout(floatingWidget, params);
                            return true;
                    }
                    return false;
                }
            });
        } catch (Exception e) {
            Log.e("ScreenCapture", "Error adding floating widget", e);
        }
    }

    private void toggleSelectionOverlay() {
        if (selectionOverlay != null) {
            windowManager.removeView(selectionOverlay);
            selectionOverlay = null;
            return;
        }

        selectionOverlay = new SelectionOverlayView(this, new SelectionOverlayView.OnRegionSelectedListener() {
            @Override
            public void onRegionConfirmed(Rect rect) {
                if (windowManager != null && selectionOverlay != null) {
                    windowManager.removeView(selectionOverlay);
                }
                selectionOverlay = null;
                if (floatingWidget != null) {
                    floatingWidget.setVisibility(View.GONE);
                }
                new Handler(Looper.getMainLooper()).postDelayed(() -> captureCurrentScreen(rect), 200);
            }

            @Override
            public void onCancel() {
                if (windowManager != null && selectionOverlay != null) {
                    windowManager.removeView(selectionOverlay);
                }
                selectionOverlay = null;
                if (floatingWidget != null) {
                    floatingWidget.setVisibility(View.VISIBLE);
                }
            }
        });

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
        );
        windowManager.addView(selectionOverlay, params);
    }

    private void initMediaProjection(int resCode, Intent pData) {
        if (pData == null) return;

        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (manager == null) return;

        try {
            if (mediaProjection != null) {
                try { mediaProjection.stop(); } catch (Exception ignored) {}
            }
            mediaProjection = manager.getMediaProjection(resCode, pData);
            if (mediaProjection == null) {
                isProjectionInitialized = false;
                return;
            }
            mediaProjection.registerCallback(mediaProjectionCallback, new Handler(Looper.getMainLooper()));
        } catch (Exception e) {
            Log.e("ScreenCapture", "Error obtaining mediaProjection", e);
            isProjectionInitialized = false;
            return;
        }

        updateScreenMetrics();
        releaseCaptureReader();

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);

        try {
            int displayFlags = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR;
            virtualDisplay = mediaProjection.createVirtualDisplay("ScreenCapture",
                    screenWidth, screenHeight, screenDensity,
                    displayFlags,
                    imageReader.getSurface(), null, null);
            isProjectionInitialized = true;
        } catch (Exception e) {
            Log.e("ScreenCapture", "Error creating virtual display", e);
            releaseProjectionResources();
            isProjectionInitialized = false;
            return;
        }

        showFloatingIcon();
    }

    private void captureCurrentScreen(Rect cropRect) {
        if (mediaProjection == null || virtualDisplay == null || imageReader == null) {
            requestFreshMediaProjection(cropRect);
            return;
        }

        Handler mainHandler = new Handler(Looper.getMainLooper());
        final boolean[] frameProcessed = {false};

        Image immediateImage = null;
        try {
            immediateImage = imageReader.acquireLatestImage();
        } catch (Exception ignored) {}

        if (immediateImage != null) {
            frameProcessed[0] = true;
            processImage(immediateImage, cropRect);
            if (floatingWidget != null) {
                floatingWidget.setVisibility(View.VISIBLE);
            }
            return;
        }

        imageReader.setOnImageAvailableListener(reader -> {
            if (frameProcessed[0]) return;

            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image == null) {
                    image = reader.acquireNextImage();
                }
            } catch (Exception ignored) {}

            if (image == null) return;

            frameProcessed[0] = true;
            reader.setOnImageAvailableListener(null, null);

            processImage(image, cropRect);

            mainHandler.post(() -> {
                if (floatingWidget != null) floatingWidget.setVisibility(View.VISIBLE);
            });
        }, mainHandler);

        mainHandler.postDelayed(() -> {
            if (!frameProcessed[0]) {
                frameProcessed[0] = true;
                if (imageReader != null) {
                    imageReader.setOnImageAvailableListener(null, null);
                }
                Toast.makeText(this, "Không thể lấy khung hình, vui lòng thử lại.", Toast.LENGTH_SHORT).show();
                if (floatingWidget != null) floatingWidget.setVisibility(View.VISIBLE);
            }
        }, 1500);
    }

    private void requestFreshMediaProjection(Rect cropRect) {
        if (floatingWidget != null) floatingWidget.setVisibility(View.VISIBLE);
        Intent intent = new Intent(this, MediaProjectionActivity.class);
        if (cropRect != null) {
            intent.putExtra(MediaProjectionActivity.EXTRA_CROP_LEFT, cropRect.left);
            intent.putExtra(MediaProjectionActivity.EXTRA_CROP_TOP, cropRect.top);
            intent.putExtra(MediaProjectionActivity.EXTRA_CROP_RIGHT, cropRight(cropRect));
            intent.putExtra(MediaProjectionActivity.EXTRA_CROP_BOTTOM, cropBottom(cropRect));
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private int cropRight(Rect rect) {
        return rect != null ? rect.right : -1;
    }

    private int cropBottom(Rect rect) {
        return rect != null ? rect.bottom : -1;
    }

    private void releaseCaptureReader() {
        if (virtualDisplay != null) {
            try {
                virtualDisplay.release();
            } catch (Exception ignored) {}
            virtualDisplay = null;
        }
        if (imageReader != null) {
            try {
                imageReader.close();
            } catch (Exception ignored) {}
            imageReader = null;
        }
    }

    private void releaseProjectionResources() {
        releaseCaptureReader();
        if (mediaProjection != null) {
            try {
                mediaProjection.stop();
            } catch (Exception ignored) {}
            mediaProjection = null;
        }
    }

    private void processImage(Image image, Rect cropRect) {
        Bitmap cropped = null;
        try {
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * screenWidth;

            Bitmap bitmap = Bitmap.createBitmap(
                    screenWidth + rowPadding / pixelStride,
                    screenHeight,
                    Bitmap.Config.ARGB_8888
            );
            bitmap.copyPixelsFromBuffer(buffer);

            int left = (cropRect != null && cropRect.left >= 0) ? Math.min(cropRect.left, screenWidth - 1) : 0;
            int top = (cropRect != null && cropRect.top >= 0) ? Math.min(cropRect.top, screenHeight - 1) : 0;
            int width = (cropRect != null && cropRect.width() > 0) ? Math.min(screenWidth - left, cropRect.width()) : (screenWidth - left);
            int height = (cropRect != null && cropRect.height() > 0) ? Math.min(screenHeight - top, cropRect.height()) : (screenHeight - top);

            if (width > 0 && height > 0) {
                cropped = Bitmap.createBitmap(bitmap, left, top, width, height);
            }
            bitmap.recycle();
        } catch (Exception e) {
            Log.e("ScreenCapture", "Error processing image", e);
        } finally {
            image.close();
        }

        if (cropped == null) {
            if (floatingWidget != null) floatingWidget.setVisibility(View.VISIBLE);
            return;
        }

        File savedFile = saveBitmap(cropped);
        if (savedFile != null) {
            openScreenshotCrop(savedFile);
        } else {
            if (floatingWidget != null) floatingWidget.setVisibility(View.VISIBLE);
        }
    }

    private void launchActivityViaPendingIntent(Intent intent) {
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                (int) System.currentTimeMillis(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Bundle options = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            options = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    .toBundle();
        }

        try {
            if (options != null) {
                pendingIntent.send(this, 0, null, null, null, null, options);
            } else {
                pendingIntent.send();
            }
        } catch (Exception e) {
            try {
                startActivity(intent);
            } catch (Exception ignored) {}
        }
    }

    private void openScreenshotCrop(File imageFile) {
        Intent intent = new Intent(this, ScreenshotCropActivity.class);
        intent.putExtra(ScreenshotCropActivity.EXTRA_IMAGE_PATH, imageFile.getAbsolutePath());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        launchActivityViaPendingIntent(intent);
    }

    private void uploadScreenshot(File file, String caption) {
        if (isUploading) {
            Toast.makeText(this, "Đang gửi ảnh, vui lòng chờ kết quả.", Toast.LENGTH_SHORT).show();
            return;
        }

        updateNotification("Uploading screenshot...", false);
        isUploading = true;

        RequestBody captionPart = RequestBody.create(MediaType.parse("text/plain"), caption);
        RequestBody imageRequestBody = RequestBody.create(MediaType.parse("image/png"), file);
        MultipartBody.Part filePart = MultipartBody.Part.createFormData("image", file.getName(), imageRequestBody);

        String mode = caption.isEmpty() ? "combined" : "manual";
        RequestBody modePart = RequestBody.create(MediaType.parse("text/plain"), mode);
        String apiKey = "my_super_secret_key_2026";

        apiService.verifyImage(apiKey, filePart, captionPart, modePart).enqueue(new Callback<PredictionResponse>() {
            @Override
            public void onResponse(Call<PredictionResponse> call, Response<PredictionResponse> response) {
                isUploading = false;
                if (response.isSuccessful() && response.body() != null) {
                    PredictionResponse result = response.body();
                    String imageReference = result.uploadedImageUrl != null && !result.uploadedImageUrl.isEmpty()
                            ? result.uploadedImageUrl
                            : file.getAbsolutePath();

                    try (DatabaseHelper dbHelper = new DatabaseHelper(ScreenCaptureService.this)) {
                        dbHelper.insertHistory(result.verdict, imageReference, new Gson().toJson(result));
                    }

                    String message = "Kết quả: " + result.verdict + ". Nhấn để xem chi tiết.";
                    openResultActivity(result, imageReference);
                    updateNotificationWithResult(message, result, imageReference);

                    Toast.makeText(ScreenCaptureService.this, "Đã có kết quả kiểm chứng!", Toast.LENGTH_LONG).show();

                    if (pendingStop) {
                        stopSelf();
                    } else if (floatingWidget != null) {
                        floatingWidget.setVisibility(View.VISIBLE);
                    }
                } else {
                    updateNotification("Tải lên thất bại. Vui lòng thử lại.", true);
                    if (pendingStop) {
                        stopSelf();
                    } else if (floatingWidget != null) {
                        floatingWidget.setVisibility(View.VISIBLE);
                    }
                }
            }

            @Override
            public void onFailure(Call<PredictionResponse> call, Throwable t) {
                isUploading = false;
                updateNotification("Upload failed.", true);
                Toast.makeText(ScreenCaptureService.this, "Upload error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                if (pendingStop) {
                    stopSelf();
                } else if (floatingWidget != null) {
                    floatingWidget.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void openResultActivity(PredictionResponse result, String imageReference) {
        Intent intent = new Intent(this, ResultActivity.class);
        intent.putExtra("PREDICTION_JSON", new Gson().toJson(result));
        intent.putExtra("IMAGE_PATH", imageReference);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        launchActivityViaPendingIntent(intent);
    }

    private void updateNotificationWithResult(String text, PredictionResponse result, String imageReference) {
        Intent intent = new Intent(this, ResultActivity.class);
        intent.putExtra("PREDICTION_JSON", new Gson().toJson(result));
        intent.putExtra("IMAGE_PATH", imageReference);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, (int) System.currentTimeMillis(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Phát hiện Tin giả - Kết quả")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(false)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(Notification.DEFAULT_ALL)
                .setContentIntent(pendingIntent);

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(2, builder.build());
        }
    }

    private void updateNotification(String text, boolean isResult) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Phát hiện Tin giả")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(!isResult)
                .setAutoCancel(isResult);

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(1, builder.build());
        }
    }

    private File saveBitmap(Bitmap bitmap) {
        String fileName = "Capture_" + System.currentTimeMillis() + ".png";
        File debugFile = new File(getCacheDir(), fileName);

        try (OutputStream out = new FileOutputStream(debugFile)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
        } catch (Exception e) {
            return null;
        }

        return debugFile;
    }

    private void updateScreenMetrics() {
        if (windowManager == null) {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        }
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateScreenMetrics();
        if (virtualDisplay != null && imageReader != null) {
            try {
                imageReader.close();
                imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
                virtualDisplay.resize(screenWidth, screenHeight, screenDensity);
                virtualDisplay.setSurface(imageReader.getSurface());
            } catch (Exception ignored) {}
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();

        if (ACTION_INIT_MEDIA_PROJECTION.equals(action)) {
            int type = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
            }
            setupForegroundNotification("Phát hiện Tin giả", "Bong bóng kiểm tra tin giả đang hoạt động.", type);

            int resCode = intent.getIntExtra("RESULT_CODE", 0);
            Intent pData = intent.getParcelableExtra("DATA");
            initMediaProjection(resCode, pData);
            Toast.makeText(this, "Bong bóng quét tin giả đã sẵn sàng! Chạm biểu tượng nổi để khoanh vùng.", Toast.LENGTH_LONG).show();
        } else if (ACTION_EXECUTE_CAPTURE.equals(action)) {
            int type = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
            }
            setupForegroundNotification("Phát hiện Tin giả", "Đang xử lý ảnh...", type);

            int resCode = intent.getIntExtra("RESULT_CODE", 0);
            Intent pData = intent.getParcelableExtra("DATA");
            int cLeft = intent.getIntExtra(MediaProjectionActivity.EXTRA_CROP_LEFT, -1);
            int cTop = intent.getIntExtra(MediaProjectionActivity.EXTRA_CROP_TOP, -1);
            int cRight = intent.getIntExtra(MediaProjectionActivity.EXTRA_CROP_RIGHT, -1);
            int cBottom = intent.getIntExtra(MediaProjectionActivity.EXTRA_CROP_BOTTOM, -1);

            initMediaProjection(resCode, pData);
            captureCurrentScreen(new Rect(cLeft, cTop, cRight, cBottom));
        } else if (ACTION_SHOW_CAPTION_FOR_IMAGE.equals(action)) {
            String imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH);
            int type = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                type = isProjectionInitialized ?
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION :
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
            }
            setupForegroundNotification("Phát hiện Tin giả", "Đang phân tích ảnh chụp màn hình...", type);
            if (imagePath != null) {
                uploadScreenshot(new File(imagePath), "");
            }
        } else if (ACTION_UPLOAD_GALLERY.equals(action)) {
            String imagePath = intent.getStringExtra("IMAGE_PATH");
            String caption = intent.getStringExtra("CAPTION");
            int type = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                type = isProjectionInitialized ?
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION :
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
            }
            setupForegroundNotification("Phát hiện Tin giả", "Đang tải ảnh từ thư viện...", type);
            if (imagePath != null) {
                uploadScreenshot(new File(imagePath), caption != null ? caption : "");
            }
        } else if (ACTION_CROP_CANCELED.equals(action)) {
            if (floatingWidget != null) {
                floatingWidget.setVisibility(View.VISIBLE);
            }
        } else {
            int type = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                type = isProjectionInitialized ?
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION :
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
            }
            setupForegroundNotification("Phát hiện Tin giả", "Dịch vụ đang chạy.", type);
            showFloatingIcon();
        }

        return START_NOT_STICKY;
    }

    private void setupForegroundNotification(String title, String content, int fgsType) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && fgsType != 0) {
            startForeground(1, notification, fgsType);
        } else {
            startForeground(1, notification);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releaseProjectionResources();
        isProjectionInitialized = false;
        if (floatingWidget != null) {
            try {
                windowManager.removeView(floatingWidget);
            } catch (Exception ignored) {}
            floatingWidget = null;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
