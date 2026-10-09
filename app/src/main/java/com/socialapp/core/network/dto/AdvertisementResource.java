package com.socialapp.core.network.dto;

import com.google.gson.annotations.SerializedName;

public class AdvertisementResource {
    @SerializedName("id")
    public int id;

    @SerializedName("title")
    public String title;

    @SerializedName("image_url")
    public String imageUrl;

    @SerializedName("link_url")
    public String linkUrl;

    @SerializedName("position")
    public String position;
}
