package com.socialapp.core.prefetch;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.socialapp.MyApplication;
import com.socialapp.core.di.AppContainer;
import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.network.dto.ProfileResource;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cửa soát vé trung tâm theo đặc tả A1: Gom mọi chỗ lấy dữ liệu trong app về một hàm Repository.load(screen).
 * Kiểm tra PrefetchCache (FULL HIT) -> InFlightTask (LATE HIT) -> MISS (Nạp mạng giả / mạng thật).
 * Tự động ghi nhận log vào MetricsLogger và kích hoạt Prefetch cho màn hình kế tiếp.
 */
public class Repository {

    private static final String TAG = "Repository";
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final ExecutorService workerExecutor = Executors.newCachedThreadPool();

    public interface RealDataLoader<T> {
        void execute(@NonNull ScreenLoadCallback<T> callback);
    }

    private static int currentSessionId = 1;
    private static int currentStepIndex = 1;
    private static ScreenId lastScreen = null;
    private static HitType lastHitType = HitType.MISS;
    private static long lastLatencyMs = 0;
    private static long transitionNavStartTimeMs = 0;

    public static synchronized HitType getLastHitType() {
        return lastHitType;
    }

    public static synchronized long getLastLatencyMs() {
        return lastLatencyMs;
    }

    public static synchronized void setSessionId(int sessionId) {
        if (currentSessionId != sessionId) {
            AppContainer container = MyApplication.getInstance() != null ? MyApplication.getInstance().getAppContainer() : null;
            if (container != null) {
                container.getPrefetchEngine().startNewSession();
            }
        }
        currentSessionId = sessionId;
        currentStepIndex = 1;
        lastScreen = null;
    }

    public static synchronized int getSessionId() {
        return currentSessionId;
    }

    public static synchronized int getStepIndex() {
        return currentStepIndex;
    }

    /**
     * Đánh dấu thời điểm bắt đầu chuyển hướng giao diện (dành cho đo ui_latency_ms).
     */
    public static void markNavigationStart() {
        transitionNavStartTimeMs = SystemClock.elapsedRealtime();
    }

    /**
     * Cửa nạp dữ liệu thống nhất: Repository.load(screen)
     */
    public static <T> void load(@NonNull ScreenId screen, @NonNull ScreenLoadCallback<T> callback) {
        load(screen, null, null, callback);
    }

    public static <T> void load(@NonNull ScreenId screen, @Nullable Bundle params, @NonNull ScreenLoadCallback<T> callback) {
        load(screen, params, null, callback);
    }

