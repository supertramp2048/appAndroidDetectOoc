package com.socialapp.feature.factcheck;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ChatResponse {

    @SerializedName("answer")
    @Nullable
    public String answer;

    @SerializedName("recommendations")
    @Nullable
    public List<ArticleRec> recommendations;

    public static class ArticleRec {
        @SerializedName("title")
        @Nullable
        public String title;

        @SerializedName("id")
        @Nullable
        public String id;

        @SerializedName("url")
        @Nullable
        public String url;
    }
}
