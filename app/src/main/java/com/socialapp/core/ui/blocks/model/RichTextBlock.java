package com.socialapp.core.ui.blocks.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.socialapp.core.ui.blocks.layout.StyleId;

import java.util.Objects;

/**
 * Immutable block representing h1..h4, lead paragraph, body paragraph, or list item parsed from HTML.
 */
public final class RichTextBlock implements ContentBlock {

    public enum TextStyle {
        HEADING_H1(StyleId.DETAIL_H1),
        HEADING_H2(StyleId.DETAIL_H2),
        HEADING_H3(StyleId.DETAIL_H3),
        HEADING_H4(StyleId.DETAIL_H4),
        LEAD(StyleId.DETAIL_LEAD),
        PARAGRAPH(StyleId.DETAIL_BODY),
        LIST_ITEM(StyleId.DETAIL_LIST_ITEM);

        private final StyleId styleId;

        TextStyle(StyleId styleId) {
            this.styleId = styleId;
        }

        @NonNull
        public StyleId toStyleId() {
            return styleId;
        }

        public boolean isHeading() {
            return this == HEADING_H1 || this == HEADING_H2 || this == HEADING_H3 || this == HEADING_H4;
        }
    }

    @NonNull
    private final String blockId;
    @NonNull
    private final TextStyle textStyle;
    @NonNull
    private final StyledTextSpec styledText;
    private final int listLevel;
    private final int contentHash;

    public RichTextBlock(
            @NonNull String blockId,
            @NonNull TextStyle textStyle,
            @NonNull StyledTextSpec styledText,
            int listLevel) {
        this.blockId = blockId;
        this.textStyle = textStyle;
        this.styledText = styledText;
        this.listLevel = Math.max(0, listLevel);
        this.contentHash = Objects.hash(this.blockId, this.textStyle, this.styledText, this.listLevel);
    }

    @NonNull
    public RichTextBlock withTextStyle(@NonNull TextStyle newStyle) {
        if (newStyle == this.textStyle) return this;
        return new RichTextBlock(blockId, newStyle, styledText, listLevel);
    }

    @NonNull
    @Override
    public String getBlockId() {
        return blockId;
    }

    @Override
    public int getBlockType() {
        return TYPE_RICH_TEXT;
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
    public TextStyle getTextStyle() {
        return textStyle;
    }

    @NonNull
    public StyledTextSpec getStyledText() {
        return styledText;
    }

    public int getListLevel() {
        return listLevel;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RichTextBlock)) return false;
        RichTextBlock that = (RichTextBlock) o;
        return contentHash == that.contentHash
                && listLevel == that.listLevel
                && blockId.equals(that.blockId)
                && textStyle == that.textStyle
                && styledText.equals(that.styledText);
    }

    @Override
    public int hashCode() {
        return contentHash;
    }
}