    public static <T> void load(
            @NonNull ScreenId screen,
            @Nullable Bundle params,
            @Nullable RealDataLoader<T> realDataLoader,
            @NonNull ScreenLoadCallback<T> callback
    ) {
        long startTime = SystemClock.elapsedRealtime();
        AppContainer container = MyApplication.getInstance() != null ? MyApplication.getInstance().getAppContainer() : null;

        if (container == null) {
            Log.w(TAG, "MyApplication instance is null, loading mock directly.");
            T data = (T) FakeDataSource.getSampleDataForScreen(screen, params);
            callback.onLoaded(HitType.MISS, 0, data);
            return;
        }

        PrefetchEngine prefetchEngine = container.getPrefetchEngine();
        MetricsLogger metricsLogger = container.getMetricsLogger();
        PrefetchCache cache = prefetchEngine.getCache();

        updateActiveContextFromParams(prefetchEngine, params);

        ScreenId fromScreen = lastScreen;
        lastScreen = screen;

        // 1. Kiểm tra FULL HIT trong PrefetchCache (tra cứu theo khóa ngữ cảnh ID + hỗ trợ SWR 2 tầng)
        if (cache.containsContextual(screen, params)) {
            PrefetchEntry<?> entry = cache.getContextual(screen, params);
            Object rawData = entry != null ? entry.getData() : null;

            if (isCachedDataCompatible(screen, params, rawData, prefetchEngine.isSimulationMode())) {
                cache.markUsedContextual(screen, params);
                long latencyMs = 0;
                long uiLatencyMs = transitionNavStartTimeMs > 0 ? (SystemClock.elapsedRealtime() - transitionNavStartTimeMs) : latencyMs;

                if (prefetchEngine.isSimulationMode()) {
                    if (rawData == null || (rawData instanceof String && screen != ScreenId.FACT_CHECK && screen != ScreenId.SETTINGS)) {
                        rawData = FakeDataSource.getSampleDataForScreen(screen, params);
                    } else if (screen == ScreenId.POST_DETAIL && params != null && rawData instanceof PostResource) {
                        int requestedPostId = params.getInt("postId", -1);
                        if (requestedPostId != -1 && ((PostResource) rawData).id != requestedPostId) {
                            rawData = FakeDataSource.getSampleDataForScreen(screen, params);
                        }
                    }
                }

                // [Ý 1 - SWR (Stale-While-Revalidate)]:
                // Nếu bản ghi đã qua hạn mềm (softTtl) nhưng vẫn còn hạn cứng (hardTtl),
                // trả ngay lập tức bản cũ trong 0ms lên UI, đồng thời âm thầm gọi mạng làm mới dưới nền.
                if (entry != null && entry.isStaleButUsable()
                        && !prefetchEngine.isSimulationMode() && realDataLoader != null) {
                    triggerSilentSwrRevalidation(entry, screen, params, realDataLoader, prefetchEngine, cache);
                }

                prefetchEngine.updateActiveIdsFromLoadedData(screen, rawData);
                T cachedData = (T) rawData;

                logAndNotify(metricsLogger, prefetchEngine, fromScreen, screen, HitType.FULL_HIT, latencyMs, uiLatencyMs, 0);

                T finalData = cachedData;
                mainHandler.post(() -> callback.onLoaded(HitType.FULL_HIT, latencyMs, finalData));
                return;
            }
        }

        // 2. Kiểm tra LATE HIT (Request prefetch đang bay dở dang — dùng chung cho cả mô phỏng & mạng thật)
        PrefetchEngine.InFlightTask inFlightTask = prefetchEngine.getInFlightTask(screen, params);
        if (inFlightTask != null) {
            inFlightTask.addWaiter((actualLoadTimeMs, loadedData) -> {
                long latencyMs = SystemClock.elapsedRealtime() - startTime;
                long uiLatencyMs = transitionNavStartTimeMs > 0 ? (SystemClock.elapsedRealtime() - transitionNavStartTimeMs) : latencyMs;

                if (!prefetchEngine.isSimulationMode() && realDataLoader != null
                        && !isCachedDataCompatible(screen, params, loadedData, false)) {
                    // Nếu dữ liệu tải trước ở mạng thật không khớp ID hoặc lỗi, gọi tiếp RealDataLoader
                    executeRealDataMiss(realDataLoader, callback, metricsLogger, prefetchEngine, cache, fromScreen, screen, params, startTime);
                    return;
                }

                cache.markUsedContextual(screen, params);
                Object effectiveData = loadedData;
                if (effectiveData == null && (prefetchEngine.isSimulationMode() || realDataLoader == null)) {
                    effectiveData = FakeDataSource.getSampleDataForScreen(screen, params);
                }

                prefetchEngine.updateActiveIdsFromLoadedData(screen, effectiveData);
                T data = (T) effectiveData;

                logAndNotify(metricsLogger, prefetchEngine, fromScreen, screen, HitType.LATE_HIT, latencyMs, uiLatencyMs, 0);

                mainHandler.post(() -> callback.onLoaded(HitType.LATE_HIT, latencyMs, data));
            });
            return;
        }

        // 3. MISS: Chưa được tải trước
        if (prefetchEngine.isSimulationMode() || realDataLoader == null) {
            // Chạy qua MockNetworkEngine (isForeground = true) để chia sẻ băng thông thực với các request prefetch nền
            String reqId = "fg-" + screen.getId() + "-" + UUID.randomUUID().toString().substring(0, 6);
            long fetchedBytes = (long) (screen.getBaseSizeBytes() * prefetchEngine.getPayloadScale());

            prefetchEngine.getMockNetworkEngine().executeWithDataSupplier(
                    reqId,
                    screen.getId(),
                    true, // foreground request
                    () -> FakeDataSource.getSampleDataForScreen(screen, params)
            ).whenComplete((simResp, ex) -> {
                long latencyMs = SystemClock.elapsedRealtime() - startTime;
                long uiLatencyMs = transitionNavStartTimeMs > 0 ? (SystemClock.elapsedRealtime() - transitionNavStartTimeMs) : latencyMs;

                Object rawData = (simResp != null && simResp.getData() != null)
                        ? simResp.getData()
                        : FakeDataSource.getSampleDataForScreen(screen, params);
                cache.putContextual(screen, params, rawData, fetchedBytes);
                cache.markUsedContextual(screen, params);
                prefetchEngine.updateActiveIdsFromLoadedData(screen, rawData);

                T data = (T) rawData;

                logAndNotify(metricsLogger, prefetchEngine, fromScreen, screen, HitType.MISS, latencyMs, uiLatencyMs, fetchedBytes);

                mainHandler.post(() -> callback.onLoaded(HitType.MISS, latencyMs, data));
            });
        } else {
            // Chạy gọi API thật (Real API Mode) và lưu kết quả vào PrefetchCache
            executeRealDataMiss(realDataLoader, callback, metricsLogger, prefetchEngine, cache, fromScreen, screen, params, startTime);
        }
    }

