package com.example.myapplication;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class PredictionResponse {
    @SerializedName("verdict")
    public String verdict;
    

    
    @SerializedName("reasoning")
    public String reasoning;
    
    @SerializedName("caption_used")
    public String captionUsed;

    @SerializedName("uploaded_image_url")
    public String uploadedImageUrl;
    
    @SerializedName("top_article")
    public Article topArticle;
    
    @SerializedName("supporting_articles")
    public List<Article> supportingArticles;

    public static class Article {
        @SerializedName("url")
        public String url;
        
        @SerializedName("title")
        public String title;
        
        @SerializedName("snippet")
        public String snippet;
        
        @SerializedName("chunk_text")
        public String chunkText;
        
        @SerializedName("relevance_score")
        public double relevanceScore;
    }
}
