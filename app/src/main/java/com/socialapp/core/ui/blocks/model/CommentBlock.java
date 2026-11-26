package com.socialapp.core.ui.blocks.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.socialapp.core.network.dto.CommentResource;

import java.util.Objects;

/**
 * Immutable snapshot block representing a flattened comment item (depth 0..2).
 */
public final class CommentBlock implements ContentBlock {

    public static final int MAX_DEPTH = 2;

    @NonNull
    private final String blockId;
    private final int commentId;
    private final int postId;
    @Nullable
    private final Integer parentId;
    private final int authorId;
    @Nullable
    private final String authorName;
    @Nullable
    private final String authorAvatarUrl;
    @NonNull
    private final String content;
    @Nullable
    private final String status;
    @Nullable
    private final String createdAt;
    @Nullable
    private final String updatedAt;
    private final int repliesCount;
    private final int depth;
    private final boolean repliesExpanded;
    private final int contentHash;

    public CommentBlock(
            int commentId,
            int postId,
            @Nullable Integer parentId,
            int authorId,
            @Nullable String authorName,
            @Nullable String authorAvatarUrl,
            @Nullable String content,
            @Nullable String status,
            @Nullable String createdAt,
            @Nullable String updatedAt,
            int repliesCount,
            int depth,
            boolean repliesExpanded) {
        this.commentId = commentId;
        this.blockId = "comment_" + commentId;
        this.postId = postId;
        this.parentId = parentId;
        this.authorId = authorId;
        this.authorName = authorName != null && !authorName.trim().isEmpty() ? authorName : null;
        this.authorAvatarUrl = authorAvatarUrl != null && !authorAvatarUrl.trim().isEmpty() ? authorAvatarUrl : null;
        this.content = content != null ? content : "";
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.repliesCount = Math.max(0, repliesCount);
        this.depth = Math.max(0, Math.min(depth, MAX_DEPTH));
        this.repliesExpanded = repliesExpanded;
        this.contentHash = Objects.hash(
                this.commentId,
                this.postId,
                this.parentId,
                this.authorId,
                this.authorName,
                this.authorAvatarUrl,
                this.content,
                this.status,
                this.createdAt,
                this.updatedAt,
                this.repliesCount,
                this.depth,
                this.repliesExpanded
        );
    }

    @NonNull
    public static CommentBlock fromCommentResource(@NonNull CommentResource comment, int depth, boolean repliesExpanded) {
        int authorId = comment.author != null ? comment.author.id : -1;
        String authorName = comment.author != null ? comment.author.name : null;
        String avatarUrl = comment.author != null ? comment.author.getAvatarUrl() : null;
        return new CommentBlock(
                comment.id,
                comment.postId,
                comment.parentId,
                authorId,
                authorName,
                avatarUrl,
                comment.content,
                comment.status,
                comment.createdAt,
                comment.updatedAt,
                comment.repliesCount,
                depth,
                repliesExpanded
        );
    }

    @NonNull
    public CommentBlock withRepliesState(boolean expanded, int newRepliesCount) {
        return new CommentBlock(
                commentId, postId, parentId, authorId, authorName, authorAvatarUrl,
                content, status, createdAt, updatedAt, newRepliesCount, depth, expanded
        );
    }

    @NonNull
    public CommentBlock withUpdatedContent(@Nullable String newContent, @Nullable String newUpdatedAt) {
        return new CommentBlock(
                commentId, postId, parentId, authorId, authorName, authorAvatarUrl,
                newContent, status, createdAt, newUpdatedAt, repliesCount, depth, repliesExpanded
        );
    }

    @NonNull
    @Override
    public String getBlockId() {
        return blockId;
    }

    @Override
    public int getBlockType() {
        return TYPE_COMMENT;
    }

    @Override
    public int getContentHash() {
        return contentHash;
    }

    @Nullable
    @Override
    public Object getChangePayload(@NonNull ContentBlock newItem) {
        if (!(newItem instanceof CommentBlock)) {
            return null;
        }
        CommentBlock next = (CommentBlock) newItem;
        if (commentId != next.commentId) {
            return null;
        }

        boolean headerSame = authorId == next.authorId
                && Objects.equals(authorName, next.authorName)
                && Objects.equals(authorAvatarUrl, next.authorAvatarUrl)
                && Objects.equals(createdAt, next.createdAt);

        if (!headerSame) {
            return null;
        }

        boolean bodySame = Objects.equals(content, next.content)
                && Objects.equals(status, next.status)
                && isEdited() == next.isEdited()
                && depth == next.depth;

        if (!bodySame) {
            return new BlockPayloads.CommentContentPayload(next.content, next.status, next.isEdited(), next.depth);
        }

        if (repliesCount != next.repliesCount || repliesExpanded != next.repliesExpanded) {
            return new BlockPayloads.CommentRepliesPayload(next.repliesCount, next.repliesExpanded);
        }

        return null;
    }

    public int getCommentId() {
        return commentId;
    }

    public int getPostId() {
        return postId;
    }

    @Nullable
    public Integer getParentId() {
        return parentId;
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

    @NonNull
    public String getContent() {
        return content;
    }

    @Nullable
    public String getStatus() {
        return status;
    }

    public boolean isRemoved() {
        return status != null && !"published".equalsIgnoreCase(status.trim());
    }

    @Nullable
    public String getCreatedAt() {
        return createdAt;
    }

    @Nullable
    public String getUpdatedAt() {
        return updatedAt;
    }

    public boolean isEdited() {
        return createdAt != null && updatedAt != null && !createdAt.equals(updatedAt);
    }

    public int getRepliesCount() {
        return repliesCount;
    }

    public int getDepth() {
        return depth;
    }

    public boolean isRepliesExpanded() {
        return repliesExpanded;
    }

    /**
     * Layout-affecting hash (excludes repliesCount/repliesExpanded since those stay on the same single action line).
     */
    public int getLayoutHash() {
        return Objects.hash(
                commentId,
                authorId,
                authorName,
                content,
                status,
                createdAt,
                isEdited(),
                depth
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommentBlock)) return false;
        CommentBlock that = (CommentBlock) o;
        return contentHash == that.contentHash
                && commentId == that.commentId
                && postId == that.postId
                && authorId == that.authorId
                && repliesCount == that.repliesCount
                && depth == that.depth
                && repliesExpanded == that.repliesExpanded
                && Objects.equals(parentId, that.parentId)
                && Objects.equals(authorName, that.authorName)
                && Objects.equals(authorAvatarUrl, that.authorAvatarUrl)
                && Objects.equals(content, that.content)
                && Objects.equals(status, that.status)
                && Objects.equals(createdAt, that.createdAt)
                && Objects.equals(updatedAt, that.updatedAt);
    }

    @Override
    public int hashCode() {
        return contentHash;
    }
}
