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

        ScreenId fromScreen = lastScreen;
        lastScreen = screen;

        // Nếu TẮT chế độ mô phỏng mạng và có RealDataLoader: Gọi thẳng API mạng thật
        if (!prefetchEngine.isSimulationMode() && realDataLoader != null) {
            realDataLoader.execute(new ScreenLoadCallback<T>() {
                @Override
                public void onLoaded(@NonNull HitType hitType, long actualLatencyMs, @Nullable T data) {
                    long latencyMs = SystemClock.elapsedRealtime() - startTime;
                    long uiLatencyMs = transitionNavStartTimeMs > 0 ? (SystemClock.elapsedRealtime() - transitionNavStartTimeMs) : latencyMs;
                    long fetchedBytes = screen.getBaseSizeBytes();

                    logAndNotify(metricsLogger, prefetchEngine, fromScreen, screen, HitType.MISS, latencyMs, uiLatencyMs, fetchedBytes);

                    callback.onLoaded(HitType.MISS, latencyMs, data);
                }

                @Override
                public void onError(@NonNull Throwable error) {
                    callback.onError(error);
                }
            });
            return;
        }

        // 1. Kiểm tra FULL HIT trong PrefetchCache
        if (cache.contains(screen)) {
            cache.markUsed(screen);
            long latencyMs = 0;
            long uiLatencyMs = transitionNavStartTimeMs > 0 ? (SystemClock.elapsedRealtime() - transitionNavStartTimeMs) : latencyMs;

            PrefetchEntry<?> entry = cache.get(screen);
            Object rawData = entry != null ? entry.getData() : null;
            if (rawData == null || (rawData instanceof String && screen != ScreenId.FACT_CHECK && screen != ScreenId.SETTINGS)) {
                rawData = FakeDataSource.getSampleDataForScreen(screen, params);
            } else if (screen == ScreenId.POST_DETAIL && params != null && rawData instanceof PostResource) {
                int requestedPostId = params.getInt("postId", -1);
                if (requestedPostId != -1 && ((PostResource) rawData).id != requestedPostId) {
                    rawData = FakeDataSource.getSampleDataForScreen(screen, params);
                }
            }
            T cachedData = (T) rawData;

            logAndNotify(metricsLogger, prefetchEngine, fromScreen, screen, HitType.FULL_HIT, latencyMs, uiLatencyMs, 0);

            T finalData = cachedData;
            mainHandler.post(() -> callback.onLoaded(HitType.FULL_HIT, latencyMs, finalData));
            return;
        }

        // 2. Kiểm tra LATE HIT (Request prefetch đang bay dở dang)
        PrefetchEngine.InFlightTask inFlightTask = prefetchEngine.getInFlightTask(screen);
        if (inFlightTask != null) {
            inFlightTask.addWaiter(actualLoadTimeMs -> {
                long latencyMs = SystemClock.elapsedRealtime() - startTime;
                long uiLatencyMs = transitionNavStartTimeMs > 0 ? (SystemClock.elapsedRealtime() - transitionNavStartTimeMs) : latencyMs;

                cache.markUsed(screen);
                T data = (T) FakeDataSource.getSampleDataForScreen(screen, params);

                logAndNotify(metricsLogger, prefetchEngine, fromScreen, screen, HitType.LATE_HIT, latencyMs, uiLatencyMs, 0);

                mainHandler.post(() -> callback.onLoaded(HitType.LATE_HIT, latencyMs, data));
            });
            return;
        }

        // 3. MISS: Chưa được tải trước
        if (prefetchEngine.isSimulationMode() || realDataLoader == null) {
            // Chạy qua Mạng giả lập (Simulation Mode)
            long simulatedDelayMs = prefetchEngine.calculateSimulatedNetworkDelayMs(screen);

            workerExecutor.execute(() -> {
                try {
                    Thread.sleep(simulatedDelayMs);
                } catch (InterruptedException ignored) {
                }

                long latencyMs = SystemClock.elapsedRealtime() - startTime;
                long uiLatencyMs = transitionNavStartTimeMs > 0 ? (SystemClock.elapsedRealtime() - transitionNavStartTimeMs) : latencyMs;
                long fetchedBytes = (long) (screen.getBaseSizeBytes() * prefetchEngine.getPayloadScale());

                T data = (T) FakeDataSource.getSampleDataForScreen(screen, params);

                logAndNotify(metricsLogger, prefetchEngine, fromScreen, screen, HitType.MISS, latencyMs, uiLatencyMs, fetchedBytes);

                mainHandler.post(() -> callback.onLoaded(HitType.MISS, latencyMs, data));
            });
        } else {
            // Chạy gọi API thật (Real API Mode)
            realDataLoader.execute(new ScreenLoadCallback<T>() {
                @Override
                public void onLoaded(@NonNull HitType hitType, long actualLatencyMs, @Nullable T data) {
                    long latencyMs = SystemClock.elapsedRealtime() - startTime;
                    long uiLatencyMs = transitionNavStartTimeMs > 0 ? (SystemClock.elapsedRealtime() - transitionNavStartTimeMs) : latencyMs;
                    long fetchedBytes = screen.getBaseSizeBytes();

                    logAndNotify(metricsLogger, prefetchEngine, fromScreen, screen, HitType.MISS, latencyMs, uiLatencyMs, fetchedBytes);

                    callback.onLoaded(HitType.MISS, latencyMs, data);
                }

                @Override
                public void onError(@NonNull Throwable error) {
                    callback.onError(error);
                }
            });
        }
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