    /**
     * [Ý 1 - SWR Background Revalidation]:
     * Làm mới ngầm một bản ghi ở trạng thái STALE (softTtl < age < hardTtl) mà không chặn giao diện.
     */
    private static <T> void triggerSilentSwrRevalidation(
            @NonNull PrefetchEntry<?> staleEntry,
            @NonNull ScreenId screen,
            @Nullable Bundle params,
            @NonNull RealDataLoader<T> realDataLoader,
            @NonNull PrefetchEngine prefetchEngine,
            @NonNull PrefetchCache cache
    ) {
        if (!staleEntry.tryMarkRevalidating()) {
            return; // Đã có 1 luồng revalidate đang chạy cho mục này
        }
        String contextualKey = PrefetchCache.buildContextualKey(screen, params);
        prefetchEngine.notifySwrRevalidation(contextualKey, false);
        Log.d(TAG, "[SWR] Entry is stale, serving 0ms cached data and revalidating in background: "
                + contextualKey);
        workerExecutor.execute(() -> realDataLoader.execute(new ScreenLoadCallback<T>() {
            @Override
            public void onLoaded(@NonNull HitType hitType, long actualLatencyMs, @Nullable T freshData) {
                staleEntry.clearRevalidating();
                if (freshData != null) {
                    long sizeBytes = screen.getBaseSizeBytes();
                    cache.putContextual(screen, params, freshData, sizeBytes);
                    cache.markUsedContextual(screen, params);
                    prefetchEngine.updateActiveIdsFromLoadedData(screen, freshData);
                    prefetchEngine.notifySwrRevalidation(contextualKey, true);
                }
            }

            @Override
            public void onError(@NonNull Throwable error) {
                staleEntry.clearRevalidating();
            }
        }));
    }

