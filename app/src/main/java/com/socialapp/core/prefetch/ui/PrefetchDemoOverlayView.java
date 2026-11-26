package com.socialapp.core.prefetch.ui;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.socialapp.MyApplication;
import com.socialapp.R;
import com.socialapp.core.di.AppContainer;
import com.socialapp.core.prefetch.BatchExperimentRunner;
import com.socialapp.core.prefetch.HitType;
import com.socialapp.core.prefetch.MetricsLogger;
import com.socialapp.core.prefetch.PrefetchEngine;
import com.socialapp.core.prefetch.ReplayEngine;
import com.socialapp.core.prefetch.ReplayResult;
import com.socialapp.core.prefetch.Repository;
import com.socialapp.core.prefetch.ScreenId;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Thanh hiển thị trạng thái Demo toàn cục cho bộ Markov & Prefetch.
 */
public class PrefetchDemoOverlayView extends FrameLayout {

    private TextView tvConfigBadge;
    private TextView btnToggleExpand;
    private TextView btnConfig;
    private TextView btnReplayToggle;
    private View containerExpandedDetails;
    private TextView tvCurrentScreen;
    private TextView tvPredictions;
    private TextView tvLastHitStatus;
    private TextView tvStatsSummary;

    private static boolean isExpanded = true;

    public PrefetchDemoOverlayView(@NonNull Context context) {
        this(context, null);
    }

    public PrefetchDemoOverlayView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(@NonNull Context context) {
        LayoutInflater.from(context).inflate(R.layout.layout_prefetch_demo_overlay, this, true);

        tvConfigBadge = findViewById(R.id.tvConfigBadge);
        btnToggleExpand = findViewById(R.id.btnToggleExpand);
        btnConfig = findViewById(R.id.btnConfig);
        btnReplayToggle = findViewById(R.id.btnReplayToggle);
        containerExpandedDetails = findViewById(R.id.containerExpandedDetails);
        tvCurrentScreen = findViewById(R.id.tvCurrentScreen);
        tvPredictions = findViewById(R.id.tvPredictions);
        tvLastHitStatus = findViewById(R.id.tvLastHitStatus);
        tvStatsSummary = findViewById(R.id.tvStatsSummary);

        applyExpandState();

        btnToggleExpand.setOnClickListener(v -> {
            isExpanded = !isExpanded;
            applyExpandState();
        });

        findViewById(R.id.headerBar).setOnClickListener(v -> {
            isExpanded = !isExpanded;
            applyExpandState();
        });

        btnConfig.setOnClickListener(v -> showConfigDialog());
        btnReplayToggle.setOnClickListener(v -> handleReplayToggle());

        refreshUI();
    }

    private void applyExpandState() {
        if (containerExpandedDetails != null) {
            containerExpandedDetails.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        }
        if (btnToggleExpand != null) {
            btnToggleExpand.setText(isExpanded ? "▲ Thu gọn" : "▼ Mở rộng");
        }
    }

