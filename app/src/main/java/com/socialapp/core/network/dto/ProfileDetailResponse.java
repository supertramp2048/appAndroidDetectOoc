package com.socialapp.core.network.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

public class ProfileDetailResponse {

    @SerializedName("data")
    @Nullable
    public ProfileResource data;

    @SerializedName("user")
    @Nullable
    public UserResource user;

    @SerializedName("followers_count")
    public int followersCount;

    @SerializedName("following_count")
    public int followingCount;

    @SerializedName("posts_count")
    public int postsCount;

    @SerializedName("is_following")
    public boolean isFollowing;

    @NonNull
    public ProfileResource getProfile() {
        if (data != null) {
            return data;
        }
        ProfileResource fallback = new ProfileResource();
        fallback.user = user;
        fallback.followersCount = followersCount;
        fallback.followingCount = followingCount;
        fallback.postsCount = postsCount;
        fallback.isFollowing = isFollowing;
        return fallback;
    }
}
