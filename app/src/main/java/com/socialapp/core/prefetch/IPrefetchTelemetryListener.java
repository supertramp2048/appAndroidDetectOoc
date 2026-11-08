package com.socialapp.core.prefetch;

import androidx.annotation.NonNull;

/**
 * Real-time event listener for prefetch engine metrics, cache evictions, and hit rates.
 * Consumed by developer consoles, HUDs, and experiment loggers.
 */
public interface IPrefetchTelemetryListener {

    /**
     * Fired when a prefetch request has been triggered and queued.
     */
    void onPrefetchTriggered(@NonNull String targetScreen, @NonNull String cacheKey, long estimatedPayloadBytes);

    /**
     * Fired when a background prefetch completes and is stored into the cache.
     */
    void onPrefetchCompleted(@NonNull String targetScreen, @NonNull String cacheKey, long durationMs, long payloadBytes);

    /**
     * Fired when a background prefetch fails with an exception.
     */
    void onPrefetchFailed(@NonNull String targetScreen, @NonNull String cacheKey, @NonNull Throwable error);

    /**
     * Fired when a consumer (Repository / Screen) acquires data, reporting Full Hit, Late Hit, or Miss.
     */
    void onAcquireResult(@NonNull PrefetchResult<?> result);

    /**
     * Fired when an entry is evicted from cache (either expired or displaced by LRU).
     */
    void onCacheEvicted(@NonNull String cacheKey, long wastedBytes, boolean wasExpired, boolean wasUsed);
}
