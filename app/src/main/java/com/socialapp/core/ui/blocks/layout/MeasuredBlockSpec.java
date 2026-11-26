package com.socialapp.core.ui.blocks.layout;

import androidx.annotation.NonNull;

/**
 * Base immutable precomputed layout specification stored in L1 BlockLayoutCache.
 */
public abstract class MeasuredBlockSpec {

    @NonNull
    protected final String blockId;
    protected final int contentHash;
    protected final int targetWidthPx;
    protected final float fontScale;
    @NonNull
    protected final StyleId styleId;
    protected final int totalHeightPx;

    protected MeasuredBlockSpec(
            @NonNull String blockId,
            int contentHash,
            int targetWidthPx,
            float fontScale,
            @NonNull StyleId styleId,
            int totalHeightPx) {
        this.blockId = blockId;
        this.contentHash = contentHash;
        this.targetWidthPx = Math.max(0, targetWidthPx);
        this.fontScale = fontScale;
        this.styleId = styleId;
        this.totalHeightPx = Math.max(0, totalHeightPx);
    }

    @NonNull
    public String getBlockId() {
        return blockId;
    }

    public int getContentHash() {
        return contentHash;
    }

    public int getTargetWidthPx() {
        return targetWidthPx;
    }

    public float getFontScale() {
        return fontScale;
    }

    @NonNull
    public StyleId getStyleId() {
        return styleId;
    }

    public int getTotalHeightPx() {
        return totalHeightPx;
    }

    public boolean matchesWidthAndScale(int widthPx, float currentFontScale) {
        return targetWidthPx == widthPx && Math.abs(fontScale - currentFontScale) < 0.001f;
    }
}
