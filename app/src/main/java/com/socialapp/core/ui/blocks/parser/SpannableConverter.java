package com.socialapp.core.ui.blocks.parser;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;

import androidx.annotation.NonNull;

import com.socialapp.core.ui.blocks.model.InlineSpanSpec;
import com.socialapp.core.ui.blocks.model.StyledTextSpec;

import java.util.List;

/**
 * Converts pure-Java StyledTextSpec into Android CharSequence / SpannableStringBuilder
 * for StaticLayout shaping and Canvas rendering.
 */
public final class SpannableConverter {

    private static final int INLINE_CODE_BG = 0xFFEEF2F6;

    private SpannableConverter() {
    }

    @NonNull
    public static CharSequence toCharSequence(@NonNull StyledTextSpec spec) {
        String plainText = spec.getText();
        List<InlineSpanSpec> spans = spec.getSpans();
        if (spans.isEmpty() || plainText.isEmpty()) {
            return plainText;
        }

        SpannableStringBuilder builder = new SpannableStringBuilder(plainText);
        int maxLen = plainText.length();
        for (InlineSpanSpec span : spans) {
            int start = Math.max(0, Math.min(span.start, maxLen));
            int end = Math.max(start, Math.min(span.end, maxLen));
            if (end <= start) {
                continue;
            }
            Object androidSpan = createAndroidSpan(span);
            if (androidSpan != null) {
                builder.setSpan(androidSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (span.type == InlineSpanSpec.SpanType.INLINE_CODE) {
                builder.setSpan(new BackgroundColorSpan(INLINE_CODE_BG), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return builder;
    }

    private static Object createAndroidSpan(@NonNull InlineSpanSpec span) {
        switch (span.type) {
            case BOLD:
                return new StyleSpan(Typeface.BOLD);
            case ITALIC:
                return new StyleSpan(Typeface.ITALIC);
            case UNDERLINE:
                return new UnderlineSpan();
            case INLINE_CODE:
                return new TypefaceSpan("monospace");
            case LINK:
                return new UrlClickableSpan(span.url != null ? span.url : "");
            default:
                return null;
        }
    }
}
