package com.socialapp.core.ui.blocks.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.util.DateTimeUtils;

import java.util.Objects;

/**
 * Immutable top header block for PostDetailActivity (Category, Title, Byline, Follow, Cover Thumbnail).
 */
public final class DetailHeaderBlock implements ContentBlock {

    @NonNull
    private final String blockId;
    private final int postId;
    @NonNull
    private final String title;
    @NonNull
    private final String formattedDate;
    @Nullable
    private final String categoryName;
    private final int authorId;
    @Nullable
    private final String authorName;
    @Nullable
    private final String authorAvatarUrl;
    @Nullable
    private final String thumbnailUrl;
    private final boolean ownPost;
    private final boolean followingAuthor;
    private final int contentHash;

    public DetailHeaderBlock(
            int postId,
            @Nullable String title,
            @Nullable String formattedDate,
            @Nullable String categoryName,
            int authorId,
            @Nullable String authorName,
            @Nullable String authorAvatarUrl,
            @Nullable String thumbnailUrl,
            boolean ownPost,
            boolean followingAuthor) {
        this.postId = postId;
        this.blockId = "detail_header_" + postId;
        this.title = title != null ? title : "";
        this.formattedDate = formattedDate != null ? formattedDate : "";
        this.categoryName = normalizeBlank(categoryName);
        this.authorId = authorId;
        this.authorName = normalizeBlank(authorName);
        this.authorAvatarUrl = normalizeBlank(authorAvatarUrl);
        this.thumbnailUrl = normalizeBlank(thumbnailUrl);
        this.ownPost = ownPost;
        this.followingAuthor = followingAuthor;
        this.contentHash = Objects.hash(
                this.postId,
                this.title,
                this.formattedDate,
                this.categoryName,
                this.authorId,
                this.authorName,
                this.authorAvatarUrl,
                this.thumbnailUrl,
                this.ownPost,
                this.followingAuthor
        );
    }

    @NonNull
    public static DetailHeaderBlock fromPostResource(
            @NonNull PostResource post,
            int currentUserId,
            boolean isFollowing) {
        int authorId = post.author != null ? post.author.id : -1;
        String authorName = post.author != null ? post.author.name : null;
        String avatarUrl = post.author != null ? post.author.getAvatarUrl() : null;
        String categoryName = post.category != null ? post.category.name : null;
        boolean isOwn = currentUserId > 0 && authorId == currentUserId;
        String formattedTime = DateTimeUtils.formatFullDateTime(post.createdAt);

        return new DetailHeaderBlock(
                post.id,
                post.title,
                formattedTime,
                categoryName,
                authorId,
                authorName,
                avatarUrl,
                post.thumbnailUrl,
                isOwn,
                isFollowing
        );
    }

    @NonNull
    public DetailHeaderBlock withFollowingAuthor(boolean following) {
        return new DetailHeaderBlock(
                postId, title, formattedDate, categoryName, authorId, authorName,
                authorAvatarUrl, thumbnailUrl, ownPost, following
        );
    }

    @Nullable
    private static String normalizeBlank(@Nullable String val) {
        if (val == null) return null;
        return val.trim().isEmpty() ? null : val;
    }

    @NonNull
    @Override
    public String getBlockId() {
        return blockId;
    }

    @Override
    public int getBlockType() {
        return TYPE_DETAIL_HEADER;
    }

    @Override
    public int getContentHash() {
        return contentHash;
    }

    @Nullable
    @Override
    public Object getChangePayload(@NonNull ContentBlock newItem) {
        if (!(newItem instanceof DetailHeaderBlock)) {
            return null;
        }
        DetailHeaderBlock next = (DetailHeaderBlock) newItem;
        if (postId != next.postId) {
            return null;
        }
        boolean structuralSame = Objects.equals(title, next.title)
                && Objects.equals(formattedDate, next.formattedDate)
                && Objects.equals(categoryName, next.categoryName)
                && authorId == next.authorId
                && Objects.equals(authorName, next.authorName)
                && Objects.equals(authorAvatarUrl, next.authorAvatarUrl)
                && Objects.equals(thumbnailUrl, next.thumbnailUrl)
                && ownPost == next.ownPost;
        if (structuralSame && followingAuthor != next.followingAuthor) {
            return new BlockPayloads.DetailFollowPayload(next.followingAuthor);
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

    @NonNull
    public String getFormattedDate() {
        return formattedDate;
    }

    @Nullable
    public String getCategoryName() {
        return categoryName;
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
    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public boolean isOwnPost() {
        return ownPost;
    }

    public boolean isFollowingAuthor() {
        return followingAuthor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DetailHeaderBlock)) return false;
        DetailHeaderBlock that = (DetailHeaderBlock) o;
        return contentHash == that.contentHash
                && postId == that.postId
                && authorId == that.authorId
                && ownPost == that.ownPost
                && followingAuthor == that.followingAuthor
                && title.equals(that.title)
                && formattedDate.equals(that.formattedDate)
                && Objects.equals(categoryName, that.categoryName)
                && Objects.equals(authorName, that.authorName)
                && Objects.equals(authorAvatarUrl, that.authorAvatarUrl)
                && Objects.equals(thumbnailUrl, that.thumbnailUrl);
    }

    @Override
    public int hashCode() {
        return contentHash;
    }
}
