package com.socialapp.feature.factcheck;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.socialapp.MyApplication;
import com.socialapp.R;
import com.socialapp.core.network.FactCheckApiService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FactCheckHelperActivity extends AppCompatActivity {

    private Call<PredictionResponse> verifyCall;
    private TextView statusSubtext;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fact_check_helper);

        statusSubtext = findViewById(R.id.factcheck_status_subtext);
        Button btnCancel = findViewById(R.id.btn_cancel_factcheck);

        btnCancel.setOnClickListener(v -> cancelVerification());

        String imageUrl = getIntent().getStringExtra("IMAGE_URL");
        int postId = getIntent().getIntExtra("POST_ID", -1);
        String caption = getIntent().getStringExtra("CAPTION");

        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            Toast.makeText(this, "Không tìm thấy URL hình ảnh để kiểm chứng", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        downloadAndVerifyImage(imageUrl, postId, caption);
    }

    private void downloadAndVerifyImage(String imageUrl, int postId, @Nullable String caption) {
        if (statusSubtext != null) {
            statusSubtext.setText("Đang nạp hình ảnh từ liên kết...");
        }

        new Thread(() -> {
            try {
                File cacheFile = new File(getCacheDir(), "factcheck_temp_" + System.currentTimeMillis() + ".jpg");
                URL url = new URL(imageUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();

                InputStream inputStream = connection.getInputStream();
                FileOutputStream outputStream = new FileOutputStream(cacheFile);
                byte[] buffer = new byte[1024];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                outputStream.flush();
                outputStream.close();
                inputStream.close();

                runOnUiThread(() -> {
                    if (statusSubtext != null) {
                        statusSubtext.setText("Hệ thống AI đang quét hình ảnh và đối sánh ngữ cảnh bài viết...");
                    }
                    executeVerification(cacheFile, imageUrl, postId, caption);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Lỗi nạp hình ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        }).start();
    }

    private void executeVerification(File file, String imageUrl, int postId, @Nullable String caption) {
        FactCheckApiService apiService = MyApplication.getAppContainer(this).getFactCheckApiService();

        boolean hasCaption = caption != null && !caption.trim().isEmpty();
        String mode = hasCaption ? "manual" : "combined";
        String captionText = hasCaption ? caption : "";

        RequestBody captionPart = RequestBody.create(MediaType.parse("text/plain"), captionText);
        RequestBody imageRequestBody = RequestBody.create(MediaType.parse("image/jpeg"), file);
        MultipartBody.Part filePart = MultipartBody.Part.createFormData("image", file.getName(), imageRequestBody);
        RequestBody modePart = RequestBody.create(MediaType.parse("text/plain"), mode);
        String apiKey = "my_super_secret_key_2026";

        verifyCall = apiService.verifyImage(apiKey, filePart, captionPart, modePart);
        verifyCall.enqueue(new Callback<PredictionResponse>() {
            @Override
            public void onResponse(@NonNull Call<PredictionResponse> call, @NonNull Response<PredictionResponse> response) {
                if (isFinishing() || isDestroyed()) return;

                if (response.isSuccessful() && response.body() != null) {
                    PredictionResponse result = response.body();
                    try (DatabaseHelper dbHelper = new DatabaseHelper(FactCheckHelperActivity.this)) {
                        dbHelper.insertHistory(result.verdict, imageUrl, new Gson().toJson(result));
                    }

                    Intent intent = new Intent(FactCheckHelperActivity.class.cast(FactCheckHelperActivity.this), ResultActivity.class);
                    intent.putExtra("PREDICTION_JSON", new Gson().toJson(result));
                    intent.putExtra("IMAGE_PATH", imageUrl);
                    intent.putExtra(ResultActivity.EXTRA_POST_ID, postId);
                    startActivity(intent);
                } else {
                    Toast.makeText(FactCheckHelperActivity.this, "Kiểm chứng thất bại từ server.", Toast.LENGTH_SHORT).show();
                }
                finish();
            }

            @Override
            public void onFailure(@NonNull Call<PredictionResponse> call, @NonNull Throwable t) {
                if (isFinishing() || isDestroyed() || call.isCanceled()) return;

                Toast.makeText(FactCheckHelperActivity.this, "Lỗi kết nối server kiểm chứng: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void cancelVerification() {
        if (verifyCall != null && !verifyCall.isCanceled()) {
            verifyCall.cancel();
        }
        Toast.makeText(this, "Đã hủy kiểm chứng", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (verifyCall != null && !verifyCall.isCanceled()) {
            verifyCall.cancel();
        }
    }
}
