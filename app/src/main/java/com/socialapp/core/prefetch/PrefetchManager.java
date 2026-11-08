package com.socialapp.core.prefetch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.socialapp.core.mock.FakeApi;
import com.socialapp.core.network.sim.MockNetworkEngine;
import com.socialapp.core.network.sim.NetworkProfile;
import com.socialapp.core.network.sim.PayloadWeight;
import com.socialapp.core.network.sim.ScreenPayloadConfig;
import com.socialapp.core.prefetch.policy.MostFrequentPolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Task C4 Prefetch Manager:
 * Orchestrates proactive background prefetching, request deduplication,
 * and tri-state acquisition (FULL_HIT, LATE_HIT, MISS).
 */
public class PrefetchManager {

    private static volatile PrefetchManager instance;

    @NonNull
    public static PrefetchManager getInstance() {
        if (instance == null) {
            synchronized (PrefetchManager.class) {
                if (instance == null) {
                    instance = new PrefetchManager();
                }
            }
        }
        return instance;
    }

    private final PrefetchCache cache;
    private final MockNetworkEngine networkEngine;
    private volatile IPrefetchPolicy currentPolicy;

    // In-flight background prefetch requests: cacheKey -> InFlightTask
    private final Map<String, InFlightTask<?>> inFlightRequests = new ConcurrentHashMap<>();

    // Navigation history: chronological sequence of visited screen names
    private final List<String> navigationHistory = new CopyOnWriteArrayList<>();

    // Telemetry listeners
    private final List<IPrefetchTelemetryListener> listeners = new CopyOnWriteArrayList<>();

    // Dedicated background thread pool for prefetch coordination
    private final ExecutorService prefetchExecutor;

    public PrefetchManager() {
        this(new PrefetchCache(), MockNetworkEngine.getInstance(), new MostFrequentPolicy());
    }

    public PrefetchManager(
            @NonNull PrefetchCache cache,
            @NonNull MockNetworkEngine networkEngine,
            @NonNull IPrefetchPolicy initialPolicy) {
        this.cache = cache;
        this.networkEngine = networkEngine;
        this.currentPolicy = initialPolicy;

        this.prefetchExecutor = Executors.newFixedThreadPool(4, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);
            @Override
            public Thread newThread(@NonNull Runnable r) {
                Thread t = new Thread(r, "PrefetchWorker-" + counter.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });

        // Bubble up cache events to listeners
        this.cache.addListener(new IPrefetchTelemetryListener() {
            @Override
            public void onPrefetchTriggered(@NonNull String s, @NonNull String k, long b) {}
            @Override
            public void onPrefetchCompleted(@NonNull String s, @NonNull String k, long d, long b) {}
            @Override
            public void onPrefetchFailed(@NonNull String s, @NonNull String k, @NonNull Throwable e) {}
            @Override
            public void onAcquireResult(@NonNull PrefetchResult<?> result) {}
            @Override
            public void onCacheEvicted(@NonNull String cacheKey, long wastedBytes, boolean wasExpired, boolean wasUsed) {
                for (IPrefetchTelemetryListener l : listeners) {
                    try {
                        l.onCacheEvicted(cacheKey, wastedBytes, wasExpired, wasUsed);
                    } catch (Throwable ignored) {}
                }
            }
        });
    }

    // --- Policy Management ---

    @NonNull
    public IPrefetchPolicy getPolicy() {
        return currentPolicy;
    }

    public void setPolicy(@NonNull IPrefetchPolicy policy) {
        this.currentPolicy = policy;
    }

    @NonNull
    public PrefetchCache getCache() {
        return cache;
    }

