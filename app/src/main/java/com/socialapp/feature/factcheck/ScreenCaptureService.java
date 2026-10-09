package com.socialapp.feature.factcheck;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
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
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.ContextThemeWrapper;
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
    public static final String ACTION_SHOW_CAPTION_FOR_IMAGE = "ACTION_SHOW_CAPTION_FOR_IMAGE";
    public static final String ACTION_CROP_CANCELED = "ACTION_CROP_CANCELED";
    public static final String EXTRA_IMAGE_PATH = "IMAGE_PATH";

    private WindowManager windowManager;
    private View floatingWidget;
    private SelectionOverlayView selectionOverlay;
    private MediaProjection mediaProjection;
    private ImageReader imageReader;
    private VirtualDisplay virtualDisplay;
    private int screenWidth, screenHeight, screenDensity;
    private int resultCode;
    private Intent projectionData;
    private FactCheckApiService apiService;
    private boolean isUploading = false;
    private boolean pendingStop = false;

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
                new Handler(Looper.getMainLooper()).postDelayed(() -> startCapture(rect), 500);
            }

            @Override
            public void onCancel() {
                windowManager.removeView(selectionOverlay);
                selectionOverlay = null;
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

    private void startCapture(Rect cropRect) {
        if (projectionData == null) {
            requestMediaProjectionAgain();
            return;
        }

        updateScreenMetrics();
        if (mediaProjection == null) {
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            try {
                mediaProjection = manager.getMediaProjection(resultCode, projectionData);
            } catch (SecurityException | IllegalStateException e) {
                requestMediaProjectionAgain();
                return;
            }

            if (mediaProjection == null) {
                requestMediaProjectionAgain();
                return;
            }
        }

        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);

        if (virtualDisplay == null) {
            try {
                virtualDisplay = mediaProjection.createVirtualDisplay("ScreenCapture",
                        screenWidth, screenHeight, screenDensity,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        imageReader.getSurface(), null, null);
            } catch (SecurityException | IllegalStateException e) {
                requestMediaProjectionAgain();
                return;
            }
        } else {
            try {
                virtualDisplay.setSurface(imageReader.getSurface());
            } catch (SecurityException | IllegalStateException e) {
                requestMediaProjectionAgain();
                return;
            }
        }

        Handler mainHandler = new Handler(Looper.getMainLooper());
        imageReader.setOnImageAvailableListener(reader -> {
            reader.setOnImageAvailableListener(null, null);
            Image image = reader.acquireLatestImage();
            if (image != null) {
                processImage(image, cropRect);
                mainHandler.post(this::releaseCaptureReader);
            } else {
                mainHandler.post(() -> {
                    if (floatingWidget != null) floatingWidget.setVisibility(View.VISIBLE);
                });
            }
        }, mainHandler);
    }

    private void releaseCaptureReader() {
        if (virtualDisplay != null) {
            try {
                virtualDisplay.setSurface(null);
            } catch (Exception ignored) {}
        }
        if (imageReader != null) {
            try {
                imageReader.close();
            } catch (Exception ignored) {}
            imageReader = null;
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

            int left = Math.max(0, cropRect.left);
            int top = Math.max(0, cropRect.top);
            int width = Math.min(bitmap.getWidth() - left, cropRect.width());
            int height = Math.min(bitmap.getHeight() - top, cropRect.height());

            if (width > 0 && height > 0) {
                cropped = Bitmap.createBitmap(bitmap, left, top, width, height);
            }
            bitmap.recycle();
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

    private void openScreenshotCrop(File imageFile) {
        Intent intent = new Intent(this, ScreenshotCropActivity.class);
        intent.putExtra(ScreenshotCropActivity.EXTRA_IMAGE_PATH, imageFile.getAbsolutePath());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
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

                    DatabaseHelper dbHelper = new DatabaseHelper(ScreenCaptureService.this);
                    dbHelper.insertHistory(result.verdict, imageReference, new Gson().toJson(result));

                    String message = "Kết quả: " + result.verdict + ". Nhấn để xem chi tiết.";
                    if (MyApplication.isAppInForeground()) {
                        openResultActivity(result, imageReference);
                    } else {
                        updateNotificationWithResult(message, result, imageReference);
                    }

                    Toast.makeText(ScreenCaptureService.this, "Đã có kết quả! Vui lòng kiểm tra thông báo.", Toast.LENGTH_LONG).show();

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
        startActivity(intent);
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
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;
    }

    private void requestMediaProjectionAgain() {
        releaseProjectionResources();
        projectionData = null;
        resultCode = 0;
        if (floatingWidget != null) {
            floatingWidget.setVisibility(View.VISIBLE);
        }
        Intent intent = new Intent(this, MediaProjectionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void releaseProjectionResources() {
        if (virtualDisplay != null) {
            try { virtualDisplay.release(); } catch (Exception ignored) {}
            virtualDisplay = null;
        }
        if (imageReader != null) {
            try { imageReader.close(); } catch (Exception ignored) {}
            imageReader = null;
        }
        if (mediaProjection != null) {
            try { mediaProjection.stop(); } catch (Exception ignored) {}
            mediaProjection = null;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();

        if ("START_CAPTURE".equals(action)) {
            int type = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
            }
            setupForegroundNotification("Đang chụp màn hình", "Chạm vào biểu tượng nổi để chọn vùng.", type);
            this.resultCode = intent.getIntExtra("RESULT_CODE", 0);
            this.projectionData = intent.getParcelableExtra("DATA");
            showFloatingIcon();
        } else if ("ACTION_UPLOAD_GALLERY".equals(action)) {
            String imagePath = intent.getStringExtra("IMAGE_PATH");
            String caption = intent.getStringExtra("CAPTION");
            int type = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
            }
            setupForegroundNotification("Phát hiện Tin giả", "Đang tải ảnh từ thư viện...", type);
            if (imagePath != null) {
                uploadScreenshot(new File(imagePath), caption != null ? caption : "");
            }
        } else {
            int type = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
            }
            setupForegroundNotification("Đang chụp màn hình", "Dịch vụ đang chạy.", type);
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