    public void refreshUI() {
        AppContainer container = MyApplication.getInstance() != null ? MyApplication.getInstance().getAppContainer() : null;
        if (container == null) return;

        PrefetchEngine engine = container.getPrefetchEngine();
        MetricsLogger logger = container.getMetricsLogger();
        ReplayEngine replay = container.getReplayEngine();

        // 1. Cập nhật Badge cấu hình & trạng thái Replay
        if (replay != null && replay.isActive()) {
            btnReplayToggle.setText("⏹ Dừng");
            btnReplayToggle.setTextColor(0xFFF87171); // Đỏ
            String badge = String.format(Locale.US, "[Replay: S%d/%d | %s]",
                    replay.getCurrentSessionNumber(),
                    replay.getTotalSessions(),
                    engine.getCurrentPolicy().code
            );
            tvConfigBadge.setText(badge);
        } else {
            btnReplayToggle.setText("▶ Replay");
            btnReplayToggle.setTextColor(0xFF4ADE80); // Xanh lá
            String badge = String.format(Locale.US, "[%s | %s | %.1fx]",
                    engine.getCurrentPolicy().code,
                    engine.getCurrentNetwork().code,
                    engine.getTimeScale()
            );
            tvConfigBadge.setText(badge);
        }

        // 2. Màn hình hiện tại
        tvCurrentScreen.setText(engine.getLastCurrentScreen().getId());

        // 3. Danh sách dự đoán Markov
        List<Map.Entry<ScreenId, Double>> predictions = engine.getLastPredictions();
        if (predictions == null || predictions.isEmpty()) {
            tvPredictions.setText("Chưa có dự đoán");
        } else {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<ScreenId, Double> entry : predictions) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(String.format(Locale.US, "%s (%.1f%%)", entry.getKey().getId(), entry.getValue() * 100));
            }
            tvPredictions.setText(sb.toString());
        }

        // 4. Trạng thái bước vừa rồi
        HitType lastHit = Repository.getLastHitType();
        long lastLatency = Repository.getLastLatencyMs();
        String hitText = String.format(Locale.US, "%s (tải %d ms)", lastHit.name(), lastLatency);
        tvLastHitStatus.setText(hitText);
        if (lastHit == HitType.FULL_HIT) {
            tvLastHitStatus.setTextColor(0xFF34D399); // Xanh lá ngọc
        } else if (lastHit == HitType.LATE_HIT) {
            tvLastHitStatus.setTextColor(0xFFFBBF24); // Vàng hổ phách
        } else {
            tvLastHitStatus.setTextColor(0xFFF87171); // Đỏ san hô
        }

        // 5. Thống kê
        int total = logger.getTotalSteps();
        int hits = logger.getHitCount();
        double hitRate = total > 0 ? ((double) hits / total * 100) : 0.0;
        long wastedKb = logger.getTotalWastedBytes() / 1024;

        String stats = String.format(Locale.US, "Hit: %d/%d (%.1f%%) | Lãng phí mô phỏng đã chốt: %d KiB", hits, total, hitRate, wastedKb);
        ReplayResult.Metrics replayMetrics = logger.getCurrentReplayMetrics();
        if (replayMetrics != null) {
            stats += "\n" + formatLoadMetrics(replayMetrics);
        }
        PrefetchEngine.LambdaSnapshot lambda = engine.getP5Snapshot();
        if (lambda != null) {
            stats += String.format(Locale.US, "\n%s | λ=%.7f ms/B | Waste %s | MISS %s\nCập nhật %d | %s",
                    lambda.mode, lambda.current, percent(lambda.wasteRatio), percent(lambda.missRate),
                    lambda.updates, lambda.reason);
        } else if (engine.getCurrentPolicy() == PrefetchEngine.Policy.P5_ADAPTIVE_NETWORK) {
            stats += "\nLIVE: Legacy | Replay tiếp theo: " + engine.getNextP5Mode();
        }
        tvStatsSummary.setText(stats);
    }

    private void handleReplayToggle() {
        AppContainer container = MyApplication.getInstance() != null ? MyApplication.getInstance().getAppContainer() : null;
        if (container == null) return;

        ReplayEngine replay = container.getReplayEngine();
        if (replay.isActive()) {
            replay.stopReplay();
            Toast.makeText(getContext(), "Đã dừng Replay!", Toast.LENGTH_SHORT).show();
            refreshUI();
            return;
        }

        String[] sessionOptions = new String[]{
                "Chạy thử 5 phiên (Nhanh)",
                "Chạy thử 20 phiên (Trung bình)",
                "Chạy toàn bộ 100 phiên (Đầy đủ kịch bản)",
                "T4 — Calibration (10 sessions)",
                "T4 — Evaluation (21 sessions)",
                "Kết quả Replay gần nhất",
                "So sánh và xuất CSV demo T4"
        };
        final int[] counts = new int[]{5, 20, 100};

        new AlertDialog.Builder(getContext())
                .setTitle("▶ Bắt đầu Robot Replay")
                .setItems(sessionOptions, (dialog, which) -> {
                    if (which == sessionOptions.length - 1) {
                        showDemoResults(replay);
                        return;
                    }
                    if (which == sessionOptions.length - 2) {
                        ReplayResult result = replay.getLatestResult();
                        if (result == null) {
                            Toast.makeText(getContext(), "Chưa có kết quả Replay.", Toast.LENGTH_SHORT).show();
                        } else {
                            showReplayResult(result);
                        }
                        return;
                    }
                    boolean started;
                    if (which == counts.length) {
                        started = replay.startT4CalibrationReplay();
                    } else if (which == counts.length + 1) {
                        started = replay.startT4EvaluationReplay();
                    } else {
                        replay.loadSessionsFromAsset(ReplayEngine.DEFAULT_ASSET_FILE);
                        started = replay.startReplay(counts[which]);
                    }
                    if (started && replay.isActive()) {
                        Toast.makeText(getContext(), "Bắt đầu chạy Replay " + replay.getTotalSessions() + " phiên...",
                                Toast.LENGTH_SHORT).show();
                    }
                    refreshUI();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void showDemoResults(ReplayEngine replay) {
        List<ReplayResult> results = replay.getDemoResults();
        StringBuilder message = new StringBuilder(
                "T4 — Calibration (10 sessions) · LTE mô phỏng · Payload 1.0x · Replay 5.0x\n"
                        + "Có " + results.size() + "/3 cấu hình hợp lệ.\n");
        for (ReplayResult result : results) {
            ReplayResult.Metrics metrics = result.metrics;
            message.append("\n").append(result.lambda == null ? "P0" :
                    result.lambda.mode == PrefetchEngine.P5Mode.FIXED_LAMBDA ? "P5 Fixed Lambda" : "P5 Adaptive Lambda")
                    .append("\n").append(formatLoadMetrics(metrics))
                    .append("\nHit rate ").append(percent(metrics.hitRate))
                    .append(" | Waste ").append(metrics.simulatedWastedBytes == null ? "N/A"
                            : metrics.simulatedWastedBytes + " B");
            if (result.lambda != null) {
                message.append(String.format(Locale.US, "\nλ %.7f ms/B | Cập nhật %d | %s",
                        result.lambda.current, result.lambda.updates, result.lambda.reason));
            }
            message.append("\n");
        }
        AlertDialog.Builder dialog = new AlertDialog.Builder(getContext())
                .setTitle("Kết quả demo T4")
                .setMessage(message)
                .setNegativeButton("Đóng", null);
        if (results.size() == 3 && getContext() instanceof Activity) {
            dialog.setPositiveButton("Xuất CSV",
                    (ignored, which) -> ReplayDemoExport.share((Activity) getContext(),
                            results, replay::getCalibrationMetricsFile));
        }
        dialog.show();
    }

    private static String formatLoadMetrics(ReplayResult.Metrics metrics) {
        return String.format(Locale.US, "FULL HIT / LATE HIT / MISS: %d / %d / %d | Tải TB %s",
                metrics.fullHits, metrics.lateHits, metrics.misses, metrics.averageLoadLatencyMs == null
                        ? "N/A" : String.format(Locale.US, "%.2f ms", metrics.averageLoadLatencyMs));
    }

    boolean showReplayResult(ReplayResult result) {
        if (!(getContext() instanceof Activity)) return false;
        Activity activity = (Activity) getContext();
        if (activity.isFinishing() || activity.isDestroyed() || !isAttachedToWindow()) return false;
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Kết quả Replay gần nhất")
                .setMessage(formatReplayResult(result))
                .setPositiveButton("Đóng", null)
                .show();
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) message.setTextIsSelectable(true);
        return true;
    }

    private static String formatReplayResult(ReplayResult result) {
        String outcome;
        switch (result.outcome) {
            case COMPLETED: outcome = "Hoàn thành"; break;
            case STOPPED: outcome = "Đã dừng"; break;
            default: outcome = "Không hợp lệ (" + result.reason + ")"; break;
        }
        ReplayResult.Configuration config = result.configuration;
        ReplayResult.Metrics metrics = result.metrics;
        ReplayResult.ExportStatus export = result.getExportStatus();
        boolean hasLoads = metrics != null && metrics.hitRate != null;
        return "Trạng thái: " + outcome + "\nRun ID: " + result.runId + "\n"
                + "CSV v2: " + (metrics == null ? "N/A" : export == null ? "Đang lưu"
                : export.isComplete() ? "Đầy đủ" : "Không đầy đủ (bỏ " + export.droppedRows
                + ", lỗi ghi " + export.failedRows + ")") + "\n"
                + "Cấu hình lúc bắt đầu: " + (config == null ? "N/A" : String.format(Locale.US,
                "%s | %s | payload %.1fx | tốc độ %.1fx | %s", config.policy.code, config.network.code,
                config.payloadScale, config.timeScale, config.simulationMode ? "mô phỏng" : "mạng thật")) + "\n"
                + "Bước đã xác nhận: " + result.confirmedSteps + " | Transition: " + result.transitions + "\n"
                + "FULL HIT / LATE HIT / MISS: " + (hasLoads ? metrics.fullHits + " / " + metrics.lateHits + " / " + metrics.misses : "N/A") + "\n"
                + "Hit rate: " + (hasLoads ? String.format(Locale.US, "%.1f%%", metrics.hitRate * 100) : "N/A") + "\n"
                + "Load latency trung bình: " + (hasLoads ? String.format(Locale.US, "%.2f ms", metrics.averageLoadLatencyMs) : "N/A") + "\n"
                + "Wasted bytes mô phỏng: " + (metrics == null || metrics.simulatedWastedBytes == null
                ? "N/A" : metrics.simulatedWastedBytes + " B")
                + (result.lambda == null ? "" : String.format(Locale.US,
                "\nP5 %s | λ đầu %.7f | λ cuối %.7f ms/B | Cập nhật %d | %s"
                        + "\nPrefetch hoàn tất %d B | Lãng phí %d B | Waste %s",
                result.lambda.mode, result.lambda.initial, result.lambda.current, result.lambda.updates, result.lambda.reason,
                result.lambda.completedBytes, result.lambda.wastedBytes, percent(result.lambda.wasteRatio)));
    }

    private static String percent(Double ratio) {
        return ratio == null ? "N/A" : String.format(Locale.US, "%.1f%%", ratio * 100);
    }

    private void showConfigDialog() {
        AppContainer container = MyApplication.getInstance() != null ? MyApplication.getInstance().getAppContainer() : null;
        if (container == null) return;

        PrefetchEngine engine = container.getPrefetchEngine();
        MetricsLogger logger = container.getMetricsLogger();

        String[] options = new String[]{
                "Chính sách Prefetch (Hiện tại: " + engine.getCurrentPolicy().code + ")",
                "Hồ sơ Mạng giả lập (Hiện tại: " + engine.getCurrentNetwork().code + ")",
                "Tốc độ Tua nhanh (Hiện tại: " + engine.getTimeScale() + "x)",
                "Chế độ Mô phỏng mạng (Hiện tại: " + (engine.isSimulationMode() ? "BẬT" : "TẮT") + ")",
                "Đặt lại thống kê phiên này (Reset Stats)",
                "🚀 Chạy Lô Thử nghiệm Ma trận (Batch Run 16 cấu hình)",
                String.format(Locale.US, "Payload mô phỏng %.1fx (đặt 1.0x)", engine.getPayloadScale())
        };

        new AlertDialog.Builder(getContext())
                .setTitle("⚙ Cấu hình Thử nghiệm")
                .setItems(options, (dialog, which) -> {
                    if (container.getReplayEngine().isActive() && which != 5) {
                        Toast.makeText(getContext(), "Dừng Replay trước khi đổi cấu hình.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    switch (which) {
                        case 0:
                            showPolicySelectionDialog(engine);
                            break;
                        case 1:
                            showNetworkSelectionDialog(engine);
                            break;
                        case 2:
                            showTimeScaleDialog(engine);
                            break;
                        case 3:
                            engine.setSimulationMode(!engine.isSimulationMode());
                            Toast.makeText(getContext(), "Chế độ mô phỏng mạng: " + (engine.isSimulationMode() ? "BẬT" : "TẮT"), Toast.LENGTH_SHORT).show();
                            refreshUI();
                            break;
                        case 4:
                            container.getProfileManager().resetModel();
                            logger.resetSessionStats();
                            Repository.setSessionId(Repository.getSessionId() + 1);
                            Toast.makeText(getContext(), "Đã đặt lại dữ liệu thống kê!", Toast.LENGTH_SHORT).show();
                            refreshUI();
                            break;
                        case 5:
                            BatchExperimentRunner runner = container.getBatchRunner();
                            if (runner.isRunning()) {
                                runner.stopBatch();
                                Toast.makeText(getContext(), "Đã dừng chạy Lô!", Toast.LENGTH_SHORT).show();
                            } else {
                                container.getReplayEngine().loadSessionsFromAsset(ReplayEngine.DEFAULT_ASSET_FILE);
                                runner.setupKeyBenchmarkMatrix(5);
                                runner.startBatch();
                                if (runner.isRunning()) {
                                    Toast.makeText(getContext(), "Đang chạy tự động 16 cấu hình ma trận...", Toast.LENGTH_LONG).show();
                                }
                            }
                            refreshUI();
                            break;
                        case 6:
                            engine.setPayloadScale(1.0f);
                            Toast.makeText(getContext(), "Payload mô phỏng: 1.0x", Toast.LENGTH_SHORT).show();
                            refreshUI();
                            break;
                    }
                })
                .setNegativeButton("Đóng", null)
                .show();
    }

    private void showPolicySelectionDialog(PrefetchEngine engine) {
        PrefetchEngine.Policy[] policies = PrefetchEngine.Policy.values();
        String[] names = new String[policies.length];
        int selectedIndex = 0;
        for (int i = 0; i < policies.length; i++) {
            names[i] = policies[i].code + " - " + policies[i].description;
            if (policies[i] == engine.getCurrentPolicy()) {
                selectedIndex = i;
            }
        }

        new AlertDialog.Builder(getContext())
                .setTitle("Chọn Chính sách Prefetch")
                .setSingleChoiceItems(names, selectedIndex, (dialog, which) -> {
                    dialog.dismiss();
                    if (policies[which] == PrefetchEngine.Policy.P5_ADAPTIVE_NETWORK) {
                        showP5ModeDialog(engine);
                        return;
                    }
                    engine.setCurrentPolicy(policies[which]);
                    Toast.makeText(getContext(), "Đã chọn: " + policies[which].code, Toast.LENGTH_SHORT).show();
                    refreshUI();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void showP5ModeDialog(PrefetchEngine engine) {
        PrefetchEngine.P5Mode[] modes = PrefetchEngine.P5Mode.values();
        new AlertDialog.Builder(getContext())
                .setTitle("P5 cho Replay")
                .setSingleChoiceItems(new String[]{"Legacy", "Fixed Lambda", "Adaptive Lambda"},
                        engine.getNextP5Mode().ordinal(), (dialog, index) -> {
                            engine.setNextP5Mode(modes[index]);
                            engine.setCurrentPolicy(PrefetchEngine.Policy.P5_ADAPTIVE_NETWORK);
                            refreshUI();
                            dialog.dismiss();
                        })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void showNetworkSelectionDialog(PrefetchEngine engine) {
        PrefetchEngine.NetworkProfile[] networks = PrefetchEngine.NetworkProfile.values();
        String[] names = new String[networks.length];
        int selectedIndex = 0;
        for (int i = 0; i < networks.length; i++) {
            names[i] = networks[i].code + " (RTT " + networks[i].rttMs + "ms)";
            if (networks[i] == engine.getCurrentNetwork()) {
                selectedIndex = i;
            }
        }

        new AlertDialog.Builder(getContext())
                .setTitle("Chọn Hồ sơ Mạng Giả lập")
                .setSingleChoiceItems(names, selectedIndex, (dialog, which) -> {
                    engine.setCurrentNetwork(networks[which]);
                    Toast.makeText(getContext(), "Đã chọn mạng: " + networks[which].code, Toast.LENGTH_SHORT).show();
                    refreshUI();
                    dialog.dismiss();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void showTimeScaleDialog(PrefetchEngine engine) {
        float[] speeds = new float[]{1.0f, 2.0f, 5.0f, 10.0f};
        String[] names = new String[]{"1.0x (Thời gian thực)", "2.0x (Nhanh gấp đôi)", "5.0x (Tua nhanh 5 lần)", "10.0x (Tua nhanh 10 lần)"};

        int selectedIndex = 0;
        for (int i = 0; i < speeds.length; i++) {
            if (Math.abs(speeds[i] - engine.getTimeScale()) < 0.1f) {
                selectedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(getContext())
                .setTitle("Chọn Tốc độ Chạy (TimeScale)")
                .setSingleChoiceItems(names, selectedIndex, (dialog, which) -> {
                    engine.setTimeScale(speeds[which]);
                    Toast.makeText(getContext(), "Tốc độ: " + speeds[which] + "x", Toast.LENGTH_SHORT).show();
                    refreshUI();
                    dialog.dismiss();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }
}

