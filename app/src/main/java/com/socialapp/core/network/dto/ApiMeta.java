package com.socialapp.core.network.dto;

import com.google.gson.annotations.SerializedName;

public class ApiMeta {
    @SerializedName("current_page")
    public int currentPage;

    @SerializedName("from")
    public Integer from;

    @SerializedName("last_page")
    public int lastPage;

    @SerializedName("per_page")
    public int perPage;

    @SerializedName("to")
    public Integer to;

    @SerializedName("total")
    public int total;
}
