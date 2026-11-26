package com.socialapp.core.prefetch;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.socialapp.core.prefetch.policy.markov.MarkovPredictor;
import com.socialapp.core.prefetch.profile.NavigationProfile;
import com.socialapp.core.telemetry.NavigationEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.EnumMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Bộ xử lý Tải trước (Prefetch Engine):
 * - Quản lý cấu hình mạng giả (WiFi, LTE, 3G, EDGE).
 * - Quản lý chính sách tải trước (P0 đến P5).
 * - Dự đoán Markov bậc 1, bậc 2 và phát request tải ngầm.
 * - Xử lý Late Hit (gộp request đang bay, không gửi trùng lặp).
 */
public class PrefetchEngine {

    private static final String TAG = "PrefetchEngine";
    private static final String LEARNING_TAG = "NavigationLearning";

    public enum Policy {
        P0_NONE("P0", "Không tải trước (Baseline)"),
        P1_ALL("P1", "Tải trước tất cả"),
        P2_TOP("P2", "Tải màn phổ biến nhất"),
        P3_MARKOV_FIXED("P3", "Markov ngưỡng cố định (>= 30%)"),
        P4_MARKOV_ORDER2("P4", "Markov bậc 2"),
        P5_ADAPTIVE_NETWORK("P5", "Ngưỡng mạng thích ứng");

        public final String code;
        public final String description;

        Policy(String code, String description) {
            this.code = code;
            this.description = description;
        }
    }

    public enum NetworkProfile {
        WIFI("WIFI", 20, 30.0f * 1024 * 1024),   // RTT 20ms, 30 MB/s
        LTE("LTE", 60, 15.0f * 1024 * 1024),     // RTT 60ms, 15 MB/s
        G3("3G", 150, 2.0f * 1024 * 1024),       // RTT 150ms, 2 MB/s
        EDGE("EDGE", 400, 0.2f * 1024 * 1024);   // RTT 400ms, 200 KB/s

        public final String code;
        public final long rttMs;
        public final float bandwidthBps; // Bytes per second

        NetworkProfile(String code, long rttMs, float bandwidthBps) {
            this.code = code;
            this.rttMs = rttMs;
            this.bandwidthBps = bandwidthBps;
        }
    }

    public enum P5Mode { LEGACY, FIXED_LAMBDA, ADAPTIVE_LAMBDA }

    public static final class LambdaSnapshot {
        public final P5Mode mode;
        public final double initial;
        public final double current;
        public final int updates;
        public final long completedBytes;
        public final long wastedBytes;
        public final Double wasteRatio;
        public final Double missRate;
        public final AdaptiveLambdaController.Reason reason;

        LambdaSnapshot(P5Mode mode, double initial, double current, int updates, long completedBytes,
                       long wastedBytes, Double wasteRatio, Double missRate, AdaptiveLambdaController.Reason reason) {
            this.mode = mode;
            this.initial = initial;
            this.current = current;
            this.updates = updates;
            this.completedBytes = completedBytes;
            this.wastedBytes = wastedBytes;
            this.wasteRatio = wasteRatio;
            this.missRate = missRate;
            this.reason = reason;
        }
    }

    private static final class InFlightTask {
        final long generation;
        final P5Experiment.Attempt attempt;
        final List<ScreenLoadCallback<Object>> waiters = new ArrayList<>();
        FutureTask<Void> work;

        InFlightTask(long generation, P5Experiment.Attempt attempt) {
            this.generation = generation;
            this.attempt = attempt;
        }
    }

    public interface OnPredictionUpdatedListener {
        void onPredictionUpdated(ScreenId currentScreen, List<Map.Entry<ScreenId, Double>> predictions, Policy policy);
    }

    private final PrefetchCache cache;
    private final Executor prefetchExecutor;
    private final Consumer<Runnable> postToMain;
    private final Consumer<String> learningLog;
    private final Map<ScreenId, InFlightTask> inFlightMap = new EnumMap<>(ScreenId.class);
    private UUID cacheSessionId;
    private BiConsumer<Long, Integer> wasteSink;
    private long cacheGeneration;
    private int cancelledPrefetches;
    private P5Mode nextP5Mode = P5Mode.LEGACY;
    private AdaptiveLambdaController.Config lambdaConfig = AdaptiveLambdaController.Config.DEFAULT;
    private volatile P5Experiment p5Replay;

