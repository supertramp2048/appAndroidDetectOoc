package com.socialapp.core.network.dto;

import com.google.gson.annotations.SerializedName;

public class ApiLinks {
    @SerializedName("first")
    public String first;

    @SerializedName("last")
    public String last;

    @SerializedName("prev")
    public String prev;

    @SerializedName("next")
    public String next;

    public boolean hasNext() {
        return next != null && !next.trim().isEmpty();
    }
}
