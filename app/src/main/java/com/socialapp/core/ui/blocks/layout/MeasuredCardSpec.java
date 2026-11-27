package com.socialapp.core.ui.blocks.layout;

import android.text.StaticLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Immutable precomputed layout spec for SinglePassFeedPostView (19 XML views flattened to 1 View).
 */
public final class MeasuredCardSpec extends MeasuredBlockSpec {

    @Nullable
    public final StaticLayout titleLayout;
    @Nullable
    public final String categoryText;
    @NonNull
    public final String authorText;
    @Nullable
    public final String timeText;
    @Nullable
    public final String sourceText;
    @NonNull
    public final String voteText;
    @NonNull
    public final StyleId voteStyleId;
    @NonNull
    public final String commentsText;

    public final boolean hasCategory;
    public final boolean hasAvatar;
    public final boolean hasThumbnail;
    public final boolean hasTime;
    public final boolean hasSource;

    public final int padX;
    public final int thumbnailRadiusPx;
    public final int dividerStrokePx;
    public final int dividerY;

    // Text baseline coordinates
    public final int categoryX;
    public final int categoryBaselineY;
    public final int authorX;
    public final int authorBaselineY;
    public final int timeX;
    public final int timeBaselineY;
    public final int voteX;
    public final int voteBaselineY;
    public final int commentsX;
    public final int commentsBaselineY;
    public final int sourceX;
    public final int sourceBaselineY;

    // Visual & hit-testing rectangles
    @NonNull
    public final BlockRect cardRect;
    @NonNull
    public final BlockRect avatarRect;
    @NonNull
    public final BlockRect avatarTouchRect;
    @NonNull
    public final BlockRect titleRect;
    @NonNull
    public final BlockRect thumbnailRect;
    @NonNull
    public final BlockRect authorRect;
    @NonNull
    public final BlockRect authorTouchRect;
    @NonNull
    public final BlockRect commentsRect;
    @NonNull
    public final BlockRect commentsTouchRect;

    public MeasuredCardSpec(
            @NonNull String blockId,
            int contentHash,
            int targetWidthPx,
            float fontScale,
            int totalHeightPx,
            @Nullable StaticLayout titleLayout,
            @Nullable String categoryText,
            @NonNull String authorText,
            @Nullable String timeText,
            @Nullable String sourceText,
            @NonNull String voteText,
            @NonNull StyleId voteStyleId,
            @NonNull String commentsText,
            boolean hasCategory,
            boolean hasAvatar,
            boolean hasThumbnail,
            boolean hasTime,
            boolean hasSource,
            int padX,
            int thumbnailRadiusPx,
            int dividerStrokePx,
            int dividerY,
            int categoryX,
            int categoryBaselineY,
            int authorX,
            int authorBaselineY,
            int timeX,
            int timeBaselineY,
            int voteX,
            int voteBaselineY,
            int commentsX,
            int commentsBaselineY,
            int sourceX,
            int sourceBaselineY,
            @NonNull BlockRect cardRect,
            @NonNull BlockRect avatarRect,
            @NonNull BlockRect avatarTouchRect,
            @NonNull BlockRect titleRect,
            @NonNull BlockRect thumbnailRect,
            @NonNull BlockRect authorRect,
            @NonNull BlockRect authorTouchRect,
            @NonNull BlockRect commentsRect,
            @NonNull BlockRect commentsTouchRect) {
        super(blockId, contentHash, targetWidthPx, fontScale, StyleId.FEED_TITLE, totalHeightPx);
        this.titleLayout = titleLayout;
        this.categoryText = categoryText;
        this.authorText = authorText;
        this.timeText = timeText;
        this.sourceText = sourceText;
        this.voteText = voteText;
        this.voteStyleId = voteStyleId;
        this.commentsText = commentsText;
        this.hasCategory = hasCategory;
        this.hasAvatar = hasAvatar;
        this.hasThumbnail = hasThumbnail;
        this.hasTime = hasTime;
        this.hasSource = hasSource;
        this.padX = padX;
        this.thumbnailRadiusPx = thumbnailRadiusPx;
        this.dividerStrokePx = dividerStrokePx;
        this.dividerY = dividerY;
        this.categoryX = categoryX;
        this.categoryBaselineY = categoryBaselineY;
        this.authorX = authorX;
        this.authorBaselineY = authorBaselineY;
        this.timeX = timeX;
        this.timeBaselineY = timeBaselineY;
        this.voteX = voteX;
        this.voteBaselineY = voteBaselineY;
        this.commentsX = commentsX;
        this.commentsBaselineY = commentsBaselineY;
        this.sourceX = sourceX;
        this.sourceBaselineY = sourceBaselineY;
        this.cardRect = cardRect;
        this.avatarRect = avatarRect;
        this.avatarTouchRect = avatarTouchRect;
        this.titleRect = titleRect;
        this.thumbnailRect = thumbnailRect;
        this.authorRect = authorRect;
        this.authorTouchRect = authorTouchRect;
        this.commentsRect = commentsRect;
        this.commentsTouchRect = commentsTouchRect;
    }

    /**
     * Reuses precomputed title StaticLayout and geometry while updating vote/comment metrics.
     */
    @NonNull
    public MeasuredCardSpec withUpdatedMetrics(
            int newContentHash,
            @NonNull String newVoteText,
            @NonNull StyleId newVoteStyleId,
            int newVoteX,
            @NonNull String newCommentsText,
            int newCommentsX,
            @NonNull BlockRect newCommentsRect,
            @NonNull BlockRect newCommentsTouchRect) {
        return new MeasuredCardSpec(
                blockId,
                newContentHash,
                targetWidthPx,
                fontScale,
                totalHeightPx,
                titleLayout,
                categoryText,
                authorText,
                timeText,
                sourceText,
                newVoteText,
                newVoteStyleId,
                newCommentsText,
                hasCategory,
                hasAvatar,
                hasThumbnail,
                hasTime,
                hasSource,
                padX,
                thumbnailRadiusPx,
                dividerStrokePx,
                dividerY,
                categoryX,
                categoryBaselineY,
                authorX,
                authorBaselineY,
                timeX,
                timeBaselineY,
                newVoteX,
                voteBaselineY,
                newCommentsX,
                commentsBaselineY,
                sourceX,
                sourceBaselineY,
                cardRect,
                avatarRect,
                avatarTouchRect,
                titleRect,
                thumbnailRect,
                authorRect,
                authorTouchRect,
                newCommentsRect,
                newCommentsTouchRect
        );
    }
}