    // Lịch sử điều hướng người dùng và bộ dự đoán Markov chuẩn của B (MarkovPredictor)
    private final List<ScreenId> navigationHistory = new ArrayList<>();
    private final MarkovPredictor<ScreenId> markovOrder1 = new MarkovPredictor<>(1, 3);
    private final MarkovPredictor<ScreenId> markovOrder2 = new MarkovPredictor<>(2, 3);
    private final Set<UUID> observedSessions = new HashSet<>();
    private UUID lastEventSession;
    private long lastEventSequence;

    private Policy currentPolicy = Policy.P3_MARKOV_FIXED;
    private NetworkProfile currentNetwork = NetworkProfile.LTE;
    private float payloadScale = 1.0f;
    private float timeScale = 1.0f; // Hệ số tua nhanh (1.0 = thời gian thực)
    private boolean simulationMode = true;

    private ScreenId lastCurrentScreen = ScreenId.FEED_NEWEST;
    private List<Map.Entry<ScreenId, Double>> lastPredictions = Collections.emptyList();

    private OnPredictionUpdatedListener predictionListener;

    public PrefetchEngine() {
        this(new PrefetchCache(), new Handler(Looper.getMainLooper())::post, message -> Log.d(LEARNING_TAG, message));
    }

    PrefetchEngine(PrefetchCache cache, Consumer<Runnable> postToMain, Consumer<String> learningLog) {
        this(cache, postToMain, learningLog, Executors.newFixedThreadPool(2));
    }

    PrefetchEngine(PrefetchCache cache, Consumer<Runnable> postToMain, Consumer<String> learningLog, Executor prefetchExecutor) {
        this.cache = cache;
        this.postToMain = postToMain;
        this.learningLog = learningLog;
        this.prefetchExecutor = prefetchExecutor;
        seedInitialTransitions();
    }

    public ScreenId getLastCurrentScreen() {
        return lastCurrentScreen;
    }

    public List<Map.Entry<ScreenId, Double>> getLastPredictions() {
        return lastPredictions;
    }

    public void setPredictionListener(@Nullable OnPredictionUpdatedListener listener) {
        this.predictionListener = listener;
    }

    public PrefetchCache getCache() {
        return cache;
    }

    public Policy getCurrentPolicy() {
        return currentPolicy;
    }

    public void setCurrentPolicy(Policy currentPolicy) {
        this.currentPolicy = currentPolicy;
    }

    public P5Mode getNextP5Mode() { return nextP5Mode; }

    public void setNextP5Mode(P5Mode mode) { nextP5Mode = mode; }

    public void setLambdaConfig(AdaptiveLambdaController.Config config) { lambdaConfig = config; }

    boolean usesDefaultLambdaConfig() { return lambdaConfig == AdaptiveLambdaController.Config.DEFAULT; }

    synchronized void beginP5Replay(UUID runId, Consumer<String> sink) {
        if (currentPolicy == Policy.P5_ADAPTIVE_NETWORK) {
            p5Replay = new P5Experiment(runId, nextP5Mode, lambdaConfig, sink);
        }
    }

    synchronized LambdaSnapshot finishP5Replay(UUID runId) {
        P5Experiment experiment = p5ForRun(runId);
        if (experiment == null) return null;
        LambdaSnapshot result = experiment.finish();
        p5Replay = null;
        return result;
    }

    P5Experiment p5ForRun(UUID runId) {
        P5Experiment experiment = p5Replay;
        return experiment != null && experiment.matches(runId) ? experiment : null;
    }

    public LambdaSnapshot getP5Snapshot() {
        P5Experiment experiment = p5Replay;
        return experiment == null ? null : experiment.snapshot();
    }

    public NetworkProfile getCurrentNetwork() {
        return currentNetwork;
    }

