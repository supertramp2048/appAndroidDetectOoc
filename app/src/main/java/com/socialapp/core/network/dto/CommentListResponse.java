package com.socialapp.core.network.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class CommentListResponse {

    @SerializedName("data")
    public List<CommentResource> data;

    @SerializedName("links")
    public ApiLinks links;

    @SerializedName("meta")
    public ApiMeta meta;
}
