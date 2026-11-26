package com.socialapp.core.prefetch;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.socialapp.MyApplication;
import com.socialapp.core.di.AppContainer;
import com.socialapp.core.network.SocialAppApiService;
import com.socialapp.core.network.dto.CommentListResponse;
import com.socialapp.core.network.dto.PostDetailResponse;
import com.socialapp.core.network.dto.PostListResponse;
import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.network.dto.ProfileDetailResponse;
import com.socialapp.core.network.sim.MockNetworkEngine;
import com.socialapp.core.network.sim.PayloadWeight;
import com.socialapp.core.network.sim.ScreenPayloadConfig;
import com.socialapp.core.prefetch.policy.markov.MarkovPredictor;
import com.socialapp.core.prefetch.profile.NavigationProfile;
import com.socialapp.core.telemetry.NavigationEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import retrofit2.Response;

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

    public interface RealInFlightCallback {
        void onComplete(long actualLoadTimeMs, @Nullable Object data);
    }

    public static class RealInFlightTask {
        public final ScreenId screenId;
        public final String requestId;
        public final long startTimeMs;
        public final long expectedDurationMs;
        private final List<RealInFlightCallback> waiters = new ArrayList<>();
        private boolean isDone = false;
        private Object resultData = null;

        public RealInFlightTask(ScreenId screenId, String requestId, long expectedDurationMs) {
            this.screenId = screenId;
            this.requestId = requestId;
            this.startTimeMs = SystemClock.elapsedRealtime();
            this.expectedDurationMs = expectedDurationMs;
        }

        public synchronized void addWaiter(RealInFlightCallback callback) {
            if (isDone) {
                callback.onComplete(SystemClock.elapsedRealtime() - startTimeMs, resultData);
            } else {
                waiters.add(callback);
            }
        }

        public synchronized void markDone(@Nullable Object data) {
            isDone = true;
            resultData = data;
            long actual = SystemClock.elapsedRealtime() - startTimeMs;
            for (RealInFlightCallback waiter : waiters) {
                waiter.onComplete(actual, resultData);
            }
            waiters.clear();
        }

        public boolean isDone() {
            return isDone;
        }
    }

    public interface OnPredictionUpdatedListener {
        void onPredictionUpdated(ScreenId currentScreen, List<Map.Entry<ScreenId, Double>> predictions, Policy policy);
    }

    private final PrefetchCache cache;
    private final MockNetworkEngine mockNetworkEngine = MockNetworkEngine.getInstance();
    private final RadioNetworkMonitor radioNetworkMonitor = RadioNetworkMonitor.getInstance();
    private final Executor prefetchExecutor;
    private final Consumer<Runnable> postToMain;
    private final Consumer<String> learningLog;
    private final Map<ScreenId, InFlightTask> inFlightMap = new EnumMap<>(ScreenId.class);
    private final Map<String, RealInFlightTask> realInFlightMap = new ConcurrentHashMap<>();
    private final Set<String> preloadedImageUrls = Collections.newSetFromMap(new ConcurrentHashMap<>());
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

    @Nullable
    private final PrefetchDemoPreferences demoPreferences;

    private Policy currentPolicy = PrefetchDemoPreferences.DEFAULT_POLICY;
    private NetworkProfile currentNetwork = PrefetchDemoPreferences.DEFAULT_NETWORK;
    private float payloadScale = 1.0f;
    private float timeScale = PrefetchDemoPreferences.DEFAULT_TIME_SCALE; // Hệ số tua nhanh (1.0 = thời gian thực)
    private boolean simulationMode = PrefetchDemoPreferences.DEFAULT_SIMULATION_MODE;

    private volatile int lastActivePostId = 1;
    private volatile int lastActiveUserId = 1;
    private volatile int lastDwelledPostId = -1;
    private volatile String lastPrefetchActionStatus = "Chờ tín hiệu điều hướng / dừng mắt...";

    private ScreenId lastCurrentScreen = ScreenId.FEED_NEWEST;
    private List<Map.Entry<ScreenId, Double>> lastPredictions = Collections.emptyList();

    private OnPredictionUpdatedListener predictionListener;

    public PrefetchEngine() {
        this((PrefetchDemoPreferences) null);
    }

    public PrefetchEngine(@Nullable Context context) {
        this(context != null ? new PrefetchDemoPreferences(context) : null);
    }

    public PrefetchEngine(@Nullable PrefetchDemoPreferences demoPreferences) {
        this(new PrefetchCache(), new Handler(Looper.getMainLooper())::post,
                message -> Log.d(LEARNING_TAG, message), Executors.newFixedThreadPool(2), demoPreferences);
    }

    PrefetchEngine(PrefetchCache cache, Consumer<Runnable> postToMain, Consumer<String> learningLog) {
        this(cache, postToMain, learningLog, Executors.newFixedThreadPool(2), null);
    }

    PrefetchEngine(PrefetchCache cache, Consumer<Runnable> postToMain, Consumer<String> learningLog, Executor prefetchExecutor) {
        this(cache, postToMain, learningLog, prefetchExecutor, null);
    }

    private PrefetchEngine(PrefetchCache cache, Consumer<Runnable> postToMain, Consumer<String> learningLog,
                           Executor prefetchExecutor, @Nullable PrefetchDemoPreferences demoPreferences) {
        this.cache = cache;
        this.postToMain = postToMain;
        this.learningLog = learningLog;
        this.prefetchExecutor = prefetchExecutor;
        this.demoPreferences = demoPreferences;
        if (demoPreferences != null) {
            this.currentPolicy = demoPreferences.getPolicy();
            this.currentNetwork = demoPreferences.getNetwork();
            this.timeScale = demoPreferences.getTimeScale();
            this.simulationMode = demoPreferences.isSimulationMode();
        }
        registerScreenPayloadSizes();
        syncToSharedEngines();
        seedInitialTransitions();
    }

    private void registerScreenPayloadSizes() {
        for (ScreenId screenId : ScreenId.values()) {
            ScreenPayloadConfig.registerSize(screenId.getId(), screenId.getBaseSizeBytes() / 1024.0);
        }
    }

    private void syncToSharedEngines() {
        mockNetworkEngine.setSimulationEnabled(simulationMode);
        mockNetworkEngine.setTimeScale(timeScale);
        mockNetworkEngine.setProfile(mapToSimNetworkProfile(currentNetwork));
        mockNetworkEngine.setWeight(mapToSimPayloadWeight(payloadScale));
        PrefetchRuntimeConfig.getInstance().setPolicyByCode(currentPolicy.code);
    }

    private static com.socialapp.core.network.sim.NetworkProfile mapToSimNetworkProfile(NetworkProfile profile) {
        if (profile == NetworkProfile.WIFI) return com.socialapp.core.network.sim.NetworkProfile.WIFI;
        if (profile == NetworkProfile.G3) return com.socialapp.core.network.sim.NetworkProfile.NETWORK_3G;
        if (profile == NetworkProfile.EDGE) return com.socialapp.core.network.sim.NetworkProfile.EDGE_2G;
        return com.socialapp.core.network.sim.NetworkProfile.LTE_4G;
    }

    private static PayloadWeight mapToSimPayloadWeight(float scale) {
        if (scale <= 0.6f) return PayloadWeight.LIGHT_0_5;
        if (scale >= 1.5f) return PayloadWeight.HEAVY_2_0;
        return PayloadWeight.NORMAL_1_0;
    }

    public int getLastActivePostId() {
        return lastActivePostId;
    }

    public void setLastActivePostId(int postId) {
        if (postId > 0) {
            this.lastActivePostId = postId;
        }
    }

    public int getLastActiveUserId() {
        return lastActiveUserId;
    }

    public void setLastActiveUserId(int userId) {
        if (userId > 0) {
            this.lastActiveUserId = userId;
        }
    }

    public MockNetworkEngine getMockNetworkEngine() {
        return mockNetworkEngine;
    }

    public RadioNetworkMonitor getRadioNetworkMonitor() {
        return radioNetworkMonitor;
    }

    public int getLastDwelledPostId() {
        return lastDwelledPostId;
    }

    @NonNull
    public String getLastPrefetchActionStatus() {
        return lastPrefetchActionStatus;
    }

    public void notifySwrRevalidation(@NonNull String contextualKey, boolean completed) {
        if (completed) {
            this.lastPrefetchActionStatus = "✅ SWR Làm mới xong: " + contextualKey + " [FRESH]";
        } else {
            this.lastPrefetchActionStatus = "🔄 SWR Trả ngay 0ms + Đang làm mới ngầm: " + contextualKey;
        }
        notifyOverlayStateChanged();
    }

    public int getPreloadedImageCount() {
        return preloadedImageUrls.size();
    }

    public synchronized int getInFlightCount() {
        int count = inFlightMap.size();
        for (RealInFlightTask task : realInFlightMap.values()) {
            if (task != null && !task.isDone()) {
                count++;
            }
        }
        return count;
    }

    private void notifyOverlayStateChanged() {
        if (predictionListener != null) {
            postToMain.accept(() -> {
                if (predictionListener != null) {
                    predictionListener.onPredictionUpdated(lastCurrentScreen, lastPredictions, currentPolicy);
                }
            });
        }
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

    public void setCurrentPolicy(@NonNull Policy currentPolicy) {
        setCurrentPolicy(currentPolicy, true);
    }

    public void setCurrentPolicy(@NonNull Policy currentPolicy, boolean persist) {
        if (currentPolicy == null) return;
        this.currentPolicy = currentPolicy;
        if (persist && demoPreferences != null) {
            demoPreferences.setPolicy(currentPolicy);
        }
        PrefetchRuntimeConfig.getInstance().setPolicyByCode(currentPolicy.code);
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

    public void setCurrentNetwork(@NonNull NetworkProfile currentNetwork) {
        setCurrentNetwork(currentNetwork, true);
    }

    public void setCurrentNetwork(@NonNull NetworkProfile currentNetwork, boolean persist) {
        if (currentNetwork == null) return;
        this.currentNetwork = currentNetwork;
        if (persist && demoPreferences != null) {
            demoPreferences.setNetwork(currentNetwork);
        }
        this.mockNetworkEngine.setProfile(mapToSimNetworkProfile(currentNetwork));
    }

    public float getPayloadScale() {
        return payloadScale;
    }

    public void setPayloadScale(float payloadScale) {
        this.payloadScale = payloadScale;
        this.mockNetworkEngine.setWeight(mapToSimPayloadWeight(payloadScale));
    }

    public float getTimeScale() {
        return timeScale;
    }

    public void setTimeScale(float timeScale) {
        float sanitized = Float.isNaN(timeScale) || Float.isInfinite(timeScale)
                ? PrefetchDemoPreferences.DEFAULT_TIME_SCALE
                : Math.max(PrefetchDemoPreferences.MIN_TIME_SCALE, timeScale);
        this.timeScale = sanitized;
        if (demoPreferences != null) {
            demoPreferences.setTimeScale(sanitized);
        }
        this.mockNetworkEngine.setTimeScale(this.timeScale);
    }

    public boolean isSimulationMode() {
        return simulationMode;
    }

    public synchronized void setSimulationMode(boolean simulationMode) {
        if (this.simulationMode != simulationMode) closeCacheGeneration();
        this.simulationMode = simulationMode;
        if (demoPreferences != null) {
            demoPreferences.setSimulationMode(simulationMode);
        }
        this.mockNetworkEngine.setSimulationEnabled(simulationMode);
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
     * Kiểm tra xem màn hình này đang có task tải trước mạng thật đang bay dở dang hay không.
     */
    @Nullable
    public RealInFlightTask getRealInFlightTask(@NonNull ScreenId screenId, @Nullable Bundle params) {
        String contextualKey = PrefetchCache.buildContextualKey(screenId, params);
        RealInFlightTask task = realInFlightMap.get(contextualKey);
        if (task != null && !task.isDone()) {
            return task;
        }
        RealInFlightTask genericTask = realInFlightMap.get(screenId.getId());
        if (genericTask != null && !genericTask.isDone()) {
            return genericTask;
        }
        return null;
    }

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

        // Nếu chính sách là P0 thì không tải trước
        if (currentPolicy == Policy.P0_NONE) {
            return learned;
        }

        if (simulationMode) {
            if (cacheSessionId != null && !cacheSessionId.equals(event.sessionId)) {
                return learned;
            }
            if (currentPolicy == Policy.P5_ADAPTIVE_NETWORK) {
                scheduleP5(event, predictions);
                return learned;
            }
            List<ScreenId> targetsToPrefetch = selectPrefetchTargets(predictions);
            for (ScreenId target : targetsToPrefetch) {
                triggerBackgroundPrefetch(target);
            }
            return learned;
        }

        // Chế độ mạng thật: chọn màn hình tải trước theo RadioNetworkMonitor & tải API thật
        List<ScreenId> targetsToPrefetch = selectRealPrefetchTargets(predictions);
        for (ScreenId target : targetsToPrefetch) {
            triggerRealBackgroundPrefetch(target);
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

    private List<ScreenId> selectRealPrefetchTargets(List<Map.Entry<ScreenId, Double>> predictions) {
        if (currentPolicy != Policy.P5_ADAPTIVE_NETWORK) {
            return selectPrefetchTargets(predictions);
        }

        // [Ý 1 - Radio-Aware Pareto Utility]:
        // Kết hợp công thức Jiang & Kleinrock / Higgins IMP với cảm biến sóng thật (RadioNetworkMonitor):
        //   Benefit = p * (RTT + TransferTime)
        //   Cost    = lambda * (1 - p) * TransferTime * CostFactor(Network)
        List<ScreenId> selected = new ArrayList<>();
        Context appCtx = MyApplication.getInstance();
        NetworkProfile effectiveProfile = simulationMode
                ? currentNetwork
                : radioNetworkMonitor.detectLiveNetworkProfile(appCtx);
        double costFactor = radioNetworkMonitor.getCostFactorForProfile(effectiveProfile);
        double riskLambda = 0.85;

        for (Map.Entry<ScreenId, Double> entry : predictions) {
            double p = entry.getValue();
            long bytes = (long) (entry.getKey().getBaseSizeBytes() * payloadScale);
            double transferMs = ((double) bytes / effectiveProfile.bandwidthBps) * 1000.0;
            double totalSavedMs = effectiveProfile.rttMs + transferMs;

            double expectedBenefit = p * totalSavedMs;
            double expectedCost = riskLambda * (1.0 - p) * transferMs * costFactor;
            if (expectedBenefit > expectedCost) {
                selected.add(entry.getKey());
            }
        }
        return selected;
    }

    /**
     * [Ý 1 - Viewport Dwell Signal + Glide Media Preload]:
     * Được gọi từ FeedFragment khi người dùng dừng cuộn (SCROLL_STATE_IDLE) và dừng mắt
     * trên một thẻ bài viết ở giữa màn hình quá ngưỡng VIEWPORT_DWELL_THRESHOLD_MS (450ms).
     */
    public void onPostViewportDwell(@NonNull PostResource post, long dwellDurationMs) {
        if (post.id <= 0) return;

        this.lastDwelledPostId = post.id;
        setLastActivePostId(post.id);
        if (post.author != null && post.author.id > 0) {
            setLastActiveUserId(post.author.id);
        }

        if (currentPolicy == Policy.P0_NONE) {
            this.lastPrefetchActionStatus = "Dừng mắt Post #" + post.id + " (Đang tắt vì Policy = P0)";
            notifyOverlayStateChanged();
            return;
        }

        // 1. Preload ảnh thumbnail và avatar của bài viết đang dừng mắt vào Disk/Memory cache của Glide
        preloadPostMediaWithGlide(post);

        // 2. Kiểm tra xem chính sách Markov/Pareto hiện tại có chọn tải trước POST_DETAIL không
        List<Map.Entry<ScreenId, Double>> predictions = lastPredictions.isEmpty()
                ? predictNext(lastCurrentScreen)
                : lastPredictions;
        List<ScreenId> allowedTargets = selectRealPrefetchTargets(predictions);
        if (!simulationMode && allowedTargets.contains(ScreenId.POST_DETAIL)) {
            Log.d(TAG, "[ViewportDwell] User paused " + dwellDurationMs + "ms on post #" + post.id + " -> Speculative prefetch check");
            this.lastPrefetchActionStatus = "👁 Dừng mắt Post #" + post.id + " (" + dwellDurationMs + "ms) -> Tải trước API + Ảnh";
            notifyOverlayStateChanged();
            triggerRealBackgroundPrefetch(ScreenId.POST_DETAIL);
        } else if (allowedTargets.contains(ScreenId.POST_DETAIL)) {
            this.lastPrefetchActionStatus = "👁 Dừng mắt Post #" + post.id + " (" + dwellDurationMs + "ms) -> Đã preload ảnh (Sim mode)";
            notifyOverlayStateChanged();
        } else {
            this.lastPrefetchActionStatus = "👁 Dừng mắt Post #" + post.id + " -> Chỉ preload ảnh (Pareto lọc API)";
            notifyOverlayStateChanged();
        }
    }

    /**
     * [Ý 1 - Glide Preload]: Tải trước ảnh bìa (thumbnail) và avatar tác giả của một bài viết
     * xuống bộ nhớ đệm của Glide để khi mở PostDetailActivity ảnh hiển thị tức thì (0ms).
     */
    public void preloadPostMediaWithGlide(@Nullable PostResource post) {
        if (post == null) return;
        Context appContext = MyApplication.getInstance();
        if (appContext == null) return;
        if (!radioNetworkMonitor.shouldAllowImagePreload(appContext, simulationMode)) {
            return;
        }

        String thumbUrl = post.thumbnailUrl != null ? post.thumbnailUrl.trim() : "";
        String avatarUrl = (post.author != null && post.author.getAvatarUrl() != null)
                ? post.author.getAvatarUrl().trim()
                : "";

        postToMain.accept(() -> {
            try {
                if (!thumbUrl.isEmpty() && preloadedImageUrls.add(thumbUrl)) {
                    Glide.with(appContext)
                            .load(thumbUrl)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .preload();
                }
                if (!avatarUrl.isEmpty() && preloadedImageUrls.add(avatarUrl)) {
                    Glide.with(appContext)
                            .load(avatarUrl)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .preload();
                }
                notifyOverlayStateChanged();
            } catch (Exception e) {
                Log.w(TAG, "Glide preload skipped: " + e.getMessage());
            }
        });
    }

    /**
     * [Ý 1 - Glide Preload]: Tự động quét dữ liệu vừa prefetch (1 bài viết hoặc danh sách bài viết đầu trang)
     * để preload ảnh của các bài đầu tiên.
     */
    private void preloadMediaForPrefetchedData(@Nullable Object data) {
        if (data instanceof PostResource) {
            preloadPostMediaWithGlide((PostResource) data);
        } else if (data instanceof List<?>) {
            List<?> list = (List<?>) data;
            int maxPreloadPosts = Math.min(2, list.size());
            for (int i = 0; i < maxPreloadPosts; i++) {
                Object item = list.get(i);
                if (item instanceof PostResource) {
                    preloadPostMediaWithGlide((PostResource) item);
                }
            }
        }
    }

    @NonNull
    private Bundle buildActiveContextParams(@NonNull ScreenId screenId) {
        Bundle params = new Bundle();
        if (screenId == ScreenId.POST_DETAIL || screenId == ScreenId.COMMENTS) {
            params.putInt("postId", lastActivePostId);
        } else if (screenId == ScreenId.PROFILE) {
            params.putInt("userId", lastActiveUserId);
        }
        return params;
    }

    private void triggerRealBackgroundPrefetch(ScreenId screenId) {
        Bundle contextParams = buildActiveContextParams(screenId);
        String contextualKey = PrefetchCache.buildContextualKey(screenId, contextParams);

        // Nếu đã có bản ghi hợp lệ cho đúng ngữ cảnh (ID bài viết/user) trong Cache thì không tải lại
        if (cache.containsValid(contextualKey)) {
            PrefetchEntry<?> cachedEntry = cache.get(contextualKey);
            String stateLabel = (cachedEntry != null && cachedEntry.isStaleButUsable()) ? "STALE-SWR" : "FRESH";
            this.lastPrefetchActionStatus = "⚡ Sẵn sàng trong Cache: " + contextualKey + " [" + stateLabel + "]";
            notifyOverlayStateChanged();
            return;
        }

        // Nếu đang tải dở đúng khóa ngữ cảnh này thì không tạo thêm task trùng
        RealInFlightTask existing = realInFlightMap.get(contextualKey);
        if (existing != null && !existing.isDone()) {
            return;
        }

        long sizeBytes = scaledPayloadBytes(screenId);

        // [Ý 1 - Radio-Aware Budget Check]:
        // Kiểm tra hạn mức ngân sách 4G / trạng thái mạng thật trước khi phát request nền
        Context appContext = MyApplication.getInstance();
        if (!radioNetworkMonitor.tryConsumePrefetchBudget(appContext, sizeBytes, false)) {
            Log.d(TAG, "[RadioBudget] Skipped prefetch for " + screenId.getId() + " (Data Saver or 4G budget limit reached)");
            this.lastPrefetchActionStatus = "⛔ Chặn tải trước " + contextualKey + " (Vượt ngân sách 4G / Data Saver)";
            notifyOverlayStateChanged();
            return;
        }

        long expectedDelayMs = calculateSimulatedNetworkDelayMs(screenId);
        String reqId = "prefetch-" + screenId.getId() + "-" + UUID.randomUUID().toString().substring(0, 6);
        RealInFlightTask task = new RealInFlightTask(screenId, reqId, expectedDelayMs);
        realInFlightMap.put(contextualKey, task);
        realInFlightMap.put(screenId.getId(), task);
        this.lastPrefetchActionStatus = "⏳ Đang tải ngầm: " + contextualKey + " (" + (sizeBytes / 1024) + " KB)...";
        notifyOverlayStateChanged();

        prefetchExecutor.execute(() -> {
            Object realData = fetchRealDataForPrefetch(screenId);
            if (realData != null) {
                cache.putContextual(screenId, contextParams, realData, sizeBytes);
                updateActiveIdsFromLoadedData(screenId, realData);
                preloadMediaForPrefetchedData(realData);
                this.lastPrefetchActionStatus = "⚡ Đã tải API thật + Ảnh: " + contextualKey + " [FRESH]";
            } else {
                this.lastPrefetchActionStatus = "⚠ Tải trước thất bại: " + contextualKey;
            }
            task.markDone(realData);
            realInFlightMap.remove(contextualKey);
            realInFlightMap.remove(screenId.getId(), task);
            notifyOverlayStateChanged();
            Log.d(TAG, "Real API prefetch complete for: " + contextualKey + " (success=" + (realData != null) + ")");
        });
    }

    @Nullable
    private Object fetchRealDataForPrefetch(@NonNull ScreenId screenId) {
        MyApplication app = MyApplication.getInstance();
        AppContainer container = app != null ? app.getAppContainer() : null;
        if (container == null) {
            return null;
        }
        SocialAppApiService api = container.getSocialAppApiService();
        try {
            switch (screenId) {
                case FEED_NEWEST: {
                    Response<PostListResponse> resp = api.getPosts(1, 10, "newest", null, null, null, null).execute();
                    return (resp.isSuccessful() && resp.body() != null) ? resp.body().data : null;
                }
                case FEED_HOT: {
                    Response<PostListResponse> resp = api.getPosts(1, 10, "hot", null, null, null, null).execute();
                    return (resp.isSuccessful() && resp.body() != null) ? resp.body().data : null;
                }
                case FEED_FOLLOWING: {
                    Response<PostListResponse> resp = api.getPosts(1, 10, null, null, null, null, "following").execute();
                    return (resp.isSuccessful() && resp.body() != null) ? resp.body().data : null;
                }
                case SEARCH: {
                    Response<PostListResponse> resp = api.getPosts(1, 10, "newest", null, "", null, null).execute();
                    return (resp.isSuccessful() && resp.body() != null) ? resp.body().data : null;
                }
                case POST_DETAIL: {
                    Response<PostDetailResponse> resp = api.getPostDetail(lastActivePostId).execute();
                    return (resp.isSuccessful() && resp.body() != null) ? resp.body().data : null;
                }
                case COMMENTS: {
                    Response<CommentListResponse> resp = api.getComments(lastActivePostId, 1, 10).execute();
                    return (resp.isSuccessful() && resp.body() != null) ? resp.body().data : null;
                }
                case PROFILE: {
                    Response<ProfileDetailResponse> resp = api.getProfile(lastActiveUserId).execute();
                    return (resp.isSuccessful() && resp.body() != null) ? resp.body().getProfile() : null;
                }
                case FACT_CHECK:
                case SETTINGS:
                default:
                    return null;
            }
        } catch (Exception e) {
            Log.w(TAG, "Real prefetch failed for " + screenId.getId() + ": " + e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public void updateActiveIdsFromLoadedData(@NonNull ScreenId screen, @Nullable Object data) {
        if (data instanceof PostResource) {
            PostResource post = (PostResource) data;
            if (post.id > 0) setLastActivePostId(post.id);
            if (post.author != null && post.author.id > 0) setLastActiveUserId(post.author.id);
        } else if (data instanceof List<?>) {
            List<?> list = (List<?>) data;
            if (!list.isEmpty() && list.get(0) instanceof PostResource) {
                PostResource firstPost = (PostResource) list.get(0);
                if (firstPost.id > 0) setLastActivePostId(firstPost.id);
                if (firstPost.author != null && firstPost.author.id > 0) setLastActiveUserId(firstPost.author.id);
            }
        }
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
        realInFlightMap.clear();
        preloadedImageUrls.clear();
        radioNetworkMonitor.resetSessionBudget();
        mockNetworkEngine.cancelAll();
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

