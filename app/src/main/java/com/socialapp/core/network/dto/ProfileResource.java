package com.socialapp.core.network.dto;

import com.google.gson.annotations.SerializedName;

public class ProfileResource {
    @SerializedName("user")
    public UserResource user;

    @SerializedName("cover_photo_url")
    public String coverPhotoUrl;

    @SerializedName("followers_count")
    public int followersCount;

    @SerializedName("following_count")
    public int followingCount;

    @SerializedName("posts_count")
    public int postsCount;

    @SerializedName("is_following")
    public boolean isFollowing;
}
