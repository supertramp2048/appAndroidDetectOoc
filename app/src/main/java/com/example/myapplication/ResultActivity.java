package com.example.myapplication;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.UnderlineSpan;
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
        verdictTv.setText(prediction.verdict);
        if ("FAKE".equalsIgnoreCase(prediction.verdict)) {
            verdictTv.setTextColor(Color.RED);
        } else if ("TRUE".equalsIgnoreCase(prediction.verdict)) {
            verdictTv.setTextColor(Color.GREEN);
        }

        // Set Reasoning
        TextView reasoningTv = findViewById(R.id.reasoning_text);
        reasoningTv.setText(prediction.reasoning);

        // Set Articles
        LinearLayout articlesContainer = findViewById(R.id.articles_container);
        if (prediction.topArticle != null) {
            addArticleLink(articlesContainer, prediction.topArticle);
        }
        if (prediction.supportingArticles != null) {
            for (PredictionResponse.Article article : prediction.supportingArticles) {
                addArticleLink(articlesContainer, article);
            }
        }

        // Dismiss Behavior: Tap top 1/4 area or use system back button
        findViewById(R.id.top_image_container).setOnClickListener(v -> finish());
    }

    private void addArticleLink(LinearLayout container, PredictionResponse.Article article) {
        if (article == null || article.url == null || article.url.isEmpty()) return;

        TextView textView = new TextView(this);
        SpannableString content = new SpannableString(article.title != null ? article.title : "View Source");
        content.setSpan(new UnderlineSpan(), 0, content.length(), 0);
        textView.setText(content);
        textView.setTextColor(Color.BLUE);
        textView.setPadding(0, 24, 0, 24);
        textView.setTextSize(16);
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
