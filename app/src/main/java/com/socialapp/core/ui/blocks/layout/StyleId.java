package com.socialapp.core.ui.blocks.layout;

/**
 * Enumerates every immutable typography style used across Feed, Comments, and PostDetail blocks.
 * Separating vote polarity and action styles avoids mutating shared TextPaint instances at draw time.
 */
public enum StyleId {
    // Feed Card styles (matching view_post_card.xml)
    FEED_CATEGORY(11.0f, 0xFF1E5AA8, FontFamily.SANS_SERIF, true, false, 0.04f),
    FEED_TITLE(17.5f, 0xFF17212B, FontFamily.SERIF, true, false, 0.0f),
    FEED_BYLINE(12.0f, 0xFF161515, FontFamily.SANS_SERIF, true, false, 0.0f),
    FEED_TIME(12.0f, 0xFF757575, FontFamily.SANS_SERIF, false, false, 0.0f),
    FEED_SOURCE(11.0f, 0xFF888888, FontFamily.SANS_SERIF, false, false, 0.0f),
    FEED_VOTE_POSITIVE(12.0f, 0xFF1E5AA8, FontFamily.SANS_SERIF, true, false, 0.0f),
    FEED_VOTE_NEGATIVE(12.0f, 0xFFD83A34, FontFamily.SANS_SERIF, true, false, 0.0f),
    FEED_VOTE_NEUTRAL(12.0f, 0xFF161515, FontFamily.SANS_SERIF, true, false, 0.0f),
    FEED_COMMENTS(12.0f, 0xFF161515, FontFamily.SANS_SERIF, false, false, 0.0f),

    // Comment styles (matching item_comment.xml)
    COMMENT_AUTHOR(13.5f, 0xFF17212B, FontFamily.SANS_SERIF_MEDIUM, true, false, 0.0f),
    COMMENT_TIME(11.5f, 0xFF161515, FontFamily.SANS_SERIF, false, false, 0.0f),
    COMMENT_EDITED(11.0f, 0xFF161515, FontFamily.SANS_SERIF, false, false, 0.0f),
    COMMENT_BODY(14.0f, 0xFF17212B, FontFamily.SANS_SERIF, false, false, 0.0f),
    COMMENT_REMOVED(12.5f, 0xFFD83A34, FontFamily.SANS_SERIF, false, true, 0.0f),
    COMMENT_ACTION(12.0f, 0xFF1E5AA8, FontFamily.SANS_SERIF_MEDIUM, true, false, 0.0f),

    // Post Detail styles (matching activity_post_detail.xml & buildEditorialHtml())
    DETAIL_CATEGORY(11.5f, 0xFF1E5AA8, FontFamily.SANS_SERIF_MEDIUM, true, false, 0.04f),
    DETAIL_TITLE(25.0f, 0xFF17212B, FontFamily.SERIF, true, false, 0.0f),
    DETAIL_H1(23.0f, 0xFF17212B, FontFamily.SERIF, true, false, 0.0f),
    DETAIL_H2(21.0f, 0xFF17212B, FontFamily.SERIF, true, false, 0.0f),
    DETAIL_H3(19.0f, 0xFF17212B, FontFamily.SERIF, true, false, 0.0f),
    DETAIL_H4(18.0f, 0xFF17212B, FontFamily.SERIF, true, false, 0.0f),
    DETAIL_LEAD(19.0f, 0xFF17212B, FontFamily.SERIF, false, false, 0.01f),
    DETAIL_BODY(18.0f, 0xFF17212B, FontFamily.SERIF, false, false, 0.01f),
    DETAIL_LIST_ITEM(18.0f, 0xFF17212B, FontFamily.SERIF, false, false, 0.01f),
    DETAIL_QUOTE(18.0f, 0xFF334155, FontFamily.SERIF, false, true, 0.0f),
    DETAIL_CODE(14.0f, 0xFF17212B, FontFamily.MONOSPACE, false, false, 0.0f),
    DETAIL_CAPTION(13.0f, 0xFF64748B, FontFamily.SANS_SERIF, false, true, 0.0f),
    DETAIL_TABLE_HEADER(14.0f, 0xFF17212B, FontFamily.SANS_SERIF, true, false, 0.0f),
    DETAIL_TABLE_CELL(14.0f, 0xFF17212B, FontFamily.SANS_SERIF, false, false, 0.0f);

    public enum FontFamily {
        SANS_SERIF,
        SANS_SERIF_MEDIUM,
        SERIF,
        MONOSPACE
    }

    private final float textSizeSp;
    private final int color;
    private final FontFamily fontFamily;
    private final boolean bold;
    private final boolean italic;
    private final float letterSpacing;

    StyleId(float textSizeSp, int color, FontFamily fontFamily, boolean bold, boolean italic, float letterSpacing) {
        this.textSizeSp = textSizeSp;
        this.color = color;
        this.fontFamily = fontFamily;
        this.bold = bold;
        this.italic = italic;
        this.letterSpacing = letterSpacing;
    }

    public float getTextSizeSp() {
        return textSizeSp;
    }

    public int getColor() {
        return color;
    }

    public FontFamily getFontFamily() {
        return fontFamily;
    }

    public boolean isBold() {
        return bold;
    }

    public boolean isItalic() {
        return italic;
    }

    public float getLetterSpacing() {
        return letterSpacing;
    }

    public float computeTextSizePx(float density, float fontScale) {
        return textSizeSp * density * fontScale;
    }
}
