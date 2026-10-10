package com.socialapp.feature.factcheck;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class PredictionResponse {

    @SerializedName("verdict")
    @Nullable
    public String verdict;

    @SerializedName("confidence")
    public double confidence;

    @SerializedName("reasoning")
    @Nullable
    public String reasoning;

    @SerializedName("caption_used")
    @Nullable
    public String captionUsed;

    @SerializedName("uploaded_image_url")
    @Nullable
    public String uploadedImageUrl;

    @SerializedName("top_article")
    @Nullable
    public ArticleChunk topArticle;

    @SerializedName("supporting_articles")
    @Nullable
    public List<SearchResult> supportingArticles;

    @SerializedName("steps")
    @Nullable
    public List<VerificationStep> steps;

    public static class ArticleChunk {
        @SerializedName("url")
        @Nullable
        public String url;

        @SerializedName("title")
        @Nullable
        public String title;

        @SerializedName("chunk_text")
        @Nullable
        public String chunkText;

        @SerializedName("relevance_score")
        public double relevanceScore;
    }

    public static class SearchResult {
        @SerializedName("url")
        @Nullable
        public String url;

        @SerializedName("title")
        @Nullable
        public String title;

        @SerializedName("snippet")
        @Nullable
        public String snippet;
    }

    public static class VerificationStep {
        @SerializedName("step")
        @Nullable
        public String step;

        @SerializedName("status")
        @Nullable
        public String status;

        @SerializedName("detail")
        @Nullable
        public String detail;
    }
}
