package com.socialapp.core.network.dto;

import com.google.gson.annotations.SerializedName;

public class VoteResponse {

    @SerializedName("vote_score")
    public Integer voteScore;

    @SerializedName("user_vote")
    public Integer userVote;

    @SerializedName("data")
    public VoteResponse data;

    public int getVoteScore() {
        if (voteScore != null) return voteScore;
        if (data != null && data.voteScore != null) return data.voteScore;
        return 0;
    }

    public int getUserVote() {
        if (userVote != null) return userVote;
        if (data != null && data.userVote != null) return data.userVote;
        return 0;
    }
}
