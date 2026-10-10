package com.socialapp.core.network.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

public class UserDetailResponse {

    @SerializedName("data")
    @Nullable
    public UserResource data;

    @SerializedName("user")
    @Nullable
    public UserResource user;

    @SerializedName("id")
    public int id;

    @SerializedName("name")
    @Nullable
    public String name;

    @SerializedName("avatar_url")
    @Nullable
    public String avatarUrl;

    @SerializedName("avatar")
    @Nullable
    public String avatar;

    @SerializedName("cover_photo_url")
    @Nullable
    public String coverPhotoUrl;

    @NonNull
    public UserResource getUser() {
        if (data != null) {
            if (data.coverPhotoUrl == null && coverPhotoUrl != null) {
                data.coverPhotoUrl = coverPhotoUrl;
            }
            return data;
        }
        if (user != null) {
            if (user.coverPhotoUrl == null && coverPhotoUrl != null) {
                user.coverPhotoUrl = coverPhotoUrl;
            }
            return user;
        }
        UserResource fallback = new UserResource();
        fallback.id = id;
        fallback.name = name;
        fallback.avatarUrl = avatarUrl;
        fallback.avatar = avatar;
        fallback.coverPhotoUrl = coverPhotoUrl;
        return fallback;
    }
}