    public void addTelemetryListener(@NonNull IPrefetchTelemetryListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeTelemetryListener(@NonNull IPrefetchTelemetryListener listener) {
        listeners.remove(listener);
    }

    // --- Core Navigation Hook ---

    /**
     * Called whenever a user navigates to a screen (e.g. from Activity.onResume).
     * Automatically consults active IPrefetchPolicy and initiates prefetch for top candidates.
     */
    public void onScreenVisited(@NonNull String currentScreen) {
        if (currentScreen == null || currentScreen.isEmpty()) return;

        // Keep last 10 entries in history
        navigationHistory.add(currentScreen);
        while (navigationHistory.size() > 10) {
            navigationHistory.remove(0);
        }

        // Online Learning Hook: Record live user trajectory
        if (navigationHistory.size() >= 2) {
            String prev = navigationHistory.get(navigationHistory.size() - 2);
            PrefetchRuntimeConfig.getInstance().getPolicyFactory().getSharedMatrix().recordTransition(prev, currentScreen);
            if (navigationHistory.size() >= 3) {
                String prev2 = navigationHistory.get(navigationHistory.size() - 3);
                PrefetchRuntimeConfig.getInstance().getPolicyFactory().getSharedTensor().recordSequence(prev2, prev, currentScreen);
            }
        }

        // Feature Flag Check: Is prefetching globally enabled?
        if (!PrefetchRuntimeConfig.getInstance().isPrefetchEnabled()) {
            return;
        }

        // Consult policy for predictions
        List<String> candidateScreens = currentPolicy.predictNextScreens(currentScreen, navigationHistory);
        if (candidateScreens == null || candidateScreens.isEmpty()) {
            return;
        }

        int count = 0;
        for (String targetScreen : candidateScreens) {
            if (count >= PrefetchConfig.MAX_PREFETCH_TARGETS) {
                break;
            }
            prefetchDefaultScreen(targetScreen);
            count++;
        }
    }

    /**
     * Triggers prefetch using default payload and sample data suppliers.
     */
    public void prefetchDefaultScreen(@NonNull String screenName) {
        String cacheKey = "default_" + screenName;

        // Deduplication 1: already valid in RAM
        if (cache.containsValid(cacheKey)) {
            return;
        }

        // Deduplication 2: already in-flight
        if (inFlightRequests.containsKey(cacheKey)) {
            return;
        }

        double sizeKb = ScreenPayloadConfig.getBaseSizeKb(screenName) * networkEngine.getCurrentWeight().getMultiplier();
        long sizeBytes = (long) (sizeKb * 1024);

        triggerPrefetch(screenName, cacheKey, () -> createDefaultData(screenName), sizeBytes);
    }

    /**
     * Submits a proactive prefetch task into background execution.
     */
    public <T> void triggerPrefetch(
            @NonNull String screenName,
            @NonNull String cacheKey,
            @NonNull Supplier<T> dataSupplier,
            long payloadSizeBytes) {

        // Double-check deduplication
        if (cache.containsValid(cacheKey) || inFlightRequests.containsKey(cacheKey)) {
            return;
        }

        String reqId = "prefetch-" + UUID.randomUUID().toString().substring(0, 6);
        long startTime = System.currentTimeMillis();

        notifyTriggered(screenName, cacheKey, payloadSizeBytes);

        // Execute via MockNetworkEngine for deterministic bandwidth sharing
        CompletableFuture<T> future = networkEngine.executeWithDataSupplier(
                reqId,
                screenName,
                false, // background
                dataSupplier
        ).thenApply(simResp -> {
            T payload = simResp.getData();
            long duration = System.currentTimeMillis() - startTime;

            // Store into RAM cache with dynamic TTL
            long ttl = PrefetchRuntimeConfig.getInstance().getCacheTtlMs();
            cache.put(cacheKey, screenName, payload, payloadSizeBytes, ttl);

            // Remove from in-flight
            inFlightRequests.remove(cacheKey);

            notifyCompleted(screenName, cacheKey, duration, payloadSizeBytes);
            return payload;
        }).exceptionally(ex -> {
            inFlightRequests.remove(cacheKey);
            notifyFailed(screenName, cacheKey, ex);
            return null;
        });

        // Register in-flight tracker
        InFlightTask<T> task = new InFlightTask<>(reqId, screenName, cacheKey, startTime, payloadSizeBytes, future);
        inFlightRequests.put(cacheKey, task);
    }

    // --- Acquisition API (Called by Repositories / Consumers) ---

    /**
     * Core API invoked by Data Repositories when a screen opens.
     * Evaluates FULL_HIT, LATE_HIT, or MISS.
     */
    @NonNull
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<PrefetchResult<T>> acquire(
            @NonNull String screenName,
            @NonNull String cacheKey,
            @NonNull Supplier<T> fallbackNetworkSupplier) {

        long acquireStart = System.currentTimeMillis();
        long estimatedPureLatency = calculateEstimatedPureLatency(screenName);
        double sizeKb = ScreenPayloadConfig.getBaseSizeKb(screenName) * networkEngine.getCurrentWeight().getMultiplier();
        long sizeBytes = (long) (sizeKb * 1024);

        String effectiveKey = cacheKey;

        // CASE 1: FULL_HIT (Already in RAM and valid)
        PrefetchEntry<T> cachedEntry = cache.get(effectiveKey);
        if (cachedEntry == null && !effectiveKey.startsWith("default_")) {
            cachedEntry = cache.get("default_" + screenName);
            if (cachedEntry != null) {
                effectiveKey = "default_" + screenName;
            }
        }

        if (cachedEntry != null) {
            cachedEntry.markUsed();
            cache.recordFullHit();

            PrefetchResult<T> result = new PrefetchResult<>(
                    cachedEntry.getData(),
                    PrefetchHitType.FULL_HIT,
                    screenName,
                    effectiveKey,
                    0L,                     // 0ms wait for user!
                    estimatedPureLatency,   // Entire network delay saved!
                    cachedEntry.getPayloadSizeBytes()
            );
            notifyAcquire(result);
            return CompletableFuture.completedFuture(result);
        }

        // CASE 2: LATE_HIT (In-flight download in progress, if enabled)
        if (PrefetchRuntimeConfig.getInstance().isLateHitEnabled()) {
            InFlightTask<?> inFlight = inFlightRequests.get(effectiveKey);
            if (inFlight == null && !effectiveKey.startsWith("default_")) {
                inFlight = inFlightRequests.get("default_" + screenName);
                if (inFlight != null) {
                    effectiveKey = "default_" + screenName;
                }
            }

            if (inFlight != null) {
                final InFlightTask<?> targetTask = inFlight;
                cache.recordLateHit();
                final String finalKey = effectiveKey;

                return ((CompletableFuture<T>) targetTask.future).handle((data, ex) -> {
                    long waitMs = System.currentTimeMillis() - acquireStart;
                    long savedMs = Math.max(0, estimatedPureLatency - waitMs);

                    // Mark used in cache once landed
                    PrefetchEntry<?> landed = cache.get(finalKey);
                    if (landed != null) {
                        landed.markUsed();
                    }

                    PrefetchResult<T> result = new PrefetchResult<>(
                            data,
                            PrefetchHitType.LATE_HIT,
                            screenName,
                            finalKey,
                            waitMs,
                            savedMs,
                            targetTask.payloadSizeBytes
                    );
                    notifyAcquire(result);
                    return result;
                });
            }
        }

        // CASE 3: MISS (Not preloaded -> standard foreground fetch)
        cache.recordMiss();
        String reqId = "miss-" + UUID.randomUUID().toString().substring(0, 6);

        return networkEngine.executeWithDataSupplier(
                reqId,
                screenName,
                true, // foreground
                fallbackNetworkSupplier
        ).handle((simResp, ex) -> {
            long waitMs = System.currentTimeMillis() - acquireStart;
            T data = simResp != null ? simResp.getData() : null;
            long payloadSize = simResp != null ? (long) (simResp.getPayloadSizeKb() * 1024) : sizeBytes;

            PrefetchResult<T> result = new PrefetchResult<>(
                    data,
                    PrefetchHitType.MISS,
                    screenName,
                    cacheKey,
                    waitMs,
                    0L, // No time saved
                    payloadSize
            );
            notifyAcquire(result);
            return result;
        });
    }

    private long calculateEstimatedPureLatency(@NonNull String screenName) {
        NetworkProfile profile = networkEngine.getCurrentProfile();
        PayloadWeight weight = networkEngine.getCurrentWeight();
        double payloadKb = ScreenPayloadConfig.getBaseSizeKb(screenName) * weight.getMultiplier();
        double transferSeconds = (payloadKb * 8.0) / (profile.getBandwidthKbps());
        return profile.getRttMs() + (long) (transferSeconds * 1000.0);
    }

    @Nullable
    private Object createDefaultData(@NonNull String screenName) {
        if (ScreenPayloadConfig.SCREEN_HOME.equals(screenName)) {
            return FakeApi.getSampleCategories();
        } else if (ScreenPayloadConfig.SCREEN_POST_DETAIL.equals(screenName)) {
            return FakeApi.getSamplePosts().get(0);
        } else if (ScreenPayloadConfig.SCREEN_COMMENTS.equals(screenName)) {
            return "25 Comments for post";
        } else if (ScreenPayloadConfig.SCREEN_PROFILE.equals(screenName)) {
            return "Profile: Author";
        }
        return "Simulated Payload for " + screenName;
    }

    public void cancelAllInFlight() {
        for (InFlightTask<?> task : inFlightRequests.values()) {
            task.future.cancel(true);
        }
        inFlightRequests.clear();
    }

    public int getInFlightCount() {
        return inFlightRequests.size();
    }

    public List<String> getNavigationHistory() {
        return Collections.unmodifiableList(new ArrayList<>(navigationHistory));
    }

    // --- Telemetry Dispatchers ---

    private void notifyTriggered(@NonNull String screen, @NonNull String key, long bytes) {
        for (IPrefetchTelemetryListener l : listeners) {
            try { l.onPrefetchTriggered(screen, key, bytes); } catch (Throwable ignored) {}
        }
    }

    private void notifyCompleted(@NonNull String screen, @NonNull String key, long duration, long bytes) {
        for (IPrefetchTelemetryListener l : listeners) {
            try { l.onPrefetchCompleted(screen, key, duration, bytes); } catch (Throwable ignored) {}
        }
    }

    private void notifyFailed(@NonNull String screen, @NonNull String key, @NonNull Throwable error) {
        for (IPrefetchTelemetryListener l : listeners) {
            try { l.onPrefetchFailed(screen, key, error); } catch (Throwable ignored) {}
        }
    }

    private void notifyAcquire(@NonNull PrefetchResult<?> result) {
        for (IPrefetchTelemetryListener l : listeners) {
            try { l.onAcquireResult(result); } catch (Throwable ignored) {}
        }
    }

    // --- Inner Tracker Class ---

    public static class InFlightTask<T> {
        public final String requestId;
        public final String screenName;
        public final String cacheKey;
        public final long startTimeMs;
        public final long payloadSizeBytes;
        public final CompletableFuture<T> future;

        public InFlightTask(
                String requestId,
                String screenName,
                String cacheKey,
                long startTimeMs,
                long payloadSizeBytes,
                CompletableFuture<T> future) {
            this.requestId = requestId;
            this.screenName = screenName;
            this.cacheKey = cacheKey;
            this.startTimeMs = startTimeMs;
            this.payloadSizeBytes = payloadSizeBytes;
            this.future = future;
        }
    }
}
