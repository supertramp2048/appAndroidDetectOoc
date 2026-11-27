package com.socialapp.core.ui.blocks.layout;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe registry of immutable read-only TextPaint snapshots keyed by (density, fontScale).
 * Worker threads and Main Thread never mutate any published TextPaint instance, eliminating
 * font/size race conditions across concurrent text shaping jobs.
 */
public class TextPaintRegistry {

    public static final class MetricsKey {
        public final int densityBits;
        public final int fontScaleBits;
        public final float density;
        public final float fontScale;

        public MetricsKey(float density, float fontScale) {
            float normalizedDensity = sanitizePositive(density, 1.0f);
            float normalizedFontScale = sanitizePositive(fontScale, 1.0f);
            this.density = normalizedDensity;
            this.fontScale = normalizedFontScale;
            this.densityBits = Float.floatToIntBits(normalizedDensity);
            this.fontScaleBits = Float.floatToIntBits(normalizedFontScale);
        }

        private static float sanitizePositive(float value, float fallback) {
            if (Float.isNaN(value) || Float.isInfinite(value) || value <= 0.0f) {
                return fallback;
            }
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MetricsKey)) return false;
            MetricsKey that = (MetricsKey) o;
            return densityBits == that.densityBits && fontScaleBits == that.fontScaleBits;
        }

        @Override
        public int hashCode() {
            return Objects.hash(densityBits, fontScaleBits);
        }
    }

    /**
     * Immutable set of pre-configured TextPaint instances for a single (density, fontScale) pair.
     */
    public static final class PaintSnapshot {
        private final MetricsKey metricsKey;
        private final Map<StyleId, TextPaint> paints;
        private final Map<StyleId, Float> textSizesPx;

        PaintSnapshot(@NonNull MetricsKey metricsKey) {
            this.metricsKey = metricsKey;
            EnumMap<StyleId, TextPaint> paintMap = new EnumMap<>(StyleId.class);
            EnumMap<StyleId, Float> sizeMap = new EnumMap<>(StyleId.class);
            for (StyleId styleId : StyleId.values()) {
                float sizePx = styleId.computeTextSizePx(metricsKey.density, metricsKey.fontScale);
                sizeMap.put(styleId, sizePx);
                paintMap.put(styleId, buildReadOnlyPaint(styleId, sizePx));
            }
            this.paints = Collections.unmodifiableMap(paintMap);
            this.textSizesPx = Collections.unmodifiableMap(sizeMap);
        }

        @NonNull
        public MetricsKey getMetricsKey() {
            return metricsKey;
        }

        public float getDensity() {
            return metricsKey.density;
        }

        public float getFontScale() {
            return metricsKey.fontScale;
        }

        @NonNull
        public TextPaint get(@NonNull StyleId styleId) {
            TextPaint paint = paints.get(styleId);
            if (paint == null) {
                throw new IllegalArgumentException("Missing TextPaint for style: " + styleId);
            }
            return paint;
        }

        public float getTextSizePx(@NonNull StyleId styleId) {
            Float size = textSizesPx.get(styleId);
            return size != null ? size : styleId.computeTextSizePx(metricsKey.density, metricsKey.fontScale);
        }

        private static TextPaint buildReadOnlyPaint(@NonNull StyleId styleId, float textSizePx) {
            TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
            paint.setTextSize(textSizePx);
            paint.setColor(styleId.getColor());
            paint.setLetterSpacing(styleId.getLetterSpacing());
            paint.setTypeface(resolveTypeface(styleId));
            return paint;
        }

        private static Typeface resolveTypeface(@NonNull StyleId styleId) {
            int style = Typeface.NORMAL;
            if (styleId.isBold() && styleId.isItalic()) {
                style = Typeface.BOLD_ITALIC;
            } else if (styleId.isBold()) {
                style = Typeface.BOLD;
            } else if (styleId.isItalic()) {
                style = Typeface.ITALIC;
            }

            switch (styleId.getFontFamily()) {
                case SERIF:
                    return Typeface.create(Typeface.SERIF, style);
                case MONOSPACE:
                    return Typeface.create(Typeface.MONOSPACE, style);
                case SANS_SERIF_MEDIUM:
                    Typeface medium = Typeface.create("sans-serif-medium", style);
                    return medium != null ? medium : Typeface.create(Typeface.SANS_SERIF, style);
                case SANS_SERIF:
                default:
                    return Typeface.create(Typeface.SANS_SERIF, style);
            }
        }
    }

    private static final int MAX_CACHED_SNAPSHOTS = 4;

    private final ConcurrentHashMap<MetricsKey, PaintSnapshot> snapshots = new ConcurrentHashMap<>();
    private final AtomicReference<PaintSnapshot> currentSnapshotRef = new AtomicReference<>();

    @NonNull
    public PaintSnapshot getSnapshot(float density, float fontScale) {
        MetricsKey key = new MetricsKey(density, fontScale);
        PaintSnapshot cached = currentSnapshotRef.get();
        if (cached != null && cached.getMetricsKey().equals(key)) {
            return cached;
        }

        PaintSnapshot snapshot = snapshots.get(key);
        if (snapshot == null) {
            if (snapshots.size() >= MAX_CACHED_SNAPSHOTS) {
                snapshots.clear();
            }
            PaintSnapshot created = new PaintSnapshot(key);
            PaintSnapshot existing = snapshots.putIfAbsent(key, created);
            snapshot = existing != null ? existing : created;
        }
        currentSnapshotRef.set(snapshot);
        return snapshot;
    }

    @NonNull
    public TextPaint getReadOnlyPaint(@NonNull StyleId styleId, float density, float fontScale) {
        return getSnapshot(density, fontScale).get(styleId);
    }

    public int getCachedSnapshotCount() {
        return snapshots.size();
    }

    public void clear() {
        snapshots.clear();
        currentSnapshotRef.set(null);
    }
}
