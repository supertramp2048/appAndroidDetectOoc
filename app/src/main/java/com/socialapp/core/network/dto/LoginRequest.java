package com.socialapp.core.network.dto;

import com.google.gson.annotations.SerializedName;

public class LoginRequest {
    @SerializedName("email")
    public String email;

    @SerializedName("password")
    public String password;

    @SerializedName("remember")
    public Boolean remember;

    public LoginRequest(String email, String password, Boolean remember) {
        this.email = email;
        this.password = password;
        this.remember = remember;
    }
}