    public void setCurrentNetwork(NetworkProfile currentNetwork) {
        this.currentNetwork = currentNetwork;
    }

    public float getPayloadScale() {
        return payloadScale;
    }

    public void setPayloadScale(float payloadScale) {
        this.payloadScale = payloadScale;
    }

    public float getTimeScale() {
        return timeScale;
    }

    public void setTimeScale(float timeScale) {
        this.timeScale = Math.max(0.1f, timeScale);
    }

    public boolean isSimulationMode() {
        return simulationMode;
    }

    public synchronized void setSimulationMode(boolean simulationMode) {
        if (this.simulationMode != simulationMode) closeCacheGeneration();
        this.simulationMode = simulationMode;
    }

    /**
     * Tính độ trễ mạng giả theo công thức: RTT + (Dung lượng * scale) / Băng thông.
     */
    public long calculateSimulatedNetworkDelayMs(ScreenId screenId) {
        return Math.max(10, (long) (estimatedLoadMs(screenId) / timeScale));
    }

    long scaledPayloadBytes(ScreenId screen) {
        return (long) (screen.getBaseSizeBytes() * payloadScale);
    }

    long estimatedLoadMs(ScreenId screenId) {
        long bytes = scaledPayloadBytes(screenId);
        double transferSec = (double) bytes / currentNetwork.bandwidthBps;
        return currentNetwork.rttMs + (long) (transferSec * 1000);
    }

    /**
     * Kiểm tra xem màn hình này đang có task tải trước chạy ngầm dở dang hay không.
     */
    boolean tryLoadPrefetched(ScreenId screen, UUID sessionId, ScreenLoadCallback<Object> callback) {
        PrefetchEntry<?> cached;
        synchronized (this) {
            if (!simulationMode || sessionId == null || !sessionId.equals(cacheSessionId)) return false;
            cached = cache.takePrefetched(screen);
            if (cached == null) {
                InFlightTask task = inFlightMap.get(screen);
                if (task == null) return false;
                task.waiters.add(callback);
                return true;
            }
        }
        callback.onLoaded(HitType.FULL_HIT, 0, cached.getData());
        return true;
    }

    public synchronized void setCacheSession(@Nullable UUID sessionId, @Nullable BiConsumer<Long, Integer> onWaste) {
        closeCacheGeneration();
        cacheSessionId = sessionId;
        wasteSink = onWaste;
    }

    private void closeCacheGeneration() {
        cacheGeneration++;
        List<InFlightTask> pending = new ArrayList<>(inFlightMap.values());
        inFlightMap.clear();
        long waste = cache.closeResearchScope();
        int cancelled = cancelledPrefetches + pending.size();
        cancelledPrefetches = 0;
        if (wasteSink != null) wasteSink.accept(waste, cancelled);
        for (InFlightTask task : pending) {
            if (task.attempt != null) task.attempt.cancel();
            task.work.cancel(true);
            if (!task.waiters.isEmpty()) {
                postToMain.accept(() -> {
                    for (ScreenLoadCallback<Object> waiter : task.waiters) {
                        waiter.onCancelled(new CancellationException("Prefetch scope closed"));
                    }
                });
            }
        }
    }

