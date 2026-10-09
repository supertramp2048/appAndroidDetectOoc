package com.socialapp.core.network.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

public class ErrorResponse {
    @SerializedName("message")
    public String message;

    @SerializedName("errors")
    public Map<String, List<String>> errors;
}