    private static <T> void executeRealDataMiss(
            @NonNull RealDataLoader<T> realDataLoader,
            @NonNull ScreenLoadCallback<T> callback,
            @NonNull MetricsLogger metricsLogger,
            @NonNull PrefetchEngine prefetchEngine,
            @NonNull PrefetchCache cache,
            @Nullable ScreenId fromScreen,
            @NonNull ScreenId screen,
            @Nullable Bundle params,
            long startTime
    ) {
        realDataLoader.execute(new ScreenLoadCallback<T>() {
            @Override
            public void onLoaded(@NonNull HitType hitType, long actualLatencyMs, @Nullable T data) {
                long latencyMs = SystemClock.elapsedRealtime() - startTime;
                long uiLatencyMs = transitionNavStartTimeMs > 0 ? (SystemClock.elapsedRealtime() - transitionNavStartTimeMs) : latencyMs;
                long fetchedBytes = screen.getBaseSizeBytes();

                if (data != null) {
                    cache.putContextual(screen, params, data, fetchedBytes);
                    cache.markUsedContextual(screen, params);
                    prefetchEngine.updateActiveIdsFromLoadedData(screen, data);
                }

                logAndNotify(metricsLogger, prefetchEngine, fromScreen, screen, HitType.MISS, latencyMs, uiLatencyMs, fetchedBytes);

                callback.onLoaded(HitType.MISS, latencyMs, data);
            }

            @Override
            public void onError(@NonNull Throwable error) {
                callback.onError(error);
            }
        });
    }

    private static void updateActiveContextFromParams(@NonNull PrefetchEngine engine, @Nullable Bundle params) {
        if (params == null) return;
        int postId = params.getInt("postId", -1);
        if (postId > 0) {
            engine.setLastActivePostId(postId);
        }
        int userId = params.getInt("userId", -1);
        if (userId > 0) {
            engine.setLastActiveUserId(userId);
        }
    }

    private static boolean isCachedDataCompatible(
            @NonNull ScreenId screen,
            @Nullable Bundle params,
            @Nullable Object rawData,
            boolean simulationMode
    ) {
        if (simulationMode) {
            return true;
        }
        if (rawData == null) {
            return false;
        }
        if (rawData instanceof String && screen != ScreenId.FACT_CHECK && screen != ScreenId.SETTINGS) {
            return false;
        }
        if (screen == ScreenId.POST_DETAIL && params != null && rawData instanceof PostResource) {
            int requestedPostId = params.getInt("postId", -1);
            return requestedPostId == -1 || ((PostResource) rawData).id == requestedPostId;
        }
        if (screen == ScreenId.PROFILE && params != null && rawData instanceof ProfileResource) {
            int requestedUserId = params.getInt("userId", -1);
            ProfileResource profile = (ProfileResource) rawData;
            return requestedUserId == -1 || profile.getId() == requestedUserId;
        }
        return true;
    }

    private static synchronized void logAndNotify(
            @NonNull MetricsLogger metricsLogger,
            @NonNull PrefetchEngine prefetchEngine,
            @Nullable ScreenId fromScreen,
            @NonNull ScreenId toScreen,
            @NonNull HitType hitType,
            long latencyMs,
            long uiLatencyMs,
            long fetchedBytes
    ) {
        lastHitType = hitType;
        lastLatencyMs = latencyMs;
        long wastedBytes = prefetchEngine.getCache().flushUnusedWastedBytes();

        metricsLogger.logTransition(
                currentSessionId,
                currentStepIndex++,
                fromScreen,
                toScreen,
                hitType,
                latencyMs,
                uiLatencyMs,
                fetchedBytes,
                wastedBytes,
                prefetchEngine.getCurrentPolicy().code,
                prefetchEngine.getCurrentNetwork().code,
                prefetchEngine.getPayloadScale()
        );

        Log.d(TAG, String.format("[%s] %s -> %s | Latency: %d ms | HitType: %s",
                prefetchEngine.getCurrentPolicy().code,
                fromScreen != null ? fromScreen.getId() : "START",
                toScreen.getId(),
                latencyMs,
                hitType.name()
        ));

        // Kích hoạt cập nhật Markov & tải trước cho màn hình tiếp theo
        prefetchEngine.onScreenDisplayed(toScreen);
    }
}