    /**
     * Ghi nhận màn hình người dùng vừa bước vào:
     * 1. Cập nhật ma trận Markov.
     * 2. Dự đoán xác suất các màn hình kế tiếp.
     * 3. Quyết định chính sách và phát request tải trước vào cache.
     */
    public synchronized boolean onNavigationEvent(@NonNull NavigationEvent event) {
        boolean sameSession = event.sessionId.equals(lastEventSession);
        if (event.fromScreen == event.toScreen
                || (sameSession ? event.sequence <= lastEventSequence : !observedSessions.add(event.sessionId))) {
            return false;
        }
        boolean consecutive = sameSession && event.sequence == lastEventSequence + 1;
        lastEventSession = event.sessionId;
        lastEventSequence = event.sequence;

        if (!consecutive || event.fromScreen == null || navigationHistory.isEmpty()
                || navigationHistory.get(navigationHistory.size() - 1) != event.fromScreen) {
            navigationHistory.clear();
        }
        boolean learned = learnNavigation(event);

        ScreenId currentScreen = event.toScreen;
        lastCurrentScreen = currentScreen;
        List<Map.Entry<ScreenId, Double>> predictions = predictNext(currentScreen);
        lastPredictions = predictions;

        // Báo cho UI Overlay
        if (predictionListener != null) {
            postToMain.accept(() -> {
                if (predictionListener != null) {
                    predictionListener.onPredictionUpdated(currentScreen, predictions, currentPolicy);
                }
            });
        }

        // Nếu chính sách là P0 hoặc đang TẮT mô phỏng thì không tải giả lập
        if (currentPolicy == Policy.P0_NONE || !simulationMode
                || (cacheSessionId != null && !cacheSessionId.equals(event.sessionId))) {
            return learned;
        }

        // Chọn danh sách màn hình cần tải trước theo chính sách
        if (currentPolicy == Policy.P5_ADAPTIVE_NETWORK) {
            scheduleP5(event, predictions);
            return learned;
        }
        List<ScreenId> targetsToPrefetch = selectPrefetchTargets(predictions);

        // Kích hoạt tải trước trong nền
        for (ScreenId target : targetsToPrefetch) {
            triggerBackgroundPrefetch(target);
        }
        return learned;
    }

    private List<ScreenId> selectPrefetchTargets(List<Map.Entry<ScreenId, Double>> predictions) {
        List<ScreenId> selected = new ArrayList<>();

        switch (currentPolicy) {
            case P1_ALL:
                for (Map.Entry<ScreenId, Double> entry : predictions) {
                    selected.add(entry.getKey());
                }
                break;

            case P2_TOP:
                if (!predictions.isEmpty()) {
                    selected.add(predictions.get(0).getKey());
                }
                break;

            case P3_MARKOV_FIXED:
                // Tải nếu xác suất >= 30%
                for (Map.Entry<ScreenId, Double> entry : predictions) {
                    if (entry.getValue() >= 0.30) {
                        selected.add(entry.getKey());
                    }
                }
                break;

            case P4_MARKOV_ORDER2:
                // Dùng kết quả bậc 2 đã được tính trong predictNext, chọn top hoặc >= 25%
                for (Map.Entry<ScreenId, Double> entry : predictions) {
                    if (entry.getValue() >= 0.25) {
                        selected.add(entry.getKey());
                    }
                }
                break;

            case P0_NONE:
            default:
                break;
        }

        return selected;
    }

    private void scheduleP5(NavigationEvent event, List<Map.Entry<ScreenId, Double>> predictions) {
        P5Experiment experiment = p5ForRun(event.runId);
        boolean legacy = experiment == null || experiment.mode() == P5Mode.LEGACY;
        double lambda = legacy ? P5Experiment.LEGACY_LAMBDA : experiment.lambda();
        for (Map.Entry<ScreenId, Double> entry : predictions) {
            ScreenId target = entry.getKey();
            double probability = entry.getValue();
            long scaledBytes = scaledPayloadBytes(target);
            long estimate = legacy ? calculateSimulatedNetworkDelayMs(target) : estimatedLoadMs(target);
            long costBytes = legacy ? target.getBaseSizeBytes() : scaledBytes;
            double utility = P5Experiment.utility(probability, estimate, lambda, costBytes);
            boolean selected = legacy ? legacySelected(probability, estimate, costBytes) : utility > 0;
            P5Experiment.Attempt attempt = experiment == null ? null : experiment.consider(
                    event, target, probability, estimate, scaledBytes, lambda, utility, selected);
            if (selected) {
                if (attempt == null) triggerBackgroundPrefetch(target);
                else triggerBackgroundPrefetch(target, attempt);
            }
        }
    }

    static boolean legacySelected(double probability, long estimatedMs, long baseBytes) {
        return probability * estimatedMs > P5Experiment.LEGACY_LAMBDA * baseBytes || probability >= 0.40;
    }

    synchronized void triggerBackgroundPrefetch(ScreenId screenId) {
        triggerBackgroundPrefetch(screenId, null);
    }

