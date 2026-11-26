package com.socialapp.core.ui.blocks.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * Pure-Java specification of an inline HTML span (bold, italic, underline, code, link).
 */
public final class InlineSpanSpec {

    public enum SpanType {
        BOLD,
        ITALIC,
        UNDERLINE,
        INLINE_CODE,
        LINK
    }

    @NonNull
    public final SpanType type;
    public final int start;
    public final int end;
    @Nullable
    public final String url;

    public InlineSpanSpec(@NonNull SpanType type, int start, int end, @Nullable String url) {
        this.type = type;
        this.start = Math.max(0, start);
        this.end = Math.max(this.start, end);
        this.url = url;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InlineSpanSpec)) return false;
        InlineSpanSpec that = (InlineSpanSpec) o;
        return start == that.start
                && end == that.end
                && type == that.type
                && Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, start, end, url);
    }
}
