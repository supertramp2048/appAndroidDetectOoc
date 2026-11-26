package com.socialapp.core.ui.blocks.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * Immutable block representing an inline <img> + optional <figcaption>/<em> caption.
 * Carries explicit or fallback 16:9 aspect ratio to eliminate Cumulative Layout Shift (CLS).
 */
public final class InlineImageBlock implements ContentBlock {

    public static final float DEFAULT_ASPECT_RATIO = 16.0f / 9.0f;

    @NonNull
    private final String blockId;
    @NonNull
    private final String imageUrl;
    @Nullable
    private final String altText;
    @Nullable
    private final String caption;
    private final int widthAttr;
    private final int heightAttr;
    private final int contentHash;

    public InlineImageBlock(
            @NonNull String blockId,
            @NonNull String imageUrl,
            @Nullable String altText,
            @Nullable String caption,
            int widthAttr,
            int heightAttr) {
        this.blockId = blockId;
        this.imageUrl = imageUrl;
        this.altText = altText != null && !altText.trim().isEmpty() ? altText.trim() : null;
        this.caption = caption != null && !caption.trim().isEmpty() ? caption.trim() : null;
        this.widthAttr = Math.max(0, widthAttr);
        this.heightAttr = Math.max(0, heightAttr);
        this.contentHash = Objects.hash(this.blockId, this.imageUrl, this.altText, this.caption, this.widthAttr, this.heightAttr);
    }

    @NonNull
    public InlineImageBlock withCaption(@Nullable String newCaption) {
        return new InlineImageBlock(blockId, imageUrl, altText, newCaption, widthAttr, heightAttr);
    }

    public float getAspectRatio() {
        if (widthAttr > 0 && heightAttr > 0) {
            float ratio = widthAttr / (float) heightAttr;
            if (ratio >= 0.35f && ratio <= 3.5f) {
                return ratio;
            }
        }
        return DEFAULT_ASPECT_RATIO;
    }

    @NonNull
    @Override
    public String getBlockId() {
        return blockId;
    }

    @Override
    public int getBlockType() {
        return TYPE_INLINE_IMAGE;
    }

    @Override
    public int getContentHash() {
        return contentHash;
    }

    @Nullable
    @Override
    public Object getChangePayload(@NonNull ContentBlock newItem) {
        return null;
    }

    @NonNull
    public String getImageUrl() {
        return imageUrl;
    }

    @Nullable
    public String getAltText() {
        return altText;
    }

    @Nullable
    public String getCaption() {
        return caption;
    }

    public int getWidthAttr() {
        return widthAttr;
    }

    public int getHeightAttr() {
        return heightAttr;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InlineImageBlock)) return false;
        InlineImageBlock that = (InlineImageBlock) o;
        return contentHash == that.contentHash
                && widthAttr == that.widthAttr
                && heightAttr == that.heightAttr
                && blockId.equals(that.blockId)
                && imageUrl.equals(that.imageUrl)
                && Objects.equals(altText, that.altText)
                && Objects.equals(caption, that.caption);
    }

    @Override
    public int hashCode() {
        return contentHash;
    }
}
