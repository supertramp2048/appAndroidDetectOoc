package com.socialapp.core.ui.blocks.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.socialapp.core.network.dto.PostResource;

import java.util.Objects;

/**
 * Immutable bottom action block for PostDetailActivity (Source attribution, Upvote/Downvote, Comments, Fact-Check).
 */
public final class DetailActionBlock implements ContentBlock {

    @NonNull
    private final String blockId;
    private final int postId;
    @Nullable
    private final String sourceName;
    @Nullable
    private final String sourceUrl;
    private final int voteScore;
    private final int userVote;
    private final int commentsCount;
    private final boolean showFactCheck;
    private final int contentHash;

    public DetailActionBlock(
            int postId,
            @Nullable String sourceName,
            @Nullable String sourceUrl,
            int voteScore,
            int userVote,
            int commentsCount,
            boolean showFactCheck) {
        this.postId = postId;
        this.blockId = "detail_action_" + postId;
        this.sourceName = sourceName != null && !sourceName.trim().isEmpty() ? sourceName : null;
        this.sourceUrl = sourceUrl != null && !sourceUrl.trim().isEmpty() ? sourceUrl : null;
        this.voteScore = voteScore;
        this.userVote = userVote;
        this.commentsCount = commentsCount;
        this.showFactCheck = showFactCheck;
        this.contentHash = Objects.hash(
                this.postId,
                this.sourceName,
                this.sourceUrl,
                this.voteScore,
                this.userVote,
                this.commentsCount,
                this.showFactCheck
        );
    }

    @NonNull
    public static DetailActionBlock fromPostResource(@NonNull PostResource post) {
        boolean hasThumb = post.thumbnailUrl != null && !post.thumbnailUrl.trim().isEmpty();
        return new DetailActionBlock(
                post.id,
                post.sourceName,
                post.sourceUrl,
                post.voteScore,
                post.userVote,
                post.commentsCount,
                hasThumb
        );
    }

    @NonNull
    @Override
    public String getBlockId() {
        return blockId;
    }

    @Override
    public int getBlockType() {
        return TYPE_DETAIL_ACTION;
    }

    @Override
    public int getContentHash() {
        return contentHash;
    }

    @Nullable
    @Override
    public Object getChangePayload(@NonNull ContentBlock newItem) {
        if (!(newItem instanceof DetailActionBlock)) {
            return null;
        }
        DetailActionBlock next = (DetailActionBlock) newItem;
        if (postId != next.postId) {
            return null;
        }
        boolean staticSame = Objects.equals(sourceName, next.sourceName)
                && Objects.equals(sourceUrl, next.sourceUrl)
                && showFactCheck == next.showFactCheck;
        if (staticSame && (voteScore != next.voteScore || userVote != next.userVote || commentsCount != next.commentsCount)) {
            return new BlockPayloads.DetailActionPayload(next.voteScore, next.userVote, next.commentsCount);
        }
        return null;
    }

    public int getPostId() {
        return postId;
    }

    @Nullable
    public String getSourceName() {
        return sourceName;
    }

    @Nullable
    public String getSourceUrl() {
        return sourceUrl;
    }

    public int getVoteScore() {
        return voteScore;
    }

    public int getUserVote() {
        return userVote;
    }

    public int getCommentsCount() {
        return commentsCount;
    }

    public boolean isShowFactCheck() {
        return showFactCheck;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DetailActionBlock)) return false;
        DetailActionBlock that = (DetailActionBlock) o;
        return contentHash == that.contentHash
                && postId == that.postId
                && voteScore == that.voteScore
                && userVote == that.userVote
                && commentsCount == that.commentsCount
                && showFactCheck == that.showFactCheck
                && Objects.equals(sourceName, that.sourceName)
                && Objects.equals(sourceUrl, that.sourceUrl);
    }

    @Override
    public int hashCode() {
        return contentHash;
    }
}
