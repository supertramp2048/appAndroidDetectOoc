package com.example.myapplication;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;

import java.io.File;

public class ResultActivity extends AppCompatActivity {
    private static final String TAG = "ResultActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_prediction_result);

        String predictionJson = getIntent().getStringExtra("PREDICTION_JSON");
        String imageReference = getIntent().getStringExtra("IMAGE_PATH");

        if (predictionJson == null || imageReference == null) {
            finish();
            return;
        }

        PredictionResponse prediction = new Gson().fromJson(predictionJson, PredictionResponse.class);

        setupUI(prediction, imageReference);
    }

    private void setupUI(PredictionResponse prediction, String imageReference) {
        // Set Image
        ImageView imageView = findViewById(R.id.captured_image_view);
        if (RemoteImageLoader.isRemoteUrl(imageReference)) {
            RemoteImageLoader.load(imageView, imageReference, android.R.drawable.ic_menu_gallery);
        } else {
            File imageFile = new File(imageReference);
            if (imageFile.exists()) {
                imageView.setImageURI(Uri.fromFile(imageFile));
            }
        }

        // Set Verdict
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

        TextView confidenceTv = findViewById(R.id.confidence_text);
        if (prediction.captionUsed != null && !prediction.captionUsed.trim().isEmpty()) {
            confidenceTv.setText("Caption đã dùng: " + prediction.captionUsed);
            confidenceTv.setVisibility(View.VISIBLE);
        } else {
            confidenceTv.setVisibility(View.GONE);
        }

        // Set Reasoning
        TextView reasoningTv = findViewById(R.id.reasoning_text);
        reasoningTv.setText(prediction.reasoning != null && !prediction.reasoning.trim().isEmpty()
                ? prediction.reasoning
                : "Chưa có lập luận chi tiết.");

        // Set Articles
        LinearLayout articlesContainer = findViewById(R.id.articles_container);
        int articleCount = 0;
        if (prediction.topArticle != null) {
            if (addArticleLink(articlesContainer, prediction.topArticle)) {
                articleCount++;
            }
        }
        if (prediction.supportingArticles != null) {
            for (PredictionResponse.Article article : prediction.supportingArticles) {
                if (addArticleLink(articlesContainer, article)) {
                    articleCount++;
                }
            }
        }
        if (articleCount == 0) {
            addEmptySourcesText(articlesContainer);
        }

        // Dismiss Behavior: Tap top 1/4 area or use system back button
        findViewById(R.id.top_image_container).setOnClickListener(v -> finish());
        findViewById(R.id.close_result_button).setOnClickListener(v -> finish());
    }

    private boolean addArticleLink(LinearLayout container, PredictionResponse.Article article) {
        if (article == null || article.url == null || article.url.isEmpty()) return false;

        TextView textView = new TextView(this);
        String title = article.title != null && !article.title.trim().isEmpty()
                ? article.title
                : getString(R.string.view_source);
        if (article.snippet != null && !article.snippet.trim().isEmpty()) {
            title = title + "\n" + article.snippet;
        }
        textView.setText(title);
        textView.setTextColor(getColor(R.color.app_primary));
        textView.setTextSize(15);
        textView.setLineSpacing(dp(2), 1f);
        textView.setPadding(dp(14), dp(12), dp(14), dp(12));
        textView.setBackgroundResource(R.drawable.bg_source_item);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(10));
        textView.setLayoutParams(params);
        textView.setClickable(true);
        textView.setFocusable(true);
        
        textView.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(article.url));
                startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Error opening link", e);
                Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show();
            }
        });
        container.addView(textView);
        return true;
    }

    private void addEmptySourcesText(LinearLayout container) {
        TextView textView = new TextView(this);
        textView.setText(R.string.result_sources_empty);
        textView.setTextColor(getColor(R.color.app_text_secondary));
        textView.setTextSize(15);
        textView.setPadding(dp(14), dp(12), dp(14), dp(12));
        textView.setBackgroundResource(R.drawable.bg_source_item);
        container.addView(textView);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Optionally delete the temp file here if it's no longer needed
        String imagePath = getIntent().getStringExtra("IMAGE_PATH");
        if (imagePath != null && !RemoteImageLoader.isRemoteUrl(imagePath)) {
            File file = new File(imagePath);
            if (file.exists() && file.getName().startsWith("Capture_")) {
                file.delete();
            }
        }
    }
}