    private void triggerBackgroundPrefetch(ScreenId screenId, P5Experiment.Attempt attempt) {
        if (cacheSessionId == null || cache.contains(screenId) || inFlightMap.containsKey(screenId)) {
            if (attempt != null) attempt.skip();
            return;
        }
        long delayMs = calculateSimulatedNetworkDelayMs(screenId);
        long sizeBytes = scaledPayloadBytes(screenId);
        if (attempt != null) attempt.dispatch();
        InFlightTask task = new InFlightTask(cacheGeneration, attempt);
        inFlightMap.put(screenId, task);

        task.work = new FutureTask<>(() -> {
            Object sampleData;
            try {
                synchronized (this) {
                    if (task.generation != cacheGeneration) return;
                }
                Thread.sleep(delayMs);
                sampleData = FakeDataSource.getSampleDataForScreen(screenId);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                failPrefetch(screenId, task, error, true);
                return;
            } catch (RuntimeException error) {
                failPrefetch(screenId, task, error, false);
                return;
            }

            synchronized (this) {
                if (task.generation != cacheGeneration || !inFlightMap.remove(screenId, task)) return;
                if (task.attempt != null) task.attempt.complete();
                cache.putPrefetched(screenId, sampleData, sizeBytes, !task.waiters.isEmpty(), task.attempt);
            }
            for (ScreenLoadCallback<Object> waiter : task.waiters) {
                waiter.onLoaded(HitType.LATE_HIT, 0, sampleData);
            }
        }, null);
        prefetchExecutor.execute(task.work);
    }

    private void failPrefetch(ScreenId screen, InFlightTask task, Throwable error, boolean cancelled) {
        synchronized (this) {
            if (task.generation != cacheGeneration || !inFlightMap.remove(screen, task)) return;
            if (cancelled) cancelledPrefetches++;
            if (task.attempt != null) {
                if (cancelled) task.attempt.cancel();
                else task.attempt.fail();
            }
        }
        for (ScreenLoadCallback<Object> waiter : task.waiters) {
            if (cancelled) waiter.onCancelled(error);
            else waiter.onError(error);
        }
    }

    private boolean learnNavigation(NavigationEvent event) {
        int contextSize = Math.min(navigationHistory.size(), 2);
        List<ScreenId> context = navigationHistory.subList(navigationHistory.size() - contextSize, navigationHistory.size());
        if (contextSize > 0) {
            markovOrder1.learn(navigationHistory, event.toScreen);
            markovOrder2.learn(navigationHistory, event.toScreen);
        }
        learningLog.accept("navigationId=" + event.navigationId + " runId=" + event.runId
                + " sessionId=" + event.sessionId + " sequence=" + event.sequence + " source=" + event.source
                + " context=" + context + " fromScreen=" + event.fromScreen + " toScreen=" + event.toScreen
                + " learnedMaxOrder=" + contextSize);
        navigationHistory.add(event.toScreen);
        return contextSize > 0;
    }

    int getTransitionCount(List<ScreenId> context, ScreenId next) {
        return (context.size() == 1 ? markovOrder1 : markovOrder2).getTransitionCount(context, next);
    }

    public synchronized NavigationProfile snapshotProfile(long profileVersion) {
        Map<Integer, Map<List<ScreenId>, Map<ScreenId, Integer>>> second = markovOrder2.snapshotCounts();
        return new NavigationProfile(profileVersion, markovOrder2.getMinSupport(),
                markovOrder1.snapshotCounts().get(1), second.get(1), second.get(2));
    }

    public boolean supportsProfile(NavigationProfile profile) {
        return profile.minSupport == markovOrder1.getMinSupport() && profile.minSupport == markovOrder2.getMinSupport();
    }

    public synchronized void restoreProfile(NavigationProfile profile) {
        if (!supportsProfile(profile)) throw new IllegalArgumentException("Incompatible Markov minSupport");
        Map<Integer, Map<List<ScreenId>, Map<ScreenId, Integer>>> second = new HashMap<>();
        second.put(1, profile.order2Backoff);
        second.put(2, profile.order2);
        markovOrder1.replaceCounts(Collections.singletonMap(1, profile.order1));
        markovOrder2.replaceCounts(second);
        navigationHistory.clear();
        lastPredictions = Collections.emptyList();
    }

