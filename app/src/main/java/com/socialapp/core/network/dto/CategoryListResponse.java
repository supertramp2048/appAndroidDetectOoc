package com.socialapp.core.network.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class CategoryListResponse {

    @SerializedName("data")
    public List<CategoryResource> data;
}
