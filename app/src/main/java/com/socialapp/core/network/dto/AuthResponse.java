package com.socialapp.core.network.dto;

import com.google.gson.annotations.SerializedName;

public class AuthResponse {
    @SerializedName("token")
    public String token;

    @SerializedName("user")
    public UserResource user;
}
