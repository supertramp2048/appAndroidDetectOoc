package com.socialapp.core.ui.blocks.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.socialapp.core.network.dto.PostResource;

import java.util.Objects;

/**
 * Immutable snapshot block representing a post item in Feed / Search / Profile lists.
 * Snapshots all displayed fields at construction time so DiffUtil comparisons never suffer
 * from in-place DTO mutations.
 */
public final class FeedPostBlock implements ContentBlock {

    @NonNull
    private final String blockId;
    private final int postId;
    @NonNull
    private final String title;
    @Nullable
    private final String thumbnailUrl;
    private final int authorId;
    @Nullable
    private final String authorName;
    @Nullable
    private final String authorAvatarUrl;
    @Nullable
    private final String categoryName;
    @Nullable
    private final String sourceName;
    @Nullable
    private final String createdAt;
    private final int voteScore;
    private final int userVote;
    private final int commentsCount;
    private final int contentHash;

    public FeedPostBlock(
            int postId,
            @Nullable String title,
            @Nullable String thumbnailUrl,
            int authorId,
            @Nullable String authorName,
            @Nullable String authorAvatarUrl,
            @Nullable String categoryName,
            @Nullable String sourceName,
            @Nullable String createdAt,
            int voteScore,
            int userVote,
            int commentsCount) {
        this.postId = postId;
        this.blockId = "feed_post_" + postId;
        this.title = title != null ? title : "";
        this.thumbnailUrl = normalizeBlankToNull(thumbnailUrl);
        this.authorId = authorId;
        this.authorName = normalizeBlankToNull(authorName);
        this.authorAvatarUrl = normalizeBlankToNull(authorAvatarUrl);
        this.categoryName = normalizeBlankToNull(categoryName);
        this.sourceName = normalizeBlankToNull(sourceName);
        this.createdAt = normalizeBlankToNull(createdAt);
        this.voteScore = voteScore;
        this.userVote = userVote;
        this.commentsCount = commentsCount;
        this.contentHash = Objects.hash(
                this.postId,
                this.title,
                this.thumbnailUrl,
                this.authorId,
                this.authorName,
                this.authorAvatarUrl,
                this.categoryName,
                this.sourceName,
                this.createdAt,
                this.voteScore,
                this.userVote,
                this.commentsCount
        );
    }

    @NonNull
    public static FeedPostBlock fromPostResource(@NonNull PostResource post) {
        int authorId = post.author != null ? post.author.id : -1;
        String authorName = post.author != null ? post.author.name : null;
        String avatarUrl = post.author != null ? post.author.getAvatarUrl() : null;
        String categoryName = post.category != null ? post.category.name : null;

        return new FeedPostBlock(
                post.id,
                post.title,
                post.thumbnailUrl,
                authorId,
                authorName,
                avatarUrl,
                categoryName,
                post.sourceName,
                post.createdAt,
                post.voteScore,
                post.userVote,
                post.commentsCount
        );
    }

    @Nullable
    private static String normalizeBlankToNull(@Nullable String value) {
        if (value == null) return null;
        return value.trim().isEmpty() ? null : value;
    }

    @NonNull
    @Override
    public String getBlockId() {
        return blockId;
    }

    @Override
    public int getBlockType() {
        return TYPE_FEED_POST;
    }

    @Override
    public int getContentHash() {
        return contentHash;
    }

    @Nullable
    @Override
    public Object getChangePayload(@NonNull ContentBlock newItem) {
        if (!(newItem instanceof FeedPostBlock)) {
            return null;
        }
        FeedPostBlock next = (FeedPostBlock) newItem;
        if (postId != next.postId) {
            return null;
        }
        boolean structuralSame = Objects.equals(title, next.title)
                && Objects.equals(thumbnailUrl, next.thumbnailUrl)
                && authorId == next.authorId
                && Objects.equals(authorName, next.authorName)
                && Objects.equals(authorAvatarUrl, next.authorAvatarUrl)
                && Objects.equals(categoryName, next.categoryName)
                && Objects.equals(sourceName, next.sourceName)
                && Objects.equals(createdAt, next.createdAt);

        if (!structuralSame) {
            return null;
        }
        if (voteScore != next.voteScore || userVote != next.userVote || commentsCount != next.commentsCount) {
            return new BlockPayloads.FeedMetricsPayload(next.voteScore, next.userVote, next.commentsCount);
        }
        return null;
    }

    public int getPostId() {
        return postId;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    @Nullable
    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public boolean hasThumbnail() {
        return thumbnailUrl != null;
    }

    public int getAuthorId() {
        return authorId;
    }

    @Nullable
    public String getAuthorName() {
        return authorName;
    }

    @Nullable
    public String getAuthorAvatarUrl() {
        return authorAvatarUrl;
    }

    @Nullable
    public String getCategoryName() {
        return categoryName;
    }

    @Nullable
    public String getSourceName() {
        return sourceName;
    }

    @Nullable
    public String getCreatedAt() {
        return createdAt;
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

    /**
     * Layout-affecting hash (excludes vote/comment metrics since those never alter title wrapping or card height).
     */
    public int getLayoutHash() {
        return Objects.hash(
                postId,
                title,
                thumbnailUrl,
                authorId,
                authorName,
                categoryName,
                sourceName,
                createdAt
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FeedPostBlock)) return false;
        FeedPostBlock that = (FeedPostBlock) o;
        return contentHash == that.contentHash
                && postId == that.postId
                && authorId == that.authorId
                && voteScore == that.voteScore
                && userVote == that.userVote
                && commentsCount == that.commentsCount
                && Objects.equals(title, that.title)
                && Objects.equals(thumbnailUrl, that.thumbnailUrl)
                && Objects.equals(authorName, that.authorName)
                && Objects.equals(authorAvatarUrl, that.authorAvatarUrl)
                && Objects.equals(categoryName, that.categoryName)
                && Objects.equals(sourceName, that.sourceName)
                && Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return contentHash;
    }
}
