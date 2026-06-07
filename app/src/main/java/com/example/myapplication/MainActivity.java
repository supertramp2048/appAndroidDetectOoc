package com.example.myapplication;

import android.Manifest;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import android.content.Intent;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import com.example.myapplication.databinding.ActivityMainBinding;
import com.google.common.util.concurrent.ListenableFuture;
import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
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
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private ActivityMainBinding binding;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private File currentImageFile;
    private ApiService apiService;
    private Call<PredictionResponse> activeUploadCall;

    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = true;
                for (Boolean granted : result.values()) {
                    if (!granted) {
                        allGranted = false;
                        break;
                    }
                }
                if (allGranted) {
                    startCamera();
                } else {
                    Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<PickVisualMediaRequest> pickMedia =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    Log.d(TAG, "Selected URI: " + uri);
                    handleSelectedImage(uri);
                } else {
                    Log.d(TAG, "No media selected");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initRetrofit();

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            requestPermissions();
        }

        binding.captureButton.setOnClickListener(v -> takePhoto());
        binding.galleryButton.setOnClickListener(v -> pickMedia.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));

        binding.retakeButton.setOnClickListener(v -> retakePhoto());

        binding.submitButton.setOnClickListener(v -> uploadData());
        binding.cancelUploadButton.setOnClickListener(v -> cancelUpload());
        binding.startCaptureButton.setOnClickListener(v -> checkOverlayPermission());

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Log.d(TAG, "Overlay permission NOT granted, requesting...");
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            Toast.makeText(this, "Please enable 'Display over other apps' to use the floating icon", Toast.LENGTH_LONG).show();
        } else {
            Log.d(TAG, "Overlay permission already granted, starting projection request...");
            startMediaProjectionRequest();
        }
    }

    private void startMediaProjectionRequest() {
        Intent intent = new Intent(this, MediaProjectionActivity.class);
        startActivity(intent);
    }

    private void cancelUpload() {
        if (activeUploadCall != null) {
            activeUploadCall.cancel();
            activeUploadCall = null;
            setLoadingState(false);
            Toast.makeText(this, "Upload canceled", Toast.LENGTH_SHORT).show();
        }
    }

    private void setLoadingState(boolean isLoading) {
        binding.submitButton.setVisibility(isLoading ? View.GONE : View.VISIBLE);
        binding.uploadProgressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.cancelUploadButton.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.captureButton.setEnabled(!isLoading);
        binding.galleryButton.setEnabled(!isLoading);
        binding.retakeButton.setEnabled(!isLoading);
    }

    private void initRetrofit() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(300, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://kiendao744-backend-ooc.hf.space/api/v1/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build();

        apiService = retrofit.create(ApiService.class);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.viewFinder.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder().build();

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhoto() {
        if (imageCapture == null) return;

        File photoFile = new File(getExternalCacheDir(), System.currentTimeMillis() + ".jpg");

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                startCrop(Uri.fromFile(photoFile));
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "Photo capture failed: " + exception.getMessage(), exception);
            }
        });
    }

    private void handleSelectedImage(Uri uri) {
        startCrop(uri);
    }

    private void startCrop(@NonNull Uri uri) {
        String destinationFileName = "cropped_image_" + System.currentTimeMillis() + ".jpg";
        UCrop uCrop = UCrop.of(uri, Uri.fromFile(new File(getCacheDir(), destinationFileName)));
        uCrop.withOptions(new UCrop.Options());
        uCrop.start(this);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == UCrop.REQUEST_CROP) {
            final Uri resultUri = UCrop.getOutput(data);
            if (resultUri != null) {
                handleCropResult(resultUri);
            }
        } else if (resultCode == UCrop.RESULT_ERROR) {
            final Throwable cropError = UCrop.getError(data);
            Log.e(TAG, "Crop error: ", cropError);
        }
    }

    private void handleCropResult(@NonNull Uri resultUri) {
        currentImageFile = new File(resultUri.getPath());
        binding.imagePreview.setImageURI(resultUri);
        binding.imagePreview.setVisibility(View.VISIBLE);
        binding.retakeButton.setVisibility(View.VISIBLE);
        binding.submitButton.setEnabled(true);
        binding.captureButton.setVisibility(View.GONE);
        binding.galleryButton.setVisibility(View.GONE);
        Toast.makeText(this, "Photo ready", Toast.LENGTH_SHORT).show();
    }

    private void retakePhoto() {
        if (currentImageFile != null && currentImageFile.exists()) {
            currentImageFile.delete();
        }
        currentImageFile = null;
        binding.imagePreview.setVisibility(View.GONE);
        binding.retakeButton.setVisibility(View.GONE);
        binding.submitButton.setEnabled(false);
        binding.captureButton.setVisibility(View.VISIBLE);
        binding.galleryButton.setVisibility(View.VISIBLE);
        binding.captionEditText.setText("");
    }

    private void copyUriToFile(Uri uri, File file) throws IOException {
        InputStream in = getContentResolver().openInputStream(uri);
        OutputStream out = new FileOutputStream(file);
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        out.close();
        in.close();
    }

    private void uploadData() {
        if (currentImageFile == null) {
            Toast.makeText(this, "Please capture or select an image first", Toast.LENGTH_SHORT).show();
            return;
        }

        String caption = binding.captionEditText.getText().toString();
        
        // Gửi dữ liệu sang Service để xử lý upload ở background
        Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
        serviceIntent.setAction("ACTION_UPLOAD_GALLERY");
        serviceIntent.putExtra("IMAGE_PATH", currentImageFile.getAbsolutePath());
        serviceIntent.putExtra("CAPTION", caption);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        // Thông báo cho người dùng và xóa trạng thái UI cục bộ (tùy chọn)
        Toast.makeText(this, "Upload started in background...", Toast.LENGTH_SHORT).show();
        
        // Reset UI sau khi đã đẩy việc cho Service
        binding.imagePreview.setVisibility(View.GONE);
        binding.retakeButton.setVisibility(View.GONE);
        binding.captureButton.setVisibility(View.VISIBLE);
        binding.galleryButton.setVisibility(View.VISIBLE);
        binding.captionEditText.setText("");
        binding.submitButton.setEnabled(false);
        currentImageFile = null;
    }

    private boolean allPermissionsGranted() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.CAMERA);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        } else {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
        }

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.CAMERA);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        } else {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
        }
        requestPermissionLauncher.launch(permissions.toArray(new String[0]));
    }

    private void showPredictionDialog(PredictionResponse prediction, File imageFile) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_prediction_result, null);
        dialog.setContentView(view);

        // Set Image
        ImageView imageView = view.findViewById(R.id.captured_image_view);
        imageView.setImageURI(Uri.fromFile(imageFile));

        // Set Verdict
        TextView verdictTv = view.findViewById(R.id.verdict_text);
        verdictTv.setText(prediction.verdict);
        if ("FAKE".equalsIgnoreCase(prediction.verdict)) {
            verdictTv.setTextColor(Color.RED);
        } else if ("TRUE".equalsIgnoreCase(prediction.verdict)) {
            verdictTv.setTextColor(Color.GREEN);
        }




        // Set Reasoning
        TextView reasoningTv = view.findViewById(R.id.reasoning_text);
        reasoningTv.setText(prediction.reasoning);

        // Set Articles
        LinearLayout articlesContainer = view.findViewById(R.id.articles_container);
        if (prediction.topArticle != null) {
            addArticleLink(articlesContainer, prediction.topArticle);
        }
        if (prediction.supportingArticles != null) {
            for (PredictionResponse.Article article : prediction.supportingArticles) {
                addArticleLink(articlesContainer, article);
            }
        }

        // Dismiss Behavior: Tap top 1/4 area
        view.findViewById(R.id.top_image_container).setOnClickListener(v -> {
            dialog.dismiss();
            resetAppToOriginalState(imageFile);
        });

        dialog.show();
    }

    private void addArticleLink(LinearLayout container, PredictionResponse.Article article) {
        if (article == null || article.url == null || article.url.isEmpty()) return;

        TextView textView = new TextView(this);
        SpannableString content = new SpannableString(article.title != null ? article.title : "View Source");
        content.setSpan(new UnderlineSpan(), 0, content.length(), 0);
        textView.setText(content);
        textView.setTextColor(Color.BLUE);
        textView.setPadding(0, 24, 0, 24); // More space for easier tapping
        textView.setTextSize(16);
        textView.setClickable(true);
        textView.setFocusable(true);
        textView.setOnTouchListener(null);
        textView.setOnClickListener(v -> {
            try {
                Log.i(TAG, "Opening article link: " + article.url);
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(article.url));
                startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Error opening link", e);
                Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show();
            }
        });
        container.addView(textView);
    }

    private void resetAppToOriginalState(File tempFile) {
        // Clear temporary images
        if (tempFile != null && tempFile.exists()) {
            boolean deleted = tempFile.delete();
            Log.d(TAG, "Temp file deleted: " + deleted);
        }
        currentImageFile = null;
        // Clear UI
        binding.captionEditText.setText("");
        binding.imagePreview.setVisibility(View.GONE);
        binding.retakeButton.setVisibility(View.GONE);
        binding.captureButton.setVisibility(View.VISIBLE);
        binding.galleryButton.setVisibility(View.VISIBLE);
        binding.submitButton.setEnabled(false);
        Toast.makeText(this, "App reset to original state", Toast.LENGTH_SHORT).show();
    }
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
