package com.socialapp.core.network.dto;

import com.google.gson.annotations.SerializedName;

public class ReportRequest {
    @SerializedName("reason")
    public String reason;

    public ReportRequest(String reason) {
        this.reason = reason;
    }
}
