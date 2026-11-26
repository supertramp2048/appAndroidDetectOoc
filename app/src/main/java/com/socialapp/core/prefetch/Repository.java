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
import com.socialapp.core.telemetry.AndroidNavigationObserver;
import com.socialapp.core.telemetry.DataLoadEvent;
import com.socialapp.core.telemetry.EventSource;
import com.socialapp.core.telemetry.LoadContext;
import com.socialapp.core.telemetry.NavigationEvent;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cửa soát vé trung tâm theo đặc tả A1: Gom mọi chỗ lấy dữ liệu trong app về một hàm Repository.load(screen).
 * Kiểm tra PrefetchCache (FULL HIT) -> InFlightTask (LATE HIT) -> MISS (Nạp mạng giả / mạng thật).
 * Ghi nhận kết quả tải foreground vào MetricsLogger.
 */
public class Repository {

    private static final String TAG = "Repository";
    private static final String DATA_LOAD_TAG = "DataLoadTelemetry";
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final ExecutorService workerExecutor = Executors.newCachedThreadPool();

    public interface RealDataLoader<T> {
        void execute(@NonNull ScreenLoadCallback<T> callback, @NonNull LoadContext load);
    }

    private static int currentSessionId = 1;
    private static int liveSessionId = 1;
    private static UUID lastLiveTelemetrySessionId;
    private static LegacySession legacySession;
    private static HitType lastHitType = HitType.MISS;
    private static long lastLatencyMs = 0;

    private static final class LegacySession {
        final UUID telemetrySessionId;
        final MetricsLogger.LoadTarget target;
        final int sessionNumber;
        int nextStep = 1;

        LegacySession(UUID telemetrySessionId, MetricsLogger.LoadTarget target, int sessionNumber) {
            this.telemetrySessionId = telemetrySessionId;
            this.target = target;
            this.sessionNumber = sessionNumber;
        }
    }

    private static final class LoadMetrics {
        final LoadContext load;
        final LegacySession session;
        final String policy;
        final String network;
        final float payloadScale;
        final P5Experiment p5;

        LoadMetrics(LoadContext load, LegacySession session, PrefetchEngine engine) {
            this.load = load;
            this.session = session;
            policy = engine.getCurrentPolicy().code;
            network = engine.getCurrentNetwork().code;
            payloadScale = engine.getPayloadScale();
            p5 = engine.p5ForRun(load.runId);
        }

    }

    public static synchronized HitType getLastHitType() {
        return lastHitType;
    }

    public static synchronized long getLastLatencyMs() {
        return lastLatencyMs;
    }

    public static synchronized void setSessionId(int sessionId) {
        liveSessionId = sessionId;
        lastLiveTelemetrySessionId = null;
        resetLegacySession(sessionId);
    }

    static synchronized void setReplaySessionId(int sessionId) {
        resetLegacySession(sessionId);
    }

    private static void resetLegacySession(int sessionId) {
        currentSessionId = sessionId;
        legacySession = null;
    }

    public static synchronized int getSessionId() {
        return legacySession == null ? currentSessionId : legacySession.sessionNumber;
    }

    public static synchronized int getStepIndex() {
        return legacySession == null ? 1 : legacySession.nextStep;
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
        load(screen, params, realDataLoader, callback, LoadContext.Trigger.UNKNOWN, null);
    }

    public static <T> void load(
            @NonNull ScreenId screen,
            @Nullable Bundle params,
            @Nullable RealDataLoader<T> realDataLoader,
            @NonNull ScreenLoadCallback<T> callback,
            @NonNull LoadContext.Trigger trigger,
            @Nullable NavigationEvent confirmedNavigation
    ) {
        AppContainer container = MyApplication.getInstance() != null ? MyApplication.getInstance().getAppContainer() : null;

        if (container == null) {
            Log.w(TAG, "MyApplication instance is null, loading mock directly.");
            T data = (T) FakeDataSource.getSampleDataForScreen(screen, params);
            callback.onLoaded(HitType.MISS, 0, data);
            return;
        }

        PrefetchEngine prefetchEngine = container.getPrefetchEngine();
        MetricsLogger metricsLogger = container.getMetricsLogger();
        AndroidNavigationObserver observer = container.getNavigationObserver();
        LoadMetrics metrics = beginLoad(observer, metricsLogger, prefetchEngine, screen, trigger, confirmedNavigation);
        LoadContext load = metrics.load;

        // Nếu TẮT chế độ mô phỏng mạng và có RealDataLoader: Gọi thẳng API mạng thật
        if (!prefetchEngine.isSimulationMode() && realDataLoader != null) {
            ScreenLoadCallback<T> result = new ScreenLoadCallback<T>() {
                @Override
                public void onLoaded(@NonNull HitType hitType, long actualLatencyMs, @Nullable T data) {
                    DataLoadEvent event = complete(load, DataLoadEvent.Outcome.SUCCESS);
                    if (event == null) return;
                    logAndNotify(metricsLogger, observer, metrics, event, HitType.MISS, null);
                    callback.onLoaded(HitType.MISS, event.loadLatencyMs, data);
                }

                @Override
                public void onError(@NonNull Throwable error) {
                    reportFailure(load, callback, error);
                }

                @Override
                public void onCancelled(@NonNull Throwable error) {
                    if (complete(load, DataLoadEvent.Outcome.CANCELLED) != null) callback.onCancelled(error);
                }
            };
            try {
                realDataLoader.execute(result, load);
            } catch (RuntimeException error) {
                if (!reportFailure(load, callback, error)) throw error;
            }
            return;
        }

        // FULL HIT from valid data, or LATE HIT from the same session's in-flight transfer.
        if (supportsPrefetchedFixture(screen, params) && prefetchEngine.tryLoadPrefetched(screen, load.sessionId,
                new ScreenLoadCallback<Object>() {
            @Override
            public void onLoaded(@NonNull HitType hitType, long ignored, @Nullable Object data) {
                DataLoadEvent event = complete(load, DataLoadEvent.Outcome.SUCCESS);
                if (event == null) return;
                logAndNotify(metricsLogger, observer, metrics, event, hitType, 0L);
                mainHandler.post(() -> callback.onLoaded(hitType, event.loadLatencyMs, (T) data));
            }

            @Override
            public void onError(@NonNull Throwable error) {
                reportWorkerFailure(load, callback, error);
            }

            @Override
            public void onCancelled(@NonNull Throwable error) {
                if (complete(load, DataLoadEvent.Outcome.CANCELLED) != null) {
                    mainHandler.post(() -> callback.onCancelled(error));
                }
            }
        })) {
            return;
        }

        // 3. MISS: Mạng giả lập, hoặc fallback khi không có RealDataLoader.
        long simulatedDelayMs = prefetchEngine.calculateSimulatedNetworkDelayMs(screen);
        workerExecutor.execute(() -> {
            T data;
            try {
                Thread.sleep(simulatedDelayMs);
                data = (T) FakeDataSource.getSampleDataForScreen(screen, params);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                reportWorkerFailure(load, callback, error);
                return;
            } catch (RuntimeException error) {
                reportWorkerFailure(load, callback, error);
                return;
            }

            DataLoadEvent event = complete(load, DataLoadEvent.Outcome.SUCCESS);
            if (event == null) return;
            long fetchedBytes = (long) (screen.getBaseSizeBytes() * metrics.payloadScale);
            logAndNotify(metricsLogger, observer, metrics, event, HitType.MISS, fetchedBytes);
            mainHandler.post(() -> callback.onLoaded(HitType.MISS, event.loadLatencyMs, data));
        });
    }

