package com.socialapp.core.ui.blocks.layout;

import android.text.Spanned;
import android.text.StaticLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Immutable precomputed layout spec for PostDetail RichTextBlock and QuoteBlock views.
 */
public final class MeasuredTextBlockSpec extends MeasuredBlockSpec {

    @Nullable
    public final StaticLayout textLayout;
    @Nullable
    public final Spanned spannedText;
    @NonNull
    public final BlockRect textRect;
    @NonNull
    public final BlockRect backgroundRect;
    @NonNull
    public final BlockRect accentBarRect;
    public final int cornerRadiusPx;
    public final boolean isQuote;

    public MeasuredTextBlockSpec(
            @NonNull String blockId,
            int contentHash,
            int targetWidthPx,
            float fontScale,
            @NonNull StyleId styleId,
            int totalHeightPx,
            @Nullable StaticLayout textLayout,
            @Nullable Spanned spannedText,
            @NonNull BlockRect textRect,
            @NonNull BlockRect backgroundRect,
            @NonNull BlockRect accentBarRect,
            int cornerRadiusPx,
            boolean isQuote) {
        super(blockId, contentHash, targetWidthPx, fontScale, styleId, totalHeightPx);
        this.textLayout = textLayout;
        this.spannedText = spannedText;
        this.textRect = textRect;
        this.backgroundRect = backgroundRect;
        this.accentBarRect = accentBarRect;
        this.cornerRadiusPx = cornerRadiusPx;
        this.isQuote = isQuote;
    }
}
