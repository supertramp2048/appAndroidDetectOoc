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
     * Default Soft Time-To-Live (Soft TTL) in milliseconds for Stale-While-Revalidate (SWR).
     * Within [0, DEFAULT_SOFT_TTL_MS]: entry is FRESH (served in 0ms without network call).
     * Within [DEFAULT_SOFT_TTL_MS, DEFAULT_TTL_MS]: entry is STALE (served in 0ms + background revalidate).
     */
    public static final long DEFAULT_SOFT_TTL_MS = 20_000L;

    /**
     * Default Hard Time-To-Live (Hard TTL) in milliseconds for prefetched cache entries in RAM.
     * 60 seconds is chosen to align with average user dwell / thinking time on screens.
     */
    public static final long DEFAULT_TTL_MS = 60_000L;

    /**
     * [Ý 1] Minimum time (ms) a user must pause/dwell on a post card in the viewport
     * after scrolling stops before speculative API + Glide image preloading triggers.
     */
    public static final long VIEWPORT_DWELL_THRESHOLD_MS = 450L;

    /**
     * [Ý 1] Default session byte budget for background prefetching over metered 4G/cellular networks (2 MB).
     */
    public static final long DEFAULT_CELLULAR_PREFETCH_BUDGET_BYTES = 2L * 1024 * 1024;

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
