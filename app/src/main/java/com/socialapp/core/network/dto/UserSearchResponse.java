package com.socialapp.core.network.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class UserSearchResponse {
    @SerializedName("data")
    public List<UserResource> data;
}
