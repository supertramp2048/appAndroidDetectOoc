package com.socialapp.feature.factcheck;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.socialapp.MyApplication;
import com.socialapp.R;
import com.socialapp.core.navigation.Navigator;
import com.socialapp.core.network.SocialAppApiService;
import com.socialapp.core.network.dto.ReportRequest;
import com.socialapp.core.session.SessionManager;
import com.socialapp.core.ui.ReportSheetBottomSheet;

import java.io.File;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ResultActivity extends AppCompatActivity {

    public static final String EXTRA_POST_ID = "POST_ID";

    private SessionManager sessionManager;
    private SocialAppApiService socialApiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_prediction_result);

        sessionManager = MyApplication.getAppContainer(this).getSessionManager();
        socialApiService = MyApplication.getAppContainer(this).getSocialAppApiService();

        String predictionJson = getIntent().getStringExtra("PREDICTION_JSON");
        String imageReference = getIntent().getStringExtra("IMAGE_PATH");
        int postId = getIntent().getIntExtra(EXTRA_POST_ID, -1);

        if (predictionJson == null || imageReference == null) {
            finish();
            return;
        }

        PredictionResponse prediction = new Gson().fromJson(predictionJson, PredictionResponse.class);
        setupUI(prediction, imageReference, postId);
    }

    private void setupUI(PredictionResponse prediction, String imageReference, int postId) {
        ImageView imageView = findViewById(R.id.captured_image_view);
        if (imageReference.startsWith("http://") || imageReference.startsWith("https://")) {
            Glide.with(this).load(imageReference).into(imageView);
        } else {
            File imageFile = new File(imageReference);
            if (imageFile.exists()) {
                imageView.setImageURI(Uri.fromFile(imageFile));
            }
        }

        TextView verdictTv = findViewById(R.id.verdict_text);
        verdictTv.setText(formatVerdict(prediction.verdict));
        if ("FAKE".equalsIgnoreCase(prediction.verdict)) {
            verdictTv.setTextColor(getColor(R.color.app_danger));
            verdictTv.setBackgroundResource(R.drawable.bg_chip_danger);
        } else if ("TRUE".equalsIgnoreCase(prediction.verdict)) {
            verdictTv.setTextColor(getColor(R.color.app_success));
            verdictTv.setBackgroundResource(R.drawable.bg_chip_success);
        } else {
            verdictTv.setTextColor(getColor(R.color.app_text_secondary));
            verdictTv.setBackgroundResource(R.drawable.bg_chip_neutral);
        }

        TextView confidenceChip = findViewById(R.id.confidence_percent_chip);
        if (prediction.confidence > 0) {
            int percent = (int) Math.round(prediction.confidence * 100);
            confidenceChip.setText("Độ tin cậy: " + percent + "%");
            confidenceChip.setVisibility(View.VISIBLE);
        } else {
            confidenceChip.setVisibility(View.GONE);
        }

        TextView confidenceTv = findViewById(R.id.confidence_text);
        if (prediction.captionUsed != null && !prediction.captionUsed.trim().isEmpty()) {
            confidenceTv.setText("Caption đã dùng: " + prediction.captionUsed);
            confidenceTv.setVisibility(View.VISIBLE);
        } else {
            confidenceTv.setVisibility(View.GONE);
        }

        TextView reasoningTv = findViewById(R.id.reasoning_text);
        reasoningTv.setText(prediction.reasoning != null && !prediction.reasoning.trim().isEmpty()
                ? prediction.reasoning
                : "Chưa có lập luận chi tiết.");

        LinearLayout articlesContainer = findViewById(R.id.articles_container);
        int articleCount = 0;
        if (prediction.topArticle != null) {
            if (addArticleChunkLink(articlesContainer, prediction.topArticle)) {
                articleCount++;
            }
        }
        if (prediction.supportingArticles != null) {
            for (PredictionResponse.SearchResult article : prediction.supportingArticles) {
                if (addSearchResultLink(articlesContainer, article)) {
                    articleCount++;
                }
            }
        }
        if (articleCount == 0) {
            addEmptySourcesText(articlesContainer);
        }

        Button btnCreateDiscussion = findViewById(R.id.btn_create_discussion);
        btnCreateDiscussion.setOnClickListener(v -> handleCreateDiscussion(prediction, imageReference));

        Button btnReportPost = findViewById(R.id.btn_report_post_from_result);
        if (postId > 0) {
            btnReportPost.setVisibility(View.VISIBLE);
            btnReportPost.setOnClickListener(v -> handleReportPost(postId, prediction.verdict));
        } else {
            btnReportPost.setVisibility(View.GONE);
        }

        Button btnShare = findViewById(R.id.btn_share_result);
        btnShare.setOnClickListener(v -> handleShareResult(prediction));

        findViewById(R.id.top_image_container).setOnClickListener(v -> finish());
        findViewById(R.id.close_result_button).setOnClickListener(v -> finish());
    }

    private boolean addArticleChunkLink(LinearLayout container, PredictionResponse.ArticleChunk article) {
        if (article == null || article.url == null || article.url.trim().isEmpty()) return false;

        TextView textView = new TextView(this);
        String title = article.title != null && !article.title.trim().isEmpty() ? article.title : getString(R.string.view_source);
        if (article.chunkText != null && !article.chunkText.trim().isEmpty()) {
            title = title + "\n" + article.chunkText;
        }
        textView.setText(title);
        textView.setTextColor(getColor(R.color.app_primary));
        textView.setTextSize(14);
        textView.setPadding(dp(14), dp(12), dp(14), dp(12));
        textView.setBackgroundResource(R.drawable.bg_source_item);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(10));
        textView.setLayoutParams(params);

        textView.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(article.url));
                startActivity(intent);
            } catch (Exception ignored) {
                Toast.makeText(this, "Không thể mở liên kết", Toast.LENGTH_SHORT).show();
            }
        });
        container.addView(textView);
        return true;
    }

    private boolean addSearchResultLink(LinearLayout container, PredictionResponse.SearchResult article) {
        if (article == null || article.url == null || article.url.trim().isEmpty()) return false;

        TextView textView = new TextView(this);
        String title = article.title != null && !article.title.trim().isEmpty() ? article.title : getString(R.string.view_source);
        if (article.snippet != null && !article.snippet.trim().isEmpty()) {
            title = title + "\n" + article.snippet;
        }
        textView.setText(title);
        textView.setTextColor(getColor(R.color.app_primary));
        textView.setTextSize(14);
        textView.setPadding(dp(14), dp(12), dp(14), dp(12));
        textView.setBackgroundResource(R.drawable.bg_source_item);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(10));
        textView.setLayoutParams(params);

        textView.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(article.url));
                startActivity(intent);
            } catch (Exception ignored) {
                Toast.makeText(this, "Không thể mở liên kết", Toast.LENGTH_SHORT).show();
            }
        });
        container.addView(textView);
        return true;
    }

    private void addEmptySourcesText(LinearLayout container) {
        TextView textView = new TextView(this);
        textView.setText(R.string.result_sources_empty);
        textView.setTextColor(getColor(R.color.app_text_secondary));
        textView.setTextSize(14);
        textView.setPadding(dp(14), dp(12), dp(14), dp(12));
        textView.setBackgroundResource(R.drawable.bg_source_item);
        container.addView(textView);
    }

    private void handleCreateDiscussion(PredictionResponse prediction, String imageReference) {
        if (!sessionManager.isLoggedIn()) {
            sessionManager.requireLogin(() -> handleCreateDiscussion(prediction, imageReference), this);
            return;
        }

        String suggestedTitle = "Thảo luận kết quả kiểm chứng: " + (prediction.captionUsed != null ? prediction.captionUsed : "Nội dung tin tức");
        String body = "Kết luận kiểm chứng: " + formatVerdict(prediction.verdict)
                + "\nĐộ tin cậy: " + ((int) Math.round(prediction.confidence * 100)) + "%"
                + "\nLập luận: " + (prediction.reasoning != null ? prediction.reasoning : "");

        if (imageReference.startsWith("http://") || imageReference.startsWith("https://")) {
            Navigator.navigateToCreatePost(this, suggestedTitle, imageReference, body);
            finish();
        } else {
            File imageFile = new File(imageReference);
            if (!imageFile.exists()) {
                Navigator.navigateToCreatePost(this, suggestedTitle, null, body);
                finish();
                return;
            }

            Toast.makeText(this, "Đang tải ảnh lên bài viết...", Toast.LENGTH_SHORT).show();
            RequestBody requestFile = RequestBody.create(MediaType.parse("image/*"), imageFile);
            MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", imageFile.getName(), requestFile);

            socialApiService.uploadImage(filePart).enqueue(new Callback<Map<String, String>>() {
                @Override
                public void onResponse(@NonNull Call<Map<String, String>> call, @NonNull Response<Map<String, String>> response) {
                    String uploadedUrl = null;
                    if (response.isSuccessful() && response.body() != null) {
                        uploadedUrl = response.body().get("location");
                    }
                    Navigator.navigateToCreatePost(ResultActivity.this, suggestedTitle, uploadedUrl, body);
                    finish();
                }

                @Override
                public void onFailure(@NonNull Call<Map<String, String>> call, @NonNull Throwable t) {
                    Navigator.navigateToCreatePost(ResultActivity.this, suggestedTitle, null, body);
                    finish();
                }
            });
        }
    }

    private void handleReportPost(int postId, String verdict) {
        ReportSheetBottomSheet reportSheet = ReportSheetBottomSheet.newInstance();
        reportSheet.setOnReportSubmitListener(reason -> {
            socialApiService.reportPost(postId, new ReportRequest(reason)).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                    if (response.isSuccessful()) {
                        Toast.makeText(ResultActivity.this, "Đã gửi báo cáo bài viết.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(ResultActivity.this, "Báo cáo thất bại.", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                    Toast.makeText(ResultActivity.this, "Lỗi kết nối khi báo cáo.", Toast.LENGTH_SHORT).show();
                }
            });
        });
        reportSheet.show(getSupportFragmentManager(), "ReportPostSheet");
    }

    private void handleShareResult(PredictionResponse prediction) {
        String shareContent = "Kết quả kiểm chứng tin tức:\n"
                + "Trạng thái: " + formatVerdict(prediction.verdict) + "\n"
                + "Lập luận: " + (prediction.reasoning != null ? prediction.reasoning : "") + "\n\n"
                + "Kết quả kiểm chứng chỉ mang tính tham khảo.";

        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_TEXT, shareContent);
        startActivity(Intent.createChooser(sendIntent, "Chia sẻ kết quả kiểm chứng"));
    }

    private String formatVerdict(String verdict) {
        if ("FAKE".equalsIgnoreCase(verdict)) {
            return "Có dấu hiệu tin giả";
        }
        if ("TRUE".equalsIgnoreCase(verdict)) {
            return "Có thể tin cậy";
        }
        if (verdict == null || verdict.trim().isEmpty()) {
            return "Chưa xác định";
        }
        return verdict;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
