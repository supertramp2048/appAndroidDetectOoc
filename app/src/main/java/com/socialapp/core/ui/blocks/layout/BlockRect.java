package com.socialapp.core.ui.blocks.layout;

import android.graphics.Rect;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Immutable pure-Java integer rectangle used in MeasuredBlockSpec objects.
 * Avoids mutable android.graphics.Rect leaks and enables JVM unit testing without Robolectric.
 */
public final class BlockRect {

    public static final BlockRect EMPTY = new BlockRect(0, 0, 0, 0);

    public final int left;
    public final int top;
    public final int right;
    public final int bottom;

    public BlockRect(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = Math.max(left, right);
        this.bottom = Math.max(top, bottom);
    }

    public int width() {
        return right - left;
    }

    public int height() {
        return bottom - top;
    }

    public int centerX() {
        return (left + right) >>> 1;
    }

    public int centerY() {
        return (top + bottom) >>> 1;
    }

    public boolean isEmpty() {
        return left >= right || top >= bottom;
    }

    public boolean contains(int x, int y) {
        return !isEmpty() && x >= left && x < right && y >= top && y < bottom;
    }

    /**
     * Expands this rectangle around its center so width and height reach at least minSizePx
     * (clamped to container bounds [0, maxWidth] x [0, maxHeight]), shifting inward if an edge hits 0 or max.
     */
    @NonNull
    public BlockRect expandToMinTouchSize(int minSizePx, int maxWidth, int maxHeight) {
        if (isEmpty() || minSizePx <= 0 || maxWidth <= 0 || maxHeight <= 0) {
            return this;
        }
        int targetW = Math.min(maxWidth, Math.max(width(), minSizePx));
        int targetH = Math.min(maxHeight, Math.max(height(), minSizePx));

        int newLeft = centerX() - (targetW / 2);
        int newRight = newLeft + targetW;
        if (newLeft < 0) {
            newLeft = 0;
            newRight = targetW;
        } else if (newRight > maxWidth) {
            newRight = maxWidth;
            newLeft = Math.max(0, maxWidth - targetW);
        }

        int newTop = centerY() - (targetH / 2);
        int newBottom = newTop + targetH;
        if (newTop < 0) {
            newTop = 0;
            newBottom = targetH;
        } else if (newBottom > maxHeight) {
            newBottom = maxHeight;
            newTop = Math.max(0, maxHeight - targetH);
        }

        return new BlockRect(newLeft, newTop, newRight, newBottom);
    }

    public void toAndroidRect(@NonNull Rect outRect) {
        outRect.set(left, top, right, bottom);
    }

    @NonNull
    public Rect toNewAndroidRect() {
        return new Rect(left, top, right, bottom);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlockRect)) return false;
        BlockRect blockRect = (BlockRect) o;
        return left == blockRect.left
                && top == blockRect.top
                && right == blockRect.right
                && bottom == blockRect.bottom;
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, top, right, bottom);
    }

    @NonNull
    @Override
    public String toString() {
        return "BlockRect(" + left + ", " + top + " - " + right + ", " + bottom + ")";
    }
}