    private synchronized List<Map.Entry<ScreenId, Double>> predictNext(ScreenId currentScreen) {
        if (navigationHistory.isEmpty()) {
            return markovOrder1.predict(Collections.singletonList(currentScreen));
        }
        if (currentPolicy == Policy.P4_MARKOV_ORDER2 || currentPolicy == Policy.P5_ADAPTIVE_NETWORK) {
            // MarkovPredictor bậc 2 tự động hạ bậc (backoff) về bậc 1 nếu ngữ cảnh 2 bước chưa đủ minSupport = 3
            return markovOrder2.predict(navigationHistory);
        }
        return markovOrder1.predict(navigationHistory);
    }

    private void seedInitialTransitions() {
        // Dựng sẵn dữ liệu ban đầu (warm-start) cho kịch bản đọc tin phổ biến:
        // Trang chủ -> thường bấm xem chi tiết bài (60%) hoặc tìm kiếm (20%) hoặc sang tab khác (20%)
        addTransition(ScreenId.FEED_NEWEST, ScreenId.POST_DETAIL, 6);
        addTransition(ScreenId.FEED_NEWEST, ScreenId.SEARCH, 2);
        addTransition(ScreenId.FEED_NEWEST, ScreenId.FEED_HOT, 2);

        addTransition(ScreenId.FEED_HOT, ScreenId.POST_DETAIL, 7);
        addTransition(ScreenId.FEED_HOT, ScreenId.FEED_NEWEST, 3);

        addTransition(ScreenId.POST_DETAIL, ScreenId.COMMENTS, 5);
        addTransition(ScreenId.POST_DETAIL, ScreenId.FACT_CHECK, 3);
        addTransition(ScreenId.POST_DETAIL, ScreenId.FEED_NEWEST, 2);

        addTransition(ScreenId.COMMENTS, ScreenId.POST_DETAIL, 5);
        addTransition(ScreenId.COMMENTS, ScreenId.FEED_NEWEST, 5);

        // Ngữ cảnh bậc 2 đặc trưng
        addOrder2Transition(ScreenId.FEED_NEWEST, ScreenId.POST_DETAIL, ScreenId.FACT_CHECK, 4);
        addOrder2Transition(ScreenId.FEED_NEWEST, ScreenId.POST_DETAIL, ScreenId.COMMENTS, 3);
        addOrder2Transition(ScreenId.FEED_FOLLOWING, ScreenId.PROFILE, ScreenId.POST_DETAIL, 4);
        addOrder2Transition(ScreenId.POST_DETAIL, ScreenId.FACT_CHECK, ScreenId.FEED_NEWEST, 4);
    }

    private void addTransition(ScreenId from, ScreenId to, int weight) {
        List<ScreenId> ctx = Collections.singletonList(from);
        for (int i = 0; i < weight; i++) {
            markovOrder1.learn(ctx, to);
            markovOrder2.learn(ctx, to);
        }
    }

    private void addOrder2Transition(ScreenId prev, ScreenId curr, ScreenId next, int weight) {
        List<ScreenId> ctx2 = Arrays.asList(prev, curr);
        for (int i = 0; i < weight; i++) {
            markovOrder2.learn(ctx2, next);
        }
    }

    /**
     * Bắt đầu một phiên điều hướng mới (xóa lịch sử phiên cũ nhưng giữ lại bảng Markov đã học).
     */
    public synchronized void startNewSession() {
        navigationHistory.clear();
        closeCacheGeneration();
    }

    /**
     * Đặt lại toàn bộ phiên và bộ dự đoán Markov về trạng thái ban đầu (dùng khi đổi cấu hình thí nghiệm).
     */
    public synchronized void resetSession() {
        startNewSession();
        lastPredictions = Collections.emptyList();
        markovOrder1.reset();
        markovOrder2.reset();
        seedInitialTransitions();
    }
}

