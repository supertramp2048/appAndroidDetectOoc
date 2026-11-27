package com.socialapp.core.ui.blocks.layout;

import android.text.StaticLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Immutable precomputed layout spec for SinglePassCommentView (17 XML views flattened to 1 View).
 */
public final class MeasuredCommentSpec extends MeasuredBlockSpec {

    public final int depth;
    public final int indentPx;
    public final int padX;

    @NonNull
    public final String authorText;
    @NonNull
    public final String formattedTimeText;
    @Nullable
    public final StaticLayout contentLayout;
    @Nullable
    public final String repliesButtonText;

    public final boolean isEdited;
    public final boolean isRemoved;
    public final boolean hasRepliesButton;

    public final int authorX;
    public final int authorBaselineY;
    public final int timeX;
    public final int timeBaselineY;
    public final int editedX;
    public final int editedBaselineY;
    public final int removedX;
    public final int removedBaselineY;
    public final int replyX;
    public final int replyBaselineY;
    public final int loadRepliesX;
    public final int loadRepliesBaselineY;
    public final int dividerStartX;
    public final int dividerEndX;
    public final int dividerY;
    public final int dividerStrokePx;

    @NonNull
    public final BlockRect avatarRect;
    @NonNull
    public final BlockRect avatarTouchRect;
    @NonNull
    public final BlockRect authorRect;
    @NonNull
    public final BlockRect authorTouchRect;
    @NonNull
    public final BlockRect menuRect;
    @NonNull
    public final BlockRect menuTouchRect;
    @NonNull
    public final BlockRect contentRect;
    @NonNull
    public final BlockRect replyRect;
    @NonNull
    public final BlockRect replyTouchRect;
    @NonNull
    public final BlockRect loadRepliesRect;
    @NonNull
    public final BlockRect loadRepliesTouchRect;

    public MeasuredCommentSpec(
            @NonNull String blockId,
            int contentHash,
            int targetWidthPx,
            float fontScale,
            int totalHeightPx,
            int depth,
            int indentPx,
            int padX,
            @NonNull String authorText,
            @NonNull String formattedTimeText,
            @Nullable StaticLayout contentLayout,
            @Nullable String repliesButtonText,
            boolean isEdited,
            boolean isRemoved,
            boolean hasRepliesButton,
            int authorX,
            int authorBaselineY,
            int timeX,
            int timeBaselineY,
            int editedX,
            int editedBaselineY,
            int removedX,
            int removedBaselineY,
            int replyX,
            int replyBaselineY,
            int loadRepliesX,
            int loadRepliesBaselineY,
            int dividerStartX,
            int dividerEndX,
            int dividerY,
            int dividerStrokePx,
            @NonNull BlockRect avatarRect,
            @NonNull BlockRect avatarTouchRect,
            @NonNull BlockRect authorRect,
            @NonNull BlockRect authorTouchRect,
            @NonNull BlockRect menuRect,
            @NonNull BlockRect menuTouchRect,
            @NonNull BlockRect contentRect,
            @NonNull BlockRect replyRect,
            @NonNull BlockRect replyTouchRect,
            @NonNull BlockRect loadRepliesRect,
            @NonNull BlockRect loadRepliesTouchRect) {
        super(blockId, contentHash, targetWidthPx, fontScale, StyleId.COMMENT_BODY, totalHeightPx);
        this.depth = depth;
        this.indentPx = indentPx;
        this.padX = padX;
        this.authorText = authorText;
        this.formattedTimeText = formattedTimeText;
        this.contentLayout = contentLayout;
        this.repliesButtonText = repliesButtonText;
        this.isEdited = isEdited;
        this.isRemoved = isRemoved;
        this.hasRepliesButton = hasRepliesButton;
        this.authorX = authorX;
        this.authorBaselineY = authorBaselineY;
        this.timeX = timeX;
        this.timeBaselineY = timeBaselineY;
        this.editedX = editedX;
        this.editedBaselineY = editedBaselineY;
        this.removedX = removedX;
        this.removedBaselineY = removedBaselineY;
        this.replyX = replyX;
        this.replyBaselineY = replyBaselineY;
        this.loadRepliesX = loadRepliesX;
        this.loadRepliesBaselineY = loadRepliesBaselineY;
        this.dividerStartX = dividerStartX;
        this.dividerEndX = dividerEndX;
        this.dividerY = dividerY;
        this.dividerStrokePx = dividerStrokePx;
        this.avatarRect = avatarRect;
        this.avatarTouchRect = avatarTouchRect;
        this.authorRect = authorRect;
        this.authorTouchRect = authorTouchRect;
        this.menuRect = menuRect;
        this.menuTouchRect = menuTouchRect;
        this.contentRect = contentRect;
        this.replyRect = replyRect;
        this.replyTouchRect = replyTouchRect;
        this.loadRepliesRect = loadRepliesRect;
        this.loadRepliesTouchRect = loadRepliesTouchRect;
    }

    /**
     * Reuses precomputed content StaticLayout while updating replies expand/collapse button text and touch box.
     */
    @NonNull
    public MeasuredCommentSpec withUpdatedRepliesState(
            int newContentHash,
            @Nullable String newRepliesButtonText,
            boolean newHasRepliesButton,
            @NonNull BlockRect newLoadRepliesRect,
            @NonNull BlockRect newLoadRepliesTouchRect) {
        return new MeasuredCommentSpec(
                blockId,
                newContentHash,
                targetWidthPx,
                fontScale,
                totalHeightPx,
                depth,
                indentPx,
                padX,
                authorText,
                formattedTimeText,
                contentLayout,
                newRepliesButtonText,
                isEdited,
                isRemoved,
                newHasRepliesButton,
                authorX,
                authorBaselineY,
                timeX,
                timeBaselineY,
                editedX,
                editedBaselineY,
                removedX,
                removedBaselineY,
                replyX,
                replyBaselineY,
                loadRepliesX,
                loadRepliesBaselineY,
                dividerStartX,
                dividerEndX,
                dividerY,
                dividerStrokePx,
                avatarRect,
                avatarTouchRect,
                authorRect,
                authorTouchRect,
                menuRect,
                menuTouchRect,
                contentRect,
                replyRect,
                replyTouchRect,
                newLoadRepliesRect,
                newLoadRepliesTouchRect
        );
    }
}