    private static boolean supportsPrefetchedFixture(ScreenId screen, @Nullable Bundle params) {
        if (params == null) return true;
        if (screen == ScreenId.POST_DETAIL || screen == ScreenId.COMMENTS) return params.getInt("postId", 1) == 1;
        if (screen == ScreenId.PROFILE) return params.getInt("userId", 1) == 1;
        String query = params.getString("query");
        return params.getString("category") == null && (query == null || query.isEmpty());
    }

    private static synchronized LoadMetrics beginLoad(AndroidNavigationObserver observer, MetricsLogger logger,
                                                       PrefetchEngine engine, ScreenId screen, LoadContext.Trigger trigger,
                                                       NavigationEvent proof) {
        LoadContext load = observer.beginLoad(screen, trigger, proof);
        MetricsLogger.LoadTarget target = logger.captureLoadTarget(load.source == EventSource.REPLAY ? load.runId : null);
        if (load.sessionId == null || target == null) return new LoadMetrics(load, null, engine);
        if (legacySession == null || legacySession.target != target
                || !load.sessionId.equals(legacySession.telemetrySessionId)) {
            int number = load.replaySessionId != null ? load.replaySessionId : liveSessionNumber(load.sessionId);
            legacySession = new LegacySession(load.sessionId, target, number);
        }
        return new LoadMetrics(load, legacySession, engine);
    }

    private static int liveSessionNumber(UUID sessionId) {
        if (lastLiveTelemetrySessionId != null && !lastLiveTelemetrySessionId.equals(sessionId)) liveSessionId++;
        lastLiveTelemetrySessionId = sessionId;
        return liveSessionId;
    }

    private static <T> boolean reportFailure(LoadContext load, ScreenLoadCallback<T> callback, Throwable error) {
        if (complete(load, DataLoadEvent.Outcome.fromError(error)) == null) return false;
        callback.onError(error);
        return true;
    }

    private static <T> void reportWorkerFailure(LoadContext load, ScreenLoadCallback<T> callback, Throwable error) {
        if (complete(load, DataLoadEvent.Outcome.fromError(error)) != null) {
            mainHandler.post(() -> callback.onError(error));
        }
    }

    private static DataLoadEvent complete(LoadContext load, DataLoadEvent.Outcome outcome) {
        DataLoadEvent event = load.complete(SystemClock.elapsedRealtime(), outcome);
        if (event != null) {
            Log.i(DATA_LOAD_TAG, "loadId=" + load.loadId + " runId=" + load.runId
                    + " sessionId=" + load.sessionId + " replaySessionId=" + load.replaySessionId
                    + " screen=" + load.screen + " source=" + load.source + " trigger=" + load.trigger
                    + " navigationId=" + load.navigationId + " link=" + (load.navigationId == null ? "UNLINKED" : "LINKED")
                    + " startedAtMs=" + load.startedAtMs + " finishedAtMs=" + event.finishedAtMs
                    + " loadLatencyMs=" + event.loadLatencyMs + " outcome=" + event.outcome);
        }
        return event;
    }

    private static synchronized void logAndNotify(
            MetricsLogger metricsLogger, AndroidNavigationObserver observer, LoadMetrics metrics,
            DataLoadEvent event, HitType hitType, @Nullable Long simulatedBytes
    ) {
        LegacySession session = metrics.session;
        if (session == null) return;
        observer.withLoadSession(event.context, current -> {
            boolean active = metricsLogger.logLoad(
                    session.target,
                    session == legacySession && current,
                    session.nextStep++,
                    event,
                    hitType,
                    simulatedBytes,
                    metrics.policy,
                    metrics.network,
                    metrics.payloadScale
            );
            if (metrics.p5 != null && simulatedBytes != null) {
                metrics.p5.foreground(event.context, hitType);
            }
            if (active) {
                lastHitType = hitType;
                lastLatencyMs = event.loadLatencyMs;
            }
        });
    }
}
