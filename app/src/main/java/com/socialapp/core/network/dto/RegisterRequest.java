package com.socialapp.core.network.dto;

import com.google.gson.annotations.SerializedName;

public class RegisterRequest {
    @SerializedName("name")
    public String name;

    @SerializedName("email")
    public String email;

    @SerializedName("password")
    public String password;

    @SerializedName("password_confirmation")
    public String passwordConfirmation;

    public RegisterRequest(String name, String email, String password, String passwordConfirmation) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.passwordConfirmation = passwordConfirmation;
    }
}
