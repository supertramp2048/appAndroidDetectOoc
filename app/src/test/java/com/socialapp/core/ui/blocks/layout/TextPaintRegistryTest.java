package com.socialapp.core.ui.blocks.layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import android.text.TextPaint;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class TextPaintRegistryTest {

    @Test
    public void testSnapshotMetricsAndImmutableStyleIsolation() {
        TextPaintRegistry registry = new TextPaintRegistry();

        TextPaintRegistry.PaintSnapshot normalSnap = registry.getSnapshot(2.0f, 1.0f);
        TextPaintRegistry.PaintSnapshot scaledSnap = registry.getSnapshot(2.0f, 1.3f);

        assertSame(normalSnap, registry.getSnapshot(2.0f, 1.0f));
        assertNotSame(normalSnap, scaledSnap);

        assertEquals(17.5f * 2.0f * 1.0f, normalSnap.getTextSizePx(StyleId.FEED_TITLE), 0.01f);
        assertEquals(17.5f * 2.0f * 1.3f, scaledSnap.getTextSizePx(StyleId.FEED_TITLE), 0.01f);
        assertEquals(25.0f * 2.0f * 1.0f, normalSnap.getTextSizePx(StyleId.DETAIL_TITLE), 0.01f);
        assertEquals(14.0f * 2.0f * 1.0f, normalSnap.getTextSizePx(StyleId.COMMENT_BODY), 0.01f);

        // Distinct vote polarity styles return separate TextPaint objects
        TextPaint posPaint = normalSnap.get(StyleId.FEED_VOTE_POSITIVE);
        TextPaint negPaint = normalSnap.get(StyleId.FEED_VOTE_NEGATIVE);
        TextPaint neuPaint = normalSnap.get(StyleId.FEED_VOTE_NEUTRAL);
        assertNotNull(posPaint);
        assertNotNull(negPaint);
        assertNotNull(neuPaint);
        assertNotSame(posPaint, negPaint);
        assertNotSame(posPaint, neuPaint);
        assertNotSame(negPaint, neuPaint);
    }

    @Test
    public void testConcurrentMultiThreadSnapshotAccessNoRaceCondition() throws Exception {
        TextPaintRegistry registry = new TextPaintRegistry();
        int threadCount = 8;
        int iterationsPerThread = 250;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        float[] densities = {1.5f, 2.0f, 3.0f};
        float[] fontScales = {0.85f, 1.0f, 1.15f, 1.3f};

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int t = 0; t < threadCount; t++) {
            final int threadIdx = t;
            tasks.add(() -> {
                for (int i = 0; i < iterationsPerThread; i++) {
                    float d = densities[(threadIdx + i) % densities.length];
                    float s = fontScales[(threadIdx * 3 + i) % fontScales.length];
                    TextPaintRegistry.PaintSnapshot snap = registry.getSnapshot(d, s);
                    assertEquals(d, snap.getDensity(), 0.0001f);
                    assertEquals(s, snap.getFontScale(), 0.0001f);
                    for (StyleId styleId : StyleId.values()) {
                        TextPaint paint = snap.get(styleId);
                        assertNotNull(paint);
                        assertEquals(styleId.computeTextSizePx(d, s), snap.getTextSizePx(styleId), 0.01f);
                    }
                }
                return null;
            });
        }

        try {
            List<Future<Void>> futures = executor.invokeAll(tasks);
            for (Future<Void> f : futures) {
                f.get();
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
