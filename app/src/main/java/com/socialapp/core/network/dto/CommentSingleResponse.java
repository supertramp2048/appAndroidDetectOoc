package com.socialapp.core.network.dto;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

public class CommentSingleResponse {

    @SerializedName("data")
    @Nullable
    public CommentResource data;

    @SerializedName("id")
    public int id;

    @SerializedName("content")
    public String content;

    @Nullable
    public CommentResource getComment() {
        if (data != null) {
            return data;
        }
        if (content != null) {
            CommentResource fallback = new CommentResource();
            fallback.id = id;
            fallback.content = content;
            return fallback;
        }
        return null;
    }
}
