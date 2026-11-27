package com.socialapp.core.ui.blocks.layout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe L1 LRU cache storing precomputed MeasuredBlockSpec objects keyed by 5-field BlockCacheKey.
 * Pure Java LinkedHashMap implementation avoids Android framework dependency so tests run on JVM.
 */
public class BlockLayoutCache {

    public static final int DEFAULT_MAX_ENTRIES = 256;

    private final int maxEntries;
    private final Object lock = new Object();
    private final LinkedHashMap<BlockCacheKey, MeasuredBlockSpec> lruMap;
    private final AtomicInteger hitCount = new AtomicInteger(0);
    private final AtomicInteger missCount = new AtomicInteger(0);

    public BlockLayoutCache() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public BlockLayoutCache(int maxEntries) {
        this.maxEntries = Math.max(1, maxEntries);
        this.lruMap = new LinkedHashMap<BlockCacheKey, MeasuredBlockSpec>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<BlockCacheKey, MeasuredBlockSpec> eldest) {
                return size() > BlockLayoutCache.this.maxEntries;
            }
        };
    }

    @Nullable
    public MeasuredBlockSpec get(@NonNull BlockCacheKey key) {
        synchronized (lock) {
            MeasuredBlockSpec value = lruMap.get(key);
            if (value != null) {
                hitCount.incrementAndGet();
            } else {
                missCount.incrementAndGet();
            }
            return value;
        }
    }

    public void put(@NonNull BlockCacheKey key, @NonNull MeasuredBlockSpec spec) {
        synchronized (lock) {
            lruMap.put(key, spec);
        }
    }

    public boolean contains(@NonNull BlockCacheKey key) {
        synchronized (lock) {
            return lruMap.containsKey(key);
        }
    }

    /**
     * Trims oldest LRU entries down to keepFraction of current size (used on TRIM_MEMORY_RUNNING_MODERATE/LOW).
     */
    public int trimToFraction(float keepFraction) {
        float clamped = Math.max(0.0f, Math.min(1.0f, keepFraction));
        synchronized (lock) {
            int targetSize = Math.round(lruMap.size() * clamped);
            return trimToSizeLocked(targetSize);
        }
    }

    public int trimToSize(int targetMaxEntries) {
        synchronized (lock) {
            return trimToSizeLocked(Math.max(0, targetMaxEntries));
        }
    }

    private int trimToSizeLocked(int targetSize) {
        int removed = 0;
        Iterator<Map.Entry<BlockCacheKey, MeasuredBlockSpec>> it = lruMap.entrySet().iterator();
        while (lruMap.size() > targetSize && it.hasNext()) {
            it.next();
            it.remove();
            removed++;
        }
        return removed;
    }

    public void clear() {
        synchronized (lock) {
            lruMap.clear();
        }
    }

    public int size() {
        synchronized (lock) {
            return lruMap.size();
        }
    }

    public int getMaxEntries() {
        return maxEntries;
    }

    public int getHitCount() {
        return hitCount.get();
    }

    public int getMissCount() {
        return missCount.get();
    }

    public void resetStats() {
        hitCount.set(0);
        missCount.set(0);
    }
}
