package com.socialapp.core.network.dto;

import com.google.gson.annotations.SerializedName;

public class CommentResource {
    @SerializedName("id")
    public int id;

    @SerializedName("content")
    public String content;

    @SerializedName("post_id")
    public int postId;

    @SerializedName("parent_id")
    public Integer parentId;

    @SerializedName("replies_count")
    public int repliesCount;

    @SerializedName("status")
    public String status;

    @SerializedName("created_at")
    public String createdAt;

    @SerializedName("updated_at")
    public String updatedAt;

    @SerializedName("author")
    public UserResource author;
}
