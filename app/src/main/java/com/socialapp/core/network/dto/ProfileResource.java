package com.socialapp.core.network.dto;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

public class ProfileResource {

    @SerializedName("id")
    public int id;

    @SerializedName("name")
    @Nullable
    public String name;

    @SerializedName("avatar")
    @Nullable
    public String avatar;

    @SerializedName("avatar_url")
    @Nullable
    public String avatarUrl;

    @SerializedName("role")
    @Nullable
    public String role;

    @SerializedName("user")
    @Nullable
    public UserResource user;

    @SerializedName("cover_photo_url")
    @Nullable
    public String coverPhotoUrl;

    @SerializedName("followers_count")
    public int followersCount;

    @SerializedName("following_count")
    public int followingCount;

    @SerializedName("posts_count")
    public int postsCount;

    @SerializedName("is_following")
    public boolean isFollowing;

    public int getId() {
        if (id > 0) return id;
        if (user != null) return user.id;
        return -1;
    }

    @Nullable
    public String getName() {
        if (name != null && !name.trim().isEmpty()) return name;
        if (user != null) return user.name;
        return "Thành viên";
    }

    @Nullable
    public String getAvatarUrl() {
        if (avatarUrl != null && !avatarUrl.trim().isEmpty()) return avatarUrl;
        if (avatar != null && !avatar.trim().isEmpty()) return avatar;
        if (user != null) return user.getAvatarUrl();
        return null;
    }

    @Nullable
    public String getCoverPhotoUrl() {
        if (coverPhotoUrl != null && !coverPhotoUrl.trim().isEmpty()) return coverPhotoUrl;
        if (user != null) return user.coverPhotoUrl;
        return null;
    }

    @Nullable
    public String getRole() {
        if (role != null && !role.trim().isEmpty()) return role;
        if (user != null) return user.role;
        return "user";
    }
}
