package com.example.myapplication;

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
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import android.app.Dialog;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.style.UnderlineSpan;
import android.widget.TextView;
import android.widget.LinearLayout;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Objects;

public class ScreenCaptureService extends Service {

    private static final String CHANNEL_ID = "ScreenCaptureChannel";
    private WindowManager windowManager;
    private View floatingWidget;
    private SelectionOverlayView selectionOverlay;
    private MediaProjection mediaProjection;
    private ImageReader imageReader;
    private VirtualDisplay virtualDisplay;
    private int screenWidth, screenHeight, screenDensity;
    private int resultCode;
    private Intent projectionData;
    private ApiService apiService;
    private boolean isUploading = false;
    private boolean pendingStop = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        initRetrofit();
    }

    private void initRetrofit() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(300, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)
                .protocols(Collections.singletonList(Protocol.HTTP_1_1))
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://kiendao744-backend-ooc.hf.space/api/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build();

        apiService = retrofit.create(ApiService.class);
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
        Log.d("ScreenCapture", "showFloatingIcon triggered");
        if (!Settings.canDrawOverlays(this)) {
            Log.e("ScreenCapture", "Overlay permission not granted in service");
            stopSelf();
            return;
        }

        if (windowManager == null) {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        }

        if (floatingWidget != null) {
            Log.d("ScreenCapture", "Floating widget already exists, bringing to front");
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
            Log.d("ScreenCapture", "Floating widget added successfully at " + params.x + "," + params.y);

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
        if (projectionData == null) return;

        updateScreenMetrics();
        if (mediaProjection == null) {
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            mediaProjection = manager.getMediaProjection(resultCode, projectionData);

            if (mediaProjection == null) return;

            mediaProjection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    super.onStop();
                    stopProjection();
                }
            }, new Handler(Looper.getMainLooper()));
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }

        if (imageReader == null) {
            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
        }

        if (virtualDisplay == null) {
            virtualDisplay = mediaProjection.createVirtualDisplay("ScreenCapture",
                    screenWidth, screenHeight, screenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, null);
        }
        else {
            // Chỉ cập nhật surface mới, KHÔNG tạo lại virtualDisplay
            virtualDisplay.setSurface(imageReader.getSurface());
        }
        Handler mainHandler = new Handler(Looper.getMainLooper());
        imageReader.setOnImageAvailableListener(reader -> {
            reader.setOnImageAvailableListener(null, null);

            Image image = reader.acquireLatestImage();
            if (image != null) {
                processImage(image, cropRect);
                // Chỉ đóng imageReader, KHÔNG release virtualDisplay
                mainHandler.post(() -> {
                    if (imageReader != null) {
                        imageReader.close();
                        imageReader = null;
                    }
                });
            } else {
                mainHandler.post(() -> {
                    if (floatingWidget != null)
                        floatingWidget.setVisibility(View.VISIBLE);
                });
            }
        }, mainHandler);
    }

    private void releaseCaptureResources() {

        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }

    private void processImage(Image image, Rect cropRect) {
        Log.d("ScreenCapture", "processImage started");
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
            // Copy pixel vào bitmap TRONG KHI image vẫn còn mở
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
            // Đóng image SAU KHI đã copy xong pixel, dù có lỗi hay không
            image.close();
        }

        if (cropped == null) {
            Log.e("ScreenCapture", "Invalid crop dimensions");
            if (floatingWidget != null) floatingWidget.setVisibility(View.VISIBLE);
            return;
        }

        File savedFile = saveBitmap(cropped);
        if (savedFile != null) {
            // KHÔNG dừng MediaProjection ở đây để người dùng có thể chụp tiếp lần sau
            showCaptionDialog(savedFile);
        } else {
            Log.e("ScreenCapture", "Failed to save bitmap");
            if (floatingWidget != null) floatingWidget.setVisibility(View.VISIBLE);
        }
    }

    private void showCaptionDialog(File imageFile) {
        Log.d("ScreenCapture", "showCaptionDialog entered. File: " + imageFile.getAbsolutePath());
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                if (floatingWidget != null) {
                    floatingWidget.setVisibility(View.GONE);
                }
                
                View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_caption, null);
                
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                                WindowManager.LayoutParams.TYPE_PHONE,

                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_DIM_BEHIND |
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                        PixelFormat.TRANSLUCENT
                );
                params.gravity = Gravity.CENTER;
                params.dimAmount = 0.5f;

                windowManager.addView(dialogView, params);
                Log.d("ScreenCapture", "Caption dialog added to windowManager");

                android.widget.Button btnSend = dialogView.findViewById(R.id.btn_send);
                android.widget.Button btnSkip = dialogView.findViewById(R.id.btn_skip);
                android.widget.EditText editText = dialogView.findViewById(R.id.caption_input);

                btnSend.setOnClickListener(v -> {
                    Log.d("ScreenCapture", "Send button clicked");
                    String caption = editText.getText().toString();
                    uploadScreenshot(imageFile, caption);
                    windowManager.removeView(dialogView);
                    if (floatingWidget != null) floatingWidget.setVisibility(View.VISIBLE);
                });

                btnSkip.setOnClickListener(v -> {
                    Log.d("ScreenCapture", "Skip button clicked");
                    uploadScreenshot(imageFile, "");
                    windowManager.removeView(dialogView);
                    if (floatingWidget != null) floatingWidget.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                Log.e("ScreenCapture", "Error in showCaptionDialog", e);
                if (floatingWidget != null) floatingWidget.setVisibility(View.VISIBLE);
            }
        });
    }

    private void uploadScreenshot(File file, String caption) {
        Log.i("ScreenCapture", "NETWORK: Starting upload request...");
        updateNotification("Uploading screenshot...",false);
        isUploading = true;
        
        RequestBody captionPart = RequestBody.create(caption, MediaType.parse("text/plain"));
        RequestBody imageRequestBody = RequestBody.create(file, MediaType.parse("image/png"));
        MultipartBody.Part filePart = MultipartBody.Part.createFormData("image", file.getName(), imageRequestBody);
        
        String mode = caption.isEmpty() ? "combined" : "manual";
        RequestBody modePart = RequestBody.create(mode, MediaType.parse("text/plain"));
        String myApiKey = "my_super_secret_key_2026";

        apiService.verifyImage(myApiKey, filePart, captionPart, modePart).enqueue(new Callback<PredictionResponse>() {
            @Override
            public void onResponse(Call<PredictionResponse> call, Response<PredictionResponse> response) {
                isUploading = false;
                if (response.isSuccessful() && response.body() != null) {
                    PredictionResponse result = response.body();

                    // Lưu vào SQLite (tối đa 15 bản ghi)
                    DatabaseHelper dbHelper = new DatabaseHelper(ScreenCaptureService.this);
                    dbHelper.insertHistory(result.verdict, file.getAbsolutePath(), new Gson().toJson(result));

                    // Gửi thông báo nổi khi có kết quả
                    String message = "Kết quả: " + result.verdict + ". Nhấn để xem chi tiết.";
                    updateNotificationWithResult(message, result, file);

                    // Hiển thị thông báo nhanh để người dùng biết đã có kết quả
                    Toast.makeText(ScreenCaptureService.this, "Đã có kết quả! Vui lòng kiểm tra thông báo.", Toast.LENGTH_LONG).show();
                    
                    // Kiểm tra nếu người dùng đã nhấn X trước đó
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
                Log.e("ScreenCapture", "NETWORK: Connection Failed!", t);
                updateNotification("Upload failed.",true);
                Toast.makeText(ScreenCaptureService.this, "Upload error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                if (pendingStop) {
                    stopSelf();
                } else if (floatingWidget != null) {
                    floatingWidget.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void updateNotificationWithResult(String text, PredictionResponse result, File imageFile) {
        Intent intent = new Intent(this, ResultActivity.class);
        intent.putExtra("PREDICTION_JSON", new Gson().toJson(result));
        intent.putExtra("IMAGE_PATH", imageFile.getAbsolutePath());
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
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true);

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            // Sử dụng một ID duy nhất (ví dụ: 2) thay vì 1 để thông báo không bị xóa khi stopSelf()
            manager.notify(2, builder.build());
        }
    }

    private void updateNotification(String text, boolean isResult) {
        // 1. Tạo Intent để xử lý khi người dùng nhấn vào thông báo
        // Ở đây chúng ta có thể gửi một Broadcast hoặc đơn giản là mở lại MainActivity
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Phát hiện Tin giả")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(!isResult) // Nếu là kết quả thì cho phép gạt bỏ thông báo
                .setAutoCancel(isResult)
                .setContentIntent(pendingIntent);

        if (isResult) {
            // Nếu là kết quả, thiết lập độ ưu tiên cao nhất để nó "nhảy" ra màn hình
            builder.setPriority(NotificationCompat.PRIORITY_MAX)
                    .setDefaults(Notification.DEFAULT_ALL) // Rung và chuông
                    .setFullScreenIntent(pendingIntent, true); // Quan trọng để hiện banner
        } else {
            builder.setPriority(NotificationCompat.PRIORITY_LOW);
        }

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(1, builder.build()); // Sử dụng cùng ID 1 để cập nhật thông báo cũ
        }
    }

    private File saveBitmap(Bitmap bitmap) {
        String fileName = "Capture_" + System.currentTimeMillis() + ".png";
        File debugFile = new File(getCacheDir(), fileName);
        
        try (OutputStream out = new FileOutputStream(debugFile)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            Log.d("ScreenCapture", "File saved for upload: " + debugFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e("ScreenCapture", "Save failed", e);
            return null;
        }

        // Cũng lưu vào gallery trong background (giữ nguyên logic cũ)
        new Thread(() -> {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    android.content.ContentValues values = new android.content.ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ScreenCaptures");
                    Uri imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    if (imageUri != null) {
                        try (OutputStream fos = getContentResolver().openOutputStream(imageUri)) {
                            if (fos != null) {
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }).start();

        return debugFile;
    }

    private void updateScreenMetrics() {
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;
    }

    private void stopProjection() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateScreenMetrics();
        
        if (virtualDisplay != null) {
            if (imageReader != null) imageReader.close();
            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
            
            virtualDisplay.resize(screenWidth, screenHeight, screenDensity);
            virtualDisplay.setSurface(imageReader.getSurface());
        }

        if (selectionOverlay != null) {
            windowManager.removeView(selectionOverlay);
            selectionOverlay = null;
            toggleSelectionOverlay();
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
            
            // Đảm bảo icon nổi luôn hiện khi bắt đầu capture
            showFloatingIcon();
            
            Toast.makeText(this, "Dịch vụ đã sẵn sàng để chụp", Toast.LENGTH_SHORT).show();
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
            // Default foreground setup if no specific action matches but service is started
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
        stopProjection();
        if (floatingWidget != null) {
            try {
                windowManager.removeView(floatingWidget);
            } catch (Exception e) {
                Log.e("ScreenCapture", "Error removing floating widget on destroy", e);
            }
            floatingWidget = null;
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        // Khi người dùng vuốt bỏ ứng dụng khỏi Recent Apps, dừng service ngay lập tức
        stopSelf();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
