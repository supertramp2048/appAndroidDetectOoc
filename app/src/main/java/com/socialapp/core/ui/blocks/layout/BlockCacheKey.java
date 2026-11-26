package com.socialapp.core.ui.blocks.layout;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * 5-field L1 BlockLayoutCache key mandated by Section 4.1.3:
 * (blockId, contentHash, availableWidthPx, fontScale, styleId).
 */
public final class BlockCacheKey {

    @NonNull
    private final String blockId;
    private final int contentHash;
    private final int availableWidthPx;
    private final float fontScale;
    private final int fontScaleBits;
    @NonNull
    private final StyleId styleId;

    public BlockCacheKey(
            @NonNull String blockId,
            int contentHash,
            int availableWidthPx,
            float fontScale,
            @NonNull StyleId styleId) {
        this.blockId = blockId;
        this.contentHash = contentHash;
        this.availableWidthPx = availableWidthPx;
        float normalizedScale = (fontScale == 0.0f) ? 0.0f : fontScale;
        this.fontScale = normalizedScale;
        this.fontScaleBits = Float.floatToIntBits(normalizedScale);
        this.styleId = styleId;
    }

    @NonNull
    public String getBlockId() {
        return blockId;
    }

    public int getContentHash() {
        return contentHash;
    }

    public int getAvailableWidthPx() {
        return availableWidthPx;
    }

    public float getFontScale() {
        return fontScale;
    }

    @NonNull
    public StyleId getStyleId() {
        return styleId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlockCacheKey)) return false;
        BlockCacheKey that = (BlockCacheKey) o;
        return contentHash == that.contentHash
                && availableWidthPx == that.availableWidthPx
                && fontScaleBits == that.fontScaleBits
                && blockId.equals(that.blockId)
                && styleId == that.styleId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(blockId, contentHash, availableWidthPx, fontScaleBits, styleId);
    }

    @NonNull
    @Override
    public String toString() {
        return "BlockCacheKey{" +
                "blockId='" + blockId + '\'' +
                ", contentHash=" + contentHash +
                ", widthPx=" + availableWidthPx +
                ", fontScale=" + fontScale +
                ", styleId=" + styleId +
                '}';
    }
}
