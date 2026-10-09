package com.socialapp.core.network.dto;

import com.google.gson.annotations.SerializedName;

public class PostResource {
    @SerializedName("id")
    public int id;

    @SerializedName("title")
    public String title;

    @SerializedName("slug")
    public String slug;

    @SerializedName("thumbnail_url")
    public String thumbnailUrl;

    @SerializedName("content_html")
    public String contentHtml;

    @SerializedName("vote_score")
    public int voteScore;

    @SerializedName("user_vote")
    public int userVote; // 1 (upvote), -1 (downvote), 0 (none)

    @SerializedName("comments_count")
    public int commentsCount;

    @SerializedName("created_at")
    public String createdAt;

    @SerializedName("updated_at")
    public String updatedAt;

    @SerializedName("author")
    public UserResource author;

    @SerializedName("category")
    public CategoryResource category;

    @SerializedName("source_name")
    public String sourceName;

    @SerializedName("source_url")
    public String sourceUrl;

    @SerializedName("is_following_author")
    public boolean isFollowingAuthor;
}
