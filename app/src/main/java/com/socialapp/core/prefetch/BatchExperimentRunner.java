package com.socialapp.core.prefetch;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.socialapp.MyApplication;
import com.socialapp.core.di.AppContainer;

import java.util.ArrayList;
import java.util.List;

/**
 * Bộ chạy thử nghiệm hàng loạt (Batch Experiment Runner) cho ma trận cấu hình:
 * Chính sách (P0 - P5) x Hồ sơ mạng (WiFi, LTE, 3G, EDGE) x Độ nặng.
 */
public class BatchExperimentRunner {

    private static final String TAG = "BatchRunner";

    public static class ExperimentConfig {
        public final PrefetchEngine.Policy policy;
        public final PrefetchEngine.NetworkProfile network;
        public final float payloadScale;
        public final int sessionsToRun;

        public ExperimentConfig(
                PrefetchEngine.Policy policy,
                PrefetchEngine.NetworkProfile network,
                float payloadScale,
                int sessionsToRun
        ) {
            this.policy = policy;
            this.network = network;
            this.payloadScale = payloadScale;
            this.sessionsToRun = sessionsToRun;
        }

        public String getConfigFileName() {
            return String.format("%s_%s_x%.1f", policy.code, network.code, payloadScale);
        }
    }

    public interface OnBatchProgressListener {
        void onConfigStarted(int configIndex, int totalConfigs, ExperimentConfig config);
        void onBatchCompleted(int totalConfigs);
    }

    private final Context context;
    private final List<ExperimentConfig> queue = new ArrayList<>();
    private int currentConfigIndex = 0;
    private boolean isRunning = false;
    private OnBatchProgressListener listener;

    public BatchExperimentRunner(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    public void setListener(@Nullable OnBatchProgressListener listener) {
        this.listener = listener;
    }

    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Tạo danh sách cấu hình trọng tâm để chạy lấy số liệu thực nghiệm.
     * So sánh: P0 (Baseline), P1 (All), P3 (Markov Fixed), P5 (Adaptive Network)
     * Trên 4 mạng: WiFi, LTE, 3G, EDGE.
     */
    public void setupKeyBenchmarkMatrix(int sessionsPerConfig) {
        queue.clear();
        PrefetchEngine.Policy[] targetPolicies = new PrefetchEngine.Policy[]{
                PrefetchEngine.Policy.P0_NONE,
                PrefetchEngine.Policy.P1_ALL,
                PrefetchEngine.Policy.P3_MARKOV_FIXED,
                PrefetchEngine.Policy.P5_ADAPTIVE_NETWORK
        };

        PrefetchEngine.NetworkProfile[] targetNetworks = new PrefetchEngine.NetworkProfile[]{
                PrefetchEngine.NetworkProfile.WIFI,
                PrefetchEngine.NetworkProfile.LTE,
                PrefetchEngine.NetworkProfile.G3,
                PrefetchEngine.NetworkProfile.EDGE
        };

        for (PrefetchEngine.Policy p : targetPolicies) {
            for (PrefetchEngine.NetworkProfile net : targetNetworks) {
                queue.add(new ExperimentConfig(p, net, 1.0f, sessionsPerConfig));
            }
        }
    }

    public void startBatch() {
        if (isRunning || queue.isEmpty()) return;

        isRunning = true;
        currentConfigIndex = 0;
        runNextConfig();
    }

    public void stopBatch() {
        isRunning = false;
        AppContainer container = MyApplication.getInstance() != null ? MyApplication.getInstance().getAppContainer() : null;
        if (container != null) {
            container.getReplayEngine().stopReplay();
        }
    }

    private void runNextConfig() {
        if (!isRunning) return;

        if (currentConfigIndex >= queue.size()) {
            isRunning = false;
            if (listener != null) {
                listener.onBatchCompleted(queue.size());
            }
            Toast.makeText(context, "Hoàn tất chạy toàn bộ ma trận thí nghiệm!", Toast.LENGTH_LONG).show();
            return;
        }

        ExperimentConfig config = queue.get(currentConfigIndex);
        AppContainer container = MyApplication.getInstance() != null ? MyApplication.getInstance().getAppContainer() : null;
        if (container == null) return;

        PrefetchEngine engine = container.getPrefetchEngine();
        MetricsLogger logger = container.getMetricsLogger();
        ReplayEngine replay = container.getReplayEngine();

        // 1. Áp dụng cấu hình thí nghiệm
        engine.setCurrentPolicy(config.policy);
        engine.setCurrentNetwork(config.network);
        engine.setPayloadScale(config.payloadScale);
        logger.setConfigName(config.getConfigFileName());
        logger.resetSessionStats();
        engine.resetSession();

        Log.d(TAG, String.format("Starting experiment %d/%d: %s", currentConfigIndex + 1, queue.size(), config.getConfigFileName()));

        if (listener != null) {
            listener.onConfigStarted(currentConfigIndex + 1, queue.size(), config);
        }

        // Lắng nghe khi Replay hoàn thành cấu hình này thì tự động nhảy sang cấu hình kế tiếp
        replay.setListener(new ReplayEngine.OnReplayProgressListener() {
            @Override
            public void onProgress(int currentSession, int totalSessions, int currentStep, int totalSteps, ScreenId screen) {}

            @Override
            public void onSessionCompleted(int sessionId, int totalSessions) {}

            @Override
            public void onReplayFinished() {
                currentConfigIndex++;
                runNextConfig();
            }

            @Override
            public void onReplayStopped() {
                isRunning = false;
            }
        });

        // Kích hoạt chạy Replay với số phiên yêu cầu
        replay.startReplay(config.sessionsToRun);
    }
}

