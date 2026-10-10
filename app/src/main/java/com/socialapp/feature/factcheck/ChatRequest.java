package com.socialapp.feature.factcheck;

import com.google.gson.annotations.SerializedName;

public class ChatRequest {

    @SerializedName("query")
    private String query;

    public ChatRequest(String query) {
        this.query = query;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }
}
