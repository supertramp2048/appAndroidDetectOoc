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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    public interface InFlightCallback {
        void onComplete(long actualLoadTimeMs, @Nullable Object data);
    }

    public static class InFlightTask {
        public final ScreenId screenId;
        public final String requestId;
        public final long startTimeMs;
        public final long expectedDurationMs;
        private final List<InFlightCallback> waiters = new ArrayList<>();
        private boolean isDone = false;
        private Object resultData = null;

        public InFlightTask(ScreenId screenId, long expectedDurationMs) {
            this(screenId, "in-flight-" + UUID.randomUUID().toString().substring(0, 8), expectedDurationMs);
        }

        public InFlightTask(ScreenId screenId, String requestId, long expectedDurationMs) {
            this.screenId = screenId;
            this.requestId = requestId;
            this.startTimeMs = SystemClock.elapsedRealtime();
            this.expectedDurationMs = expectedDurationMs;
        }

        public synchronized void addWaiter(InFlightCallback callback) {
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
            for (InFlightCallback waiter : waiters) {
                waiter.onComplete(actual, resultData);
            }
            waiters.clear();
        }

        public void markDone() {
            markDone(null);
        }

        @Nullable
        public synchronized Object getResultData() {
            return resultData;
        }

        public boolean isDone() {
            return isDone;
        }
    }

    public interface OnPredictionUpdatedListener {
        void onPredictionUpdated(ScreenId currentScreen, List<Map.Entry<ScreenId, Double>> predictions, Policy policy);
    }

    private final PrefetchCache cache = PrefetchManager.getInstance().getCache();
    private final MockNetworkEngine mockNetworkEngine = MockNetworkEngine.getInstance();
    private final RadioNetworkMonitor radioNetworkMonitor = RadioNetworkMonitor.getInstance();
    private final ExecutorService prefetchExecutor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, InFlightTask> inFlightMap = new ConcurrentHashMap<>();

    // [Ý 1 - Glide Preload]: Tập hợp các URL ảnh đã được preload trong phiên để tránh gọi Glide trùng lặp
    private final Set<String> preloadedImageUrls = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Lịch sử điều hướng người dùng và bộ dự đoán Markov chuẩn của B (MarkovPredictor)
    private final List<ScreenId> navigationHistory = new ArrayList<>();
    private final MarkovPredictor<ScreenId> markovOrder1 = new MarkovPredictor<>(1, 3);
    private final MarkovPredictor<ScreenId> markovOrder2 = new MarkovPredictor<>(2, 3);

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

    public int getInFlightCount() {
        int count = 0;
        for (InFlightTask task : inFlightMap.values()) {
            if (task != null && !task.isDone()) {
                count++;
            }
        }
        return count;
    }

    private void notifyOverlayStateChanged() {
        if (predictionListener != null) {
            mainHandler.post(() -> {
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

    public void setSimulationMode(boolean simulationMode) {
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
        long bytes = (long) (screenId.getBaseSizeBytes() * payloadScale);
        double transferSec = (double) bytes / currentNetwork.bandwidthBps;
        long totalMs = currentNetwork.rttMs + (long) (transferSec * 1000);
        return Math.max(10, (long) (totalMs / timeScale));
    }

    /**
     * Kiểm tra xem màn hình này đang có task tải trước chạy ngầm dở dang hay không.
     */
    @Nullable
    public InFlightTask getInFlightTask(ScreenId screenId) {
        return getInFlightTask(screenId, null);
    }

    @Nullable
    public InFlightTask getInFlightTask(@NonNull ScreenId screenId, @Nullable Bundle params) {
        String contextualKey = PrefetchCache.buildContextualKey(screenId, params);
        InFlightTask task = inFlightMap.get(contextualKey);
        if (task != null && !task.isDone()) {
            return task;
        }
        InFlightTask genericTask = inFlightMap.get(screenId.getId());
        if (genericTask != null && !genericTask.isDone()) {
            return genericTask;
        }
        return null;
    }

    /**
     * Ghi nhận màn hình người dùng vừa bước vào:
     * 1. Cập nhật ma trận Markov.
     * 2. Dự đoán xác suất các màn hình kế tiếp.
     * 3. Quyết định chính sách và phát request tải trước vào cache.
     */
    public void onScreenDisplayed(@NonNull ScreenId currentScreen) {
        lastCurrentScreen = currentScreen;

        // Cập nhật lịch sử
        updateMarkovTransitions(currentScreen);

        // Lấy danh sách dự đoán
        List<Map.Entry<ScreenId, Double>> predictions = predictNext(currentScreen);
        lastPredictions = predictions;

        // Báo cho UI Overlay
        if (predictionListener != null) {
            mainHandler.post(() -> {
                if (predictionListener != null) {
                    predictionListener.onPredictionUpdated(currentScreen, predictions, currentPolicy);
                }
            });
        }

        // Nếu chính sách là P0 thì không tải trước
        if (currentPolicy == Policy.P0_NONE) {
            return;
        }

        // Chọn danh sách màn hình cần tải trước theo chính sách
        List<ScreenId> targetsToPrefetch = selectPrefetchTargets(predictions);

        // Kích hoạt tải trước trong nền (hỗ trợ cả mạng giả lập qua MockNetworkEngine lẫn API thật)
        for (ScreenId target : targetsToPrefetch) {
            triggerBackgroundPrefetch(target);
        }
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

            case P5_ADAPTIVE_NETWORK:
                // [Ý 1 - Radio-Aware Pareto Utility]:
                // Kết hợp công thức Jiang & Kleinrock / Higgins IMP với cảm biến sóng thật (RadioNetworkMonitor):
                //   Benefit = p * (RTT + TransferTime)
                //   Cost    = lambda * (1 - p) * TransferTime * CostFactor(Network)
                // Khi chạy mạng thật (!simulationMode), tự động đọc NetworkCapabilities (Wi-Fi vs 4G/3G)
                // để lấy CostFactor và băng thông thực tế.
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
                break;

            case P0_NONE:
            default:
                break;
        }

        return selected;
    }

    /**
     * [Ý 1 - Viewport Dwell Signal + Glide Media Preload]:
     * Được gọi từ FeedFragment khi người dùng dừng cuộn (SCROLL_STATE_IDLE) và dừng mắt
     * trên một thẻ bài viết ở giữa màn hình quá ngưỡng VIEWPORT_DWELL_THRESHOLD_MS (450ms).
     *
     * Thực hiện:
     * 1. Cập nhật mục tiêu bài viết/tác giả dự kiến (post.id, post.author.id).
     * 2. Preload ngay ảnh bìa (thumbnailUrl) và ảnh đại diện (avatarUrl) của bài viết qua Glide.
     * 3. Nếu chính sách hiện tại (P1..P5) cho phép tải trước POST_DETAIL, kích hoạt tải trước
     *    ngay dữ liệu chi tiết của đúng bài viết đó vào PrefetchCache theo khóa "POST_DETAIL:postId=<id>".
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
        List<ScreenId> allowedTargets = selectPrefetchTargets(predictions);
        if (allowedTargets.contains(ScreenId.POST_DETAIL)) {
            Log.d(TAG, "[ViewportDwell] User paused " + dwellDurationMs + "ms on post #" + post.id + " -> Speculative prefetch check");
            this.lastPrefetchActionStatus = "👁 Dừng mắt Post #" + post.id + " (" + dwellDurationMs + "ms) -> Tải trước API + Ảnh";
            notifyOverlayStateChanged();
            triggerBackgroundPrefetch(ScreenId.POST_DETAIL);
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

        mainHandler.post(() -> {
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

    private void triggerBackgroundPrefetch(ScreenId screenId) {
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
        InFlightTask existing = inFlightMap.get(contextualKey);
        if (existing != null && !existing.isDone()) {
            return;
        }

        long sizeBytes = (long) (screenId.getBaseSizeBytes() * payloadScale);

        // [Ý 1 - Radio-Aware Budget Check]:
        // Kiểm tra hạn mức ngân sách 4G / trạng thái mạng thật trước khi phát request nền
        Context appContext = MyApplication.getInstance();
        if (!radioNetworkMonitor.tryConsumePrefetchBudget(appContext, sizeBytes, simulationMode)) {
            Log.d(TAG, "[RadioBudget] Skipped prefetch for " + screenId.getId() + " (Data Saver or 4G budget limit reached)");
            this.lastPrefetchActionStatus = "⛔ Chặn tải trước " + contextualKey + " (Vượt ngân sách 4G / Data Saver)";
            notifyOverlayStateChanged();
            return;
        }

        long expectedDelayMs = calculateSimulatedNetworkDelayMs(screenId);
        String reqId = "prefetch-" + screenId.getId() + "-" + UUID.randomUUID().toString().substring(0, 6);
        InFlightTask task = new InFlightTask(screenId, reqId, expectedDelayMs);
        inFlightMap.put(contextualKey, task);
        inFlightMap.put(screenId.getId(), task);
        this.lastPrefetchActionStatus = "⏳ Đang tải ngầm: " + contextualKey + " (" + (sizeBytes / 1024) + " KB)...";
        notifyOverlayStateChanged();

        if (simulationMode) {
            // Chạy qua MockNetworkEngine để chia sẻ băng thông thực sự với các request khác
            mockNetworkEngine.executeWithDataSupplier(
                    reqId,
                    screenId.getId(),
                    false, // background prefetch
                    () -> FakeDataSource.getSampleDataForScreen(screenId, contextParams)
            ).whenComplete((simResp, ex) -> {
                Object data = (simResp != null && simResp.getData() != null)
                        ? simResp.getData()
                        : FakeDataSource.getSampleDataForScreen(screenId, contextParams);
                if (ex == null && data != null) {
                    cache.putContextual(screenId, contextParams, data, sizeBytes);
                }
                task.markDone(data);
                inFlightMap.remove(contextualKey);
                inFlightMap.remove(screenId.getId(), task);
                this.lastPrefetchActionStatus = "⚡ Đã tải xong vào Cache: " + contextualKey + " [FRESH]";
                notifyOverlayStateChanged();
                Log.d(TAG, "Simulated prefetch complete via MockNetworkEngine: " + contextualKey);
            });
        } else {
            // Chạy gọi API mạng thật ở background + Preload ảnh Glide cho kết quả trả về
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
                inFlightMap.remove(contextualKey);
                inFlightMap.remove(screenId.getId(), task);
                notifyOverlayStateChanged();
                Log.d(TAG, "Real API prefetch complete for: " + contextualKey + " (success=" + (realData != null) + ")");
            });
        }
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

    private synchronized void updateMarkovTransitions(ScreenId currentScreen) {
        if (!navigationHistory.isEmpty()) {
            markovOrder1.learn(navigationHistory, currentScreen);
            markovOrder2.learn(navigationHistory, currentScreen);
        }
        navigationHistory.add(currentScreen);
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
        cache.clear();
        inFlightMap.clear();
        preloadedImageUrls.clear();
        radioNetworkMonitor.resetSessionBudget();
        mockNetworkEngine.cancelAll();
    }

    /**
     * Đặt lại toàn bộ phiên và bộ dự đoán Markov về trạng thái ban đầu (dùng khi đổi cấu hình thí nghiệm).
     */
    public synchronized void resetSession() {
        navigationHistory.clear();
        cache.clear();
        inFlightMap.clear();
        preloadedImageUrls.clear();
        radioNetworkMonitor.resetSessionBudget();
        mockNetworkEngine.cancelAll();
        markovOrder1.reset();
        markovOrder2.reset();
        seedInitialTransitions();
    }
}

