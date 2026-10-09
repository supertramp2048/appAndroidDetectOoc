package com.socialapp.core.network.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class PostListResponse {
    @SerializedName("data")
    public List<PostResource> data;

    @SerializedName("links")
    public ApiLinks links;

    @SerializedName("meta")
    public ApiMeta meta;
}
