package com.socialapp.core.prefetch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Task C3 In-Memory Prefetch Cache:
 * Stores prefetched application payloads in RAM heap with dynamic TTL and LRU eviction.
 * Accurately tracks wasted bytes when unconsumed entries expire.
 * Also provides compatibility methods for ScreenId (Tasks A1-A5).
 */
public class PrefetchCache {

    private final int maxEntries;
    private final Object lruLock = new Object();

    // Map storing the actual entries
    private final Map<String, PrefetchEntry<?>> storage = new ConcurrentHashMap<>();

    // LRU access tracking: key -> access ordering (access-order LinkedHashMap)
    private final LinkedHashMap<String, Long> lruMap;

    // Metrics & Telemetry
    private final AtomicLong totalWastedBytes = new AtomicLong(0);
    private final AtomicLong totalUsefulBytes = new AtomicLong(0);
    private final AtomicInteger fullHitCount = new AtomicInteger(0);
    private final AtomicInteger lateHitCount = new AtomicInteger(0);
    private final AtomicInteger missCount = new AtomicInteger(0);
    private final AtomicInteger expiredCount = new AtomicInteger(0);
    private final AtomicInteger evictedCount = new AtomicInteger(0);

    // Event listeners
    private final List<IPrefetchTelemetryListener> listeners = new CopyOnWriteArrayList<>();

    // Background daemon for TTL cleanup
    private final ScheduledExecutorService cleanupScheduler;

    public PrefetchCache() {
        this(PrefetchConfig.MAX_CACHE_ENTRIES, PrefetchConfig.CLEANUP_INTERVAL_MS);
    }

