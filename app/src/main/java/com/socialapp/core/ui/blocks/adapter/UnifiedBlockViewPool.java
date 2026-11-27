package com.socialapp.core.ui.blocks.adapter;

import androidx.recyclerview.widget.RecyclerView;

import com.socialapp.core.ui.blocks.model.ContentBlock;

/**
 * Shared L3 RecycledViewPool reused across FeedFragment, SearchFragment, ProfileFragment,
 * ProfileActivity, CommentsActivity, and PostDetailActivity so ViewHolders are not recreated
 * when switching tabs or screens.
 */
public class UnifiedBlockViewPool extends RecyclerView.RecycledViewPool {

    private static final int CAP_FEED_POST = 16;
    private static final int CAP_AD_SLOT = 4;
    private static final int CAP_DETAIL_HEADER = 2;
    private static final int CAP_RICH_TEXT = 20;
    private static final int CAP_QUOTE = 6;
    private static final int CAP_INLINE_IMAGE = 8;
    private static final int CAP_CODE = 4;
    private static final int CAP_TABLE = 3;
    private static final int CAP_DETAIL_ACTION = 2;
    private static final int CAP_COMMENT = 20;

    private volatile boolean trimmedForPressure = false;

    public UnifiedBlockViewPool() {
        restoreDefaultCapacities();
    }

    public void restoreDefaultCapacities() {
        trimmedForPressure = false;
        setMaxRecycledViews(ContentBlock.TYPE_FEED_POST, CAP_FEED_POST);
        setMaxRecycledViews(ContentBlock.TYPE_AD_SLOT, CAP_AD_SLOT);
        setMaxRecycledViews(ContentBlock.TYPE_DETAIL_HEADER, CAP_DETAIL_HEADER);
        setMaxRecycledViews(ContentBlock.TYPE_RICH_TEXT, CAP_RICH_TEXT);
        setMaxRecycledViews(ContentBlock.TYPE_QUOTE, CAP_QUOTE);
        setMaxRecycledViews(ContentBlock.TYPE_INLINE_IMAGE, CAP_INLINE_IMAGE);
        setMaxRecycledViews(ContentBlock.TYPE_CODE, CAP_CODE);
        setMaxRecycledViews(ContentBlock.TYPE_TABLE, CAP_TABLE);
        setMaxRecycledViews(ContentBlock.TYPE_DETAIL_ACTION, CAP_DETAIL_ACTION);
        setMaxRecycledViews(ContentBlock.TYPE_COMMENT, CAP_COMMENT);
    }

    /**
     * Cuts recycled ViewHolder retention in half during TRIM_MEMORY_RUNNING_MODERATE.
     * Setting a smaller max automatically discards excess pooled ViewHolders immediately.
     */
    public void trimForMemoryPressure() {
        trimmedForPressure = true;
        setMaxRecycledViews(ContentBlock.TYPE_FEED_POST, Math.max(2, CAP_FEED_POST / 2));
        setMaxRecycledViews(ContentBlock.TYPE_AD_SLOT, Math.max(1, CAP_AD_SLOT / 2));
        setMaxRecycledViews(ContentBlock.TYPE_DETAIL_HEADER, 1);
        setMaxRecycledViews(ContentBlock.TYPE_RICH_TEXT, Math.max(4, CAP_RICH_TEXT / 2));
        setMaxRecycledViews(ContentBlock.TYPE_QUOTE, Math.max(1, CAP_QUOTE / 2));
        setMaxRecycledViews(ContentBlock.TYPE_INLINE_IMAGE, Math.max(2, CAP_INLINE_IMAGE / 2));
        setMaxRecycledViews(ContentBlock.TYPE_CODE, Math.max(1, CAP_CODE / 2));
        setMaxRecycledViews(ContentBlock.TYPE_TABLE, 1);
        setMaxRecycledViews(ContentBlock.TYPE_DETAIL_ACTION, 1);
        setMaxRecycledViews(ContentBlock.TYPE_COMMENT, Math.max(4, CAP_COMMENT / 2));
    }

    public boolean isTrimmedForPressure() {
        return trimmedForPressure;
    }

    @Override
    public void clear() {
        super.clear();
        trimmedForPressure = true;
    }
}
