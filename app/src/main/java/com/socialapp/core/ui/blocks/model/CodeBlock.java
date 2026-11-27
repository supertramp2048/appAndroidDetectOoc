package com.socialapp.core.ui.blocks.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * Immutable block representing a <pre> or fenced code snippet parsed from HTML.
 */
public final class CodeBlock implements ContentBlock {

    @NonNull
    private final String blockId;
    @NonNull
    private final String codeText;
    @Nullable
    private final String language;
    private final int contentHash;

    public CodeBlock(@NonNull String blockId, @NonNull String codeText, @Nullable String language) {
        this.blockId = blockId;
        this.codeText = codeText;
        this.language = language;
        this.contentHash = Objects.hash(this.blockId, this.codeText, this.language);
    }

    @NonNull
    @Override
    public String getBlockId() {
        return blockId;
    }

    @Override
    public int getBlockType() {
        return TYPE_CODE;
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
    public String getCodeText() {
        return codeText;
    }

    @Nullable
    public String getLanguage() {
        return language;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CodeBlock)) return false;
        CodeBlock codeBlock = (CodeBlock) o;
        return contentHash == codeBlock.contentHash
                && blockId.equals(codeBlock.blockId)
                && codeText.equals(codeBlock.codeText)
                && Objects.equals(language, codeBlock.language);
    }

    @Override
    public int hashCode() {
        return contentHash;
    }
}
