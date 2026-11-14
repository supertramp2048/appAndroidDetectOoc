package com.socialapp.core.prefetch;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
        void onComplete(long actualLoadTimeMs);
    }

    public static class InFlightTask {
        public final ScreenId screenId;
        public final long startTimeMs;
        public final long expectedDurationMs;
        private final List<InFlightCallback> waiters = new ArrayList<>();
        private boolean isDone = false;

        public InFlightTask(ScreenId screenId, long expectedDurationMs) {
            this.screenId = screenId;
            this.startTimeMs = SystemClock.elapsedRealtime();
            this.expectedDurationMs = expectedDurationMs;
        }

        public synchronized void addWaiter(InFlightCallback callback) {
            if (isDone) {
                callback.onComplete(SystemClock.elapsedRealtime() - startTimeMs);
            } else {
                waiters.add(callback);
            }
        }

        public synchronized void markDone() {
            isDone = true;
            long actual = SystemClock.elapsedRealtime() - startTimeMs;
            for (InFlightCallback waiter : waiters) {
                waiter.onComplete(actual);
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

    private final PrefetchCache cache = new PrefetchCache();
    private final ExecutorService prefetchExecutor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<ScreenId, InFlightTask> inFlightMap = new ConcurrentHashMap<>();

    // Lịch sử điều hướng người dùng để huấn luyện Markov tại chỗ
    private final List<ScreenId> navigationHistory = new ArrayList<>();
    private final Map<ScreenId, Map<ScreenId, Integer>> order1Transitions = new ConcurrentHashMap<>();
    private final Map<String, Map<ScreenId, Integer>> order2Transitions = new ConcurrentHashMap<>();

    private Policy currentPolicy = Policy.P3_MARKOV_FIXED;
    private NetworkProfile currentNetwork = NetworkProfile.LTE;
    private float payloadScale = 1.0f;
    private float timeScale = 1.0f; // Hệ số tua nhanh (1.0 = thời gian thực)
    private boolean simulationMode = true;

    private ScreenId lastCurrentScreen = ScreenId.FEED_NEWEST;
    private List<Map.Entry<ScreenId, Double>> lastPredictions = Collections.emptyList();

    private OnPredictionUpdatedListener predictionListener;

    public PrefetchEngine() {
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

    public void setSimulationMode(boolean simulationMode) {
        this.simulationMode = simulationMode;
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
        InFlightTask task = inFlightMap.get(screenId);
        if (task != null && !task.isDone()) {
            return task;
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

        // Nếu chính sách là P0 hoặc đang TẮT mô phỏng thì không tải giả lập
        if (currentPolicy == Policy.P0_NONE || !simulationMode) {
            return;
        }

        // Chọn danh sách màn hình cần tải trước theo chính sách
        List<ScreenId> targetsToPrefetch = selectPrefetchTargets(predictions);

        // Kích hoạt tải trước trong nền
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
                // Công thức Jiang & Kleinrock: p * thời_gian_tải > lambda * byte
                // Ở mạng chậm (3G/EDGE, thời gian tải lớn), prefetch mang lại lợi ích giảm trễ cực lớn -> prefetch tích cực
                // Ở mạng nhanh (WiFi, thời gian tải rất bé), hạn chế tải thừa để tránh lãng phí byte
                double lambda = 0.000005; // Hệ số chi phí byte
                for (Map.Entry<ScreenId, Double> entry : predictions) {
                    double p = entry.getValue();
                    long loadTimeMs = calculateSimulatedNetworkDelayMs(entry.getKey());
                    long bytes = entry.getKey().getBaseSizeBytes();
                    double benefit = p * loadTimeMs;
                    double cost = lambda * bytes;
                    if (benefit > cost || p >= 0.40) {
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

    private void triggerBackgroundPrefetch(ScreenId screenId) {
        // Nếu đã có trong Cache rồi thì không tải lại
        if (cache.contains(screenId)) {
            return;
        }

        // Nếu đang tải dở thì không tạo thêm task
        if (inFlightMap.containsKey(screenId) && !inFlightMap.get(screenId).isDone()) {
            return;
        }

        long delayMs = calculateSimulatedNetworkDelayMs(screenId);
        InFlightTask task = new InFlightTask(screenId, delayMs);
        inFlightMap.put(screenId, task);

        prefetchExecutor.execute(() -> {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ignored) {
            }

            // Giả lập dữ liệu trả về và đưa vào Cache
            long sizeBytes = (long) (screenId.getBaseSizeBytes() * payloadScale);
            Object sampleData = FakeDataSource.getSampleDataForScreen(screenId);
            cache.put(screenId, sampleData, sizeBytes);

            task.markDone();
            inFlightMap.remove(screenId);
            Log.d(TAG, "Prefetched background complete for: " + screenId.getId() + " (" + delayMs + " ms)");
        });
    }

    private void updateMarkovTransitions(ScreenId currentScreen) {
        int historySize = navigationHistory.size();

        if (historySize >= 1) {
            ScreenId prev = navigationHistory.get(historySize - 1);
            order1Transitions.putIfAbsent(prev, new ConcurrentHashMap<>());
            Map<ScreenId, Integer> nextMap = order1Transitions.get(prev);
            nextMap.put(currentScreen, nextMap.getOrDefault(currentScreen, 0) + 1);
        }

        if (historySize >= 2) {
            ScreenId prev2 = navigationHistory.get(historySize - 2);
            ScreenId prev1 = navigationHistory.get(historySize - 1);
            String key = prev2.getId() + "->" + prev1.getId();
            order2Transitions.putIfAbsent(key, new ConcurrentHashMap<>());
            Map<ScreenId, Integer> nextMap = order2Transitions.get(key);
            nextMap.put(currentScreen, nextMap.getOrDefault(currentScreen, 0) + 1);
        }

        navigationHistory.add(currentScreen);
    }

    private List<Map.Entry<ScreenId, Double>> predictNext(ScreenId currentScreen) {
        Map<ScreenId, Double> probabilities = new HashMap<>();

        if (currentPolicy == Policy.P4_MARKOV_ORDER2 && navigationHistory.size() >= 2) {
            ScreenId prev = navigationHistory.get(navigationHistory.size() - 2);
            String key = prev.getId() + "->" + currentScreen.getId();
            Map<ScreenId, Integer> nextMap = order2Transitions.get(key);
            if (nextMap != null && !nextMap.isEmpty()) {
                int total = 0;
                for (int count : nextMap.values()) total += count;
                for (Map.Entry<ScreenId, Integer> e : nextMap.entrySet()) {
                    probabilities.put(e.getKey(), (double) e.getValue() / total);
                }
            }
        }

        // Nếu bậc 2 chưa có dữ liệu hoặc ở các chính sách khác, dùng bậc 1
        if (probabilities.isEmpty()) {
            Map<ScreenId, Integer> nextMap = order1Transitions.get(currentScreen);
            if (nextMap != null && !nextMap.isEmpty()) {
                int total = 0;
                for (int count : nextMap.values()) total += count;
                for (Map.Entry<ScreenId, Integer> e : nextMap.entrySet()) {
                    probabilities.put(e.getKey(), (double) e.getValue() / total);
                }
            }
        }

        List<Map.Entry<ScreenId, Double>> sorted = new ArrayList<>(probabilities.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return sorted;
    }

    private void seedInitialTransitions() {
        // Dựng sẵn dữ liệu ban đầu cho kịch bản đọc tin phổ biến:
        // Trang chủ -> thường bấm xem chi tiết bài (60%) hoặc tìm kiếm (25%) hoặc sang tab khác (15%)
        addTransition(ScreenId.FEED_NEWEST, ScreenId.POST_DETAIL, 6);
        addTransition(ScreenId.FEED_NEWEST, ScreenId.SEARCH, 2);
        addTransition(ScreenId.FEED_NEWEST, ScreenId.FEED_HOT, 2);

        addTransition(ScreenId.FEED_HOT, ScreenId.POST_DETAIL, 7);
        addTransition(ScreenId.FEED_HOT, ScreenId.FEED_NEWEST, 3);

        addTransition(ScreenId.POST_DETAIL, ScreenId.COMMENTS, 6);
        addTransition(ScreenId.POST_DETAIL, ScreenId.FEED_NEWEST, 3);
        addTransition(ScreenId.POST_DETAIL, ScreenId.PROFILE, 1);

        addTransition(ScreenId.COMMENTS, ScreenId.POST_DETAIL, 5);
        addTransition(ScreenId.COMMENTS, ScreenId.FEED_NEWEST, 5);
    }

    private void addTransition(ScreenId from, ScreenId to, int weight) {
        order1Transitions.putIfAbsent(from, new ConcurrentHashMap<>());
        order1Transitions.get(from).put(to, weight);
    }

    public void resetSession() {
        navigationHistory.clear();
        cache.clear();
        inFlightMap.clear();
    }
}

