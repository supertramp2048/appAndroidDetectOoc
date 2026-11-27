package com.socialapp.core.ui.blocks.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable pure-Java rich text payload pairing plain text with InlineSpanSpecs.
 * Enables JVM unit tests of HtmlBlockAstParser without Robolectric's Spannable stubs.
 */
public final class StyledTextSpec {

    public static final StyledTextSpec EMPTY = new StyledTextSpec("", Collections.emptyList());

    @NonNull
    private final String text;
    @NonNull
    private final List<InlineSpanSpec> spans;
    private final int contentHash;

    public StyledTextSpec(@NonNull String text, @NonNull List<InlineSpanSpec> spans) {
        this.text = text;
        this.spans = spans.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(spans));
        this.contentHash = Objects.hash(this.text, this.spans);
    }

    @NonNull
    public String getText() {
        return text;
    }

    @NonNull
    public List<InlineSpanSpec> getSpans() {
        return spans;
    }

    public boolean isEmpty() {
        return text.trim().isEmpty();
    }

    public int getContentHash() {
        return contentHash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StyledTextSpec)) return false;
        StyledTextSpec that = (StyledTextSpec) o;
        return contentHash == that.contentHash
                && text.equals(that.text)
                && spans.equals(that.spans);
    }

    @Override
    public int hashCode() {
        return contentHash;
    }
}
