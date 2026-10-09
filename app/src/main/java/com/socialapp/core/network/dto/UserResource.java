package com.socialapp.core.network.dto;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

public class UserResource {
    @SerializedName("id")
    public int id;

    @SerializedName("name")
    public String name;

    @SerializedName("email")
    @Nullable
    public String email;

    @SerializedName("avatar_url")
    @Nullable
    public String avatarUrl;

    @SerializedName("avatar")
    @Nullable
    public String avatar;

    @SerializedName("email_verified_at")
    @Nullable
    public String emailVerifiedAt;

    @SerializedName("role")
    @Nullable
    public String role;

    @SerializedName("banned_until")
    @Nullable
    public String bannedUntil;

    @SerializedName("ban_reason")
    @Nullable
    public String banReason;

    @Nullable
    public String getAvatarUrl() {
        if (avatarUrl != null && !avatarUrl.trim().isEmpty()) {
            return avatarUrl;
        }
        return avatar;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null && !emailVerifiedAt.trim().isEmpty();
    }

    public boolean isBanned() {
        return bannedUntil != null && !bannedUntil.trim().isEmpty();
    }
}
