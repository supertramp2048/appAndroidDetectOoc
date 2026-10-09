package com.socialapp.core.network.dto;

import com.google.gson.annotations.SerializedName;

public class CategoryResource {
    @SerializedName("id")
    public int id;

    @SerializedName("name")
    public String name;

    @SerializedName("slug")
    public String slug;

    @SerializedName("description")
    public String description;

    @SerializedName("posts_count")
    public Integer postsCount;
}
