package com.socialapp.core.ui.blocks.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Unified immutable block contract for Feed, PostDetail, and Comments RecyclerViews.
 */
public interface ContentBlock {
    int TYPE_FEED_POST = 1;
    int TYPE_AD_SLOT = 2;
    int TYPE_DETAIL_HEADER = 3;
    int TYPE_RICH_TEXT = 4;     // h1..h4, p, ul/ol/li
    int TYPE_QUOTE = 5;         // blockquote
    int TYPE_INLINE_IMAGE = 6;  // img + optional figcaption
    int TYPE_CODE = 7;          // pre / code
    int TYPE_TABLE = 8;         // table (only mounted when HTML contains <table>)
    int TYPE_DETAIL_ACTION = 9; // source link + upvote/downvote/comments/factcheck
    int TYPE_COMMENT = 10;      // comment depth 0..2

    @NonNull
    String getBlockId();

    int getBlockType();

    int getContentHash();

    @Nullable
    Object getChangePayload(@NonNull ContentBlock newItem);
}
