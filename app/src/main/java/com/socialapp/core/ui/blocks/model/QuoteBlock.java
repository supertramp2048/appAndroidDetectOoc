package com.socialapp.core.ui.blocks.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * Immutable block representing a <blockquote> element parsed from HTML.
 */
public final class QuoteBlock implements ContentBlock {

    @NonNull
    private final String blockId;
    @NonNull
    private final StyledTextSpec styledText;
    private final int contentHash;

    public QuoteBlock(@NonNull String blockId, @NonNull StyledTextSpec styledText) {
        this.blockId = blockId;
        this.styledText = styledText;
        this.contentHash = Objects.hash(this.blockId, this.styledText);
    }

    @NonNull
    @Override
    public String getBlockId() {
        return blockId;
    }

    @Override
    public int getBlockType() {
        return TYPE_QUOTE;
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
    public StyledTextSpec getStyledText() {
        return styledText;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QuoteBlock)) return false;
        QuoteBlock that = (QuoteBlock) o;
        return contentHash == that.contentHash
                && blockId.equals(that.blockId)
                && styledText.equals(that.styledText);
    }

    @Override
    public int hashCode() {
        return contentHash;
    }
}