    public PrefetchCache(int maxEntries, long cleanupIntervalMs) {
        this.maxEntries = Math.max(1, maxEntries);

        // accessOrder = true for LRU behavior
        this.lruMap = new LinkedHashMap<String, Long>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                return false; // Handled manually to perform telemetry and storage synchronization
            }
        };

        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(@NonNull Runnable r) {
                Thread t = new Thread(r, "PrefetchCache-Cleaner");
                t.setDaemon(true);
                return t;
            }
        });

        if (cleanupIntervalMs > 0) {
            this.cleanupScheduler.scheduleWithFixedDelay(
                    this::cleanupExpired,
                    cleanupIntervalMs,
                    cleanupIntervalMs,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    public void addListener(@NonNull IPrefetchTelemetryListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(@NonNull IPrefetchTelemetryListener listener) {
        listeners.remove(listener);
    }

    /**
     * Puts a prefetched payload into the in-memory cache.
     * Evicts oldest entry if capacity is exceeded.
     */
    public <T> void put(
            @NonNull String key,
            @NonNull String screenName,
            @Nullable T data,
            long payloadSizeBytes,
            long ttlMs) {

        PrefetchEntry<T> newEntry = new PrefetchEntry<>(key, screenName, data, payloadSizeBytes, ttlMs);

        // Put to storage
        PrefetchEntry<?> old = storage.put(key, newEntry);
        if (old != null && !old.isUsed()) {
            recordWasted(old, false);
        }

        synchronized (lruLock) {
            lruMap.put(key, System.currentTimeMillis());

            // Enforce capacity via LRU eviction
            while (storage.size() > maxEntries && !lruMap.isEmpty()) {
                String oldestKey = lruMap.keySet().iterator().next();
                lruMap.remove(oldestKey);
                PrefetchEntry<?> evicted = storage.remove(oldestKey);
                if (evicted != null) {
                    evictedCount.incrementAndGet();
                    if (!evicted.isUsed()) {
                        recordWasted(evicted, false);
                    } else {
                        totalUsefulBytes.addAndGet(evicted.getPayloadSizeBytes());
                        notifyEviction(evicted.getKey(), 0, false, true);
                    }
                }
            }
        }
    }

    /**
     * Retrieves an entry if present and not expired.
     * Returns null if missing or expired.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public <T> PrefetchEntry<T> get(@NonNull String key) {
        PrefetchEntry<?> entry = storage.get(key);
        if (entry == null) {
            return null;
        }

        if (entry.isExpired()) {
            // Expired on read -> evict immediately and record wasted bytes
            evictEntry(key, true);
            return null;
        }

        // Update LRU access order
        synchronized (lruLock) {
            lruMap.get(key);
        }
        entry.updateAccessTime();

        return (PrefetchEntry<T>) entry;
    }

    /**
     * Checks if key exists and is unexpired without modifying LRU access order.
     */
    public boolean containsValid(@NonNull String key) {
        PrefetchEntry<?> entry = storage.get(key);
        return entry != null && !entry.isExpired();
    }

    /**
     * Explicitly removes a key from cache.
     */
    @Nullable
    public PrefetchEntry<?> remove(@NonNull String key) {
        synchronized (lruLock) {
            lruMap.remove(key);
        }
        return storage.remove(key);
    }

    /**
     * Periodic or explicit sweep to evict all expired entries and calculate wasted bytes.
     */
    public int cleanupExpired() {
        int removedCount = 0;
        long now = System.currentTimeMillis();

        for (Map.Entry<String, PrefetchEntry<?>> entry : storage.entrySet()) {
            if (entry.getValue().isExpiredAt(now)) {
                String key = entry.getKey();
                if (evictEntry(key, true)) {
                    removedCount++;
                }
            }
        }
        return removedCount;
    }

    private boolean evictEntry(@NonNull String key, boolean isExpired) {
        PrefetchEntry<?> entry = storage.remove(key);
        synchronized (lruLock) {
            lruMap.remove(key);
        }

        if (entry != null) {
            if (isExpired) {
                expiredCount.incrementAndGet();
            }
            if (!entry.isUsed()) {
                recordWasted(entry, isExpired);
            } else {
                totalUsefulBytes.addAndGet(entry.getPayloadSizeBytes());
                notifyEviction(entry.getKey(), 0, isExpired, true);
            }
            return true;
        }
        return false;
    }

    private void recordWasted(@NonNull PrefetchEntry<?> entry, boolean wasExpired) {
        long bytes = entry.getPayloadSizeBytes();
        totalWastedBytes.addAndGet(bytes);
        notifyEviction(entry.getKey(), bytes, wasExpired, false);
    }

    private void notifyEviction(@NonNull String key, long wastedBytes, boolean wasExpired, boolean wasUsed) {
        for (IPrefetchTelemetryListener l : listeners) {
            try {
                l.onCacheEvicted(key, wastedBytes, wasExpired, wasUsed);
            } catch (Throwable ignored) {}
        }
    }

    public void clear() {
        synchronized (lruLock) {
            lruMap.clear();
        }
        storage.clear();
    }

    public int size() {
        return storage.size();
    }

    @NonNull
    public Map<String, PrefetchEntry<?>> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(storage));
    }

    // --- Metrics & Counters ---

    public void recordFullHit() {
        fullHitCount.incrementAndGet();
    }

    public void recordLateHit() {
        lateHitCount.incrementAndGet();
    }

    public void recordMiss() {
        missCount.incrementAndGet();
    }

    public long getWastedBytes() {
        return totalWastedBytes.get();
    }

    public double getWastedKb() {
        return totalWastedBytes.get() / 1024.0;
    }

    public long getUsefulBytes() {
        return totalUsefulBytes.get();
    }

    public double getUsefulKb() {
        return totalUsefulBytes.get() / 1024.0;
    }

    public int getFullHitCount() {
        return fullHitCount.get();
    }

    public int getLateHitCount() {
        return lateHitCount.get();
    }

    public int getMissCount() {
        return missCount.get();
    }

    public int getExpiredCount() {
        return expiredCount.get();
    }

    public int getEvictedCount() {
        return evictedCount.get();
    }

    public double getHitRate() {
        int hits = fullHitCount.get() + lateHitCount.get();
        int total = hits + missCount.get();
        if (total == 0) return 0.0;
        return (double) hits / total;
    }

    public void resetStats() {
        totalWastedBytes.set(0);
        totalUsefulBytes.set(0);
        fullHitCount.set(0);
        lateHitCount.set(0);
        missCount.set(0);
        expiredCount.set(0);
        evictedCount.set(0);
    }

    public void shutdown() {
        cleanupScheduler.shutdownNow();
    }

    // --- ScreenId Adapter Methods (Tasks A1-A5 Compatibility) ---

    public void put(@NonNull ScreenId screenId, @Nullable Object data, long sizeBytes) {
        put(screenId.getId(), screenId.getId(), data, sizeBytes, PrefetchConfig.DEFAULT_TTL_MS);
    }

    public boolean contains(@NonNull ScreenId screenId) {
        return containsValid(screenId.getId());
    }

    @Nullable
    public PrefetchEntry<?> get(@NonNull ScreenId screenId) {
        return get(screenId.getId());
    }

    public void markUsed(@NonNull ScreenId screenId) {
        PrefetchEntry<?> entry = storage.get(screenId.getId());
        if (entry != null) {
            entry.markUsed();
        }
    }

    public long flushUnusedWastedBytes() {
        long wasted = 0;
        for (PrefetchEntry<?> entry : storage.values()) {
            if (!entry.isUsed()) {
                wasted += entry.getPayloadSizeBytes();
            }
        }
        clear();
        return wasted;
    }
}
