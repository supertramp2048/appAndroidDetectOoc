package com.socialapp.core.ui.blocks.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable block representing an HTML <table> (mounted only when HTML actually contains a table).
 */
public final class TableBlock implements ContentBlock {

    public static final class TableCell {
        @NonNull
        public final String text;
        public final boolean header;

        public TableCell(@NonNull String text, boolean header) {
            this.text = text;
            this.header = header;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TableCell)) return false;
            TableCell tableCell = (TableCell) o;
            return header == tableCell.header && text.equals(tableCell.text);
        }

        @Override
        public int hashCode() {
            return Objects.hash(text, header);
        }
    }

    public static final class TableRow {
        @NonNull
        public final List<TableCell> cells;

        public TableRow(@NonNull List<TableCell> cells) {
            this.cells = Collections.unmodifiableList(new ArrayList<>(cells));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TableRow)) return false;
            TableRow tableRow = (TableRow) o;
            return cells.equals(tableRow.cells);
        }

        @Override
        public int hashCode() {
            return cells.hashCode();
        }
    }

    @NonNull
    private final String blockId;
    @NonNull
    private final List<TableRow> rows;
    private final int contentHash;

    public TableBlock(@NonNull String blockId, @NonNull List<TableRow> rows) {
        this.blockId = blockId;
        this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
        this.contentHash = Objects.hash(this.blockId, this.rows);
    }

    @NonNull
    @Override
    public String getBlockId() {
        return blockId;
    }

    @Override
    public int getBlockType() {
        return TYPE_TABLE;
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
    public List<TableRow> getRows() {
        return rows;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TableBlock)) return false;
        TableBlock that = (TableBlock) o;
        return contentHash == that.contentHash
                && blockId.equals(that.blockId)
                && rows.equals(that.rows);
    }

    @Override
    public int hashCode() {
        return contentHash;
    }
}
