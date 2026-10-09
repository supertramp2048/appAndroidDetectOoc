package com.socialapp.core.network.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class SettingResource {
    @SerializedName("logo_url")
    public String logoUrl;

    @SerializedName("background_url")
    public String backgroundUrl;

    @SerializedName("description")
    public String description;

    @SerializedName("copyright")
    public String copyright;

    @SerializedName("links")
    public List<Object> links;

    @SerializedName("socials")
    public List<Object> socials;
}
