package com.socialapp.core.prefetch;

/**
 * System-wide configuration constants for the Prefetching & Caching Engine.
 * Supports tuning for Pareto optimization (Hit Rate vs Wasted Bandwidth).
 */
public final class PrefetchConfig {

    /**
     * Maximum number of target screens allowed to prefetch concurrently.
     * Prevents excessive bandwidth division (BW / N) and wasted byte spikes.
     */
    public static final int MAX_PREFETCH_TARGETS = 2;

    /**
     * Default Time-To-Live (TTL) in milliseconds for prefetched cache entries in RAM.
     * 60 seconds is chosen to align with average user dwell / thinking time on screens.
     */
    public static final long DEFAULT_TTL_MS = 60_000L;

    /**
     * Maximum number of entries retained in RAM heap cache before LRU eviction.
     */
    public static final int MAX_CACHE_ENTRIES = 20;

    /**
     * Periodic interval in milliseconds for background eviction of expired cache entries.
     */
    public static final long CLEANUP_INTERVAL_MS = 10_000L;

    /**
     * Default probability threshold (theta) for Markov prediction policies.
     */
    public static final double DEFAULT_MARKOV_THRESHOLD = 0.50;

    private PrefetchConfig() {}
}
