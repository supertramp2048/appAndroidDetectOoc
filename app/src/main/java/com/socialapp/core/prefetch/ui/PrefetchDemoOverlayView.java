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
 * Thanh hiển thị trạng thái Demo tối giản cho bộ dự đoán Markov & Prefetch.
 * Chỉ giữ 4 chính sách trọng tâm (P0, P3, P4, P5), cố định tốc độ thực 1.0x để số đo độ trễ chuẩn xác.
 */
public class PrefetchDemoOverlayView extends FrameLayout {

    private static final PrefetchEngine.Policy[] DEMO_POLICIES = new PrefetchEngine.Policy[]{
            PrefetchEngine.Policy.P0_NONE,
            PrefetchEngine.Policy.P3_MARKOV_FIXED,
            PrefetchEngine.Policy.P4_MARKOV_ORDER2,
            PrefetchEngine.Policy.P5_ADAPTIVE_NETWORK
    };

    private static final String[] DEMO_POLICY_LABELS = new String[]{
            "P0 - Không tải trước",
            "P3 - Markov bậc 1",
            "P4 - Markov bậc 2 (Backoff)",
            "P5 - Thích ứng mạng"
    };

    private TextView tvConfigBadge;
    private TextView btnToggleExpand;
    private TextView btnCompare;
    private TextView btnConfig;
    private TextView btnReplayToggle;
    private View containerExpandedDetails;
    private TextView tvCurrentScreen;
    private TextView tvPredictions;
    private TextView tvLastHitStatus;
    private TextView tvPrefetchActionStatus;
    private TextView tvRadioAndMediaStatus;
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
        btnCompare = findViewById(R.id.btnCompare);
        btnConfig = findViewById(R.id.btnConfig);
        btnReplayToggle = findViewById(R.id.btnReplayToggle);
        containerExpandedDetails = findViewById(R.id.containerExpandedDetails);
        tvCurrentScreen = findViewById(R.id.tvCurrentScreen);
        tvPredictions = findViewById(R.id.tvPredictions);
        tvLastHitStatus = findViewById(R.id.tvLastHitStatus);
        tvPrefetchActionStatus = findViewById(R.id.tvPrefetchActionStatus);
        tvRadioAndMediaStatus = findViewById(R.id.tvRadioAndMediaStatus);
        tvStatsSummary = findViewById(R.id.tvStatsSummary);

        AppContainer container = getAppContainer();
        if (container != null) {
            isExpanded = container.getPrefetchDemoPreferences().isOverlayExpanded();
        }

        applyExpandState();

        btnToggleExpand.setOnClickListener(v -> toggleExpandState());

        findViewById(R.id.headerBar).setOnClickListener(v -> toggleExpandState());

        tvConfigBadge.setOnClickListener(v -> showConfigDialog());
        btnConfig.setOnClickListener(v -> showConfigDialog());
        btnCompare.setOnClickListener(v -> exportComparisonFile());
        btnReplayToggle.setOnClickListener(v -> handleReplayToggle());

        refreshUI();
    }

    @Nullable
    private AppContainer getAppContainer() {
        return MyApplication.getInstance() != null ? MyApplication.getInstance().getAppContainer() : null;
    }

    private void toggleExpandState() {
        isExpanded = !isExpanded;
        AppContainer container = getAppContainer();
        if (container != null) {
            container.getPrefetchDemoPreferences().setOverlayExpanded(isExpanded);
        }
        applyExpandState();
    }

    private void applyExpandState() {
        if (containerExpandedDetails != null) {
            containerExpandedDetails.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        }
        if (btnToggleExpand != null) {
            btnToggleExpand.setText(isExpanded ? "▾" : "▴");
        }
    }

    public void refreshUI() {
        AppContainer container = getAppContainer();
        if (container == null) return;

        isExpanded = container.getPrefetchDemoPreferences().isOverlayExpanded();
        applyExpandState();

        PrefetchEngine engine = container.getPrefetchEngine();
        MetricsLogger logger = container.getMetricsLogger();
        ReplayEngine replay = container.getReplayEngine();

        // 1. Nhãn cấu hình tối giản: chỉ hiển thị Chính sách · Mạng
        if (replay != null && replay.isActive()) {
            btnReplayToggle.setText("Dừng");
            btnReplayToggle.setTextColor(0xFFE5E7EB);
            String badge = String.format(Locale.US, "S%d/%d · %s",
                    replay.getCurrentSessionNumber(),
                    replay.getTotalSessions(),
                    engine.getCurrentPolicy().code
            );
            tvConfigBadge.setText(badge);
        } else {
            btnReplayToggle.setText("Chạy");
            btnReplayToggle.setTextColor(0xFFFFFFFF);
            String networkOrReal = engine.isSimulationMode() ? engine.getCurrentNetwork().code : "REAL";
            String badge = String.format(Locale.US, "%s · %s",
                    engine.getCurrentPolicy().code,
                    networkOrReal
            );
            tvConfigBadge.setText(badge);
        }

        // 2. Màn hình hiện tại & Dự đoán Markov (Top 2)
        tvCurrentScreen.setText(engine.getLastCurrentScreen().getId());
        List<Map.Entry<ScreenId, Double>> predictions = engine.getLastPredictions();
        if (predictions == null || predictions.isEmpty()) {
            tvPredictions.setText("Chưa đủ mẫu");
        } else {
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (Map.Entry<ScreenId, Double> entry : predictions) {
                if (count > 0) sb.append(", ");
                sb.append(String.format(Locale.US, "%s (%.0f%%)", entry.getKey().getId(), entry.getValue() * 100));
                count++;
                if (count >= 2) break;
            }
            tvPredictions.setText(sb.toString());
        }

        // 3. Trạng thái bước vừa mở
        HitType lastHit = Repository.getLastHitType();
        long lastLatency = Repository.getLastLatencyMs();
        tvLastHitStatus.setText(String.format(Locale.US, "%s (%d ms)", lastHit.name(), lastLatency));
        if (lastHit == HitType.FULL_HIT) {
            tvLastHitStatus.setTextColor(0xFFFFFFFF);
        } else if (lastHit == HitType.LATE_HIT) {
            tvLastHitStatus.setTextColor(0xFFD1D5DB);
        } else {
            tvLastHitStatus.setTextColor(0xFF9CA3AF);
        }

        // 4. Hiển thị chi tiết trạng thái Ý 1 (Viewport Dwell, SWR Cache, Ảnh Glide & Ngân sách mạng)
        if (tvPrefetchActionStatus != null) {
            tvPrefetchActionStatus.setText(engine.getLastPrefetchActionStatus());
        }
        if (tvRadioAndMediaStatus != null) {
            int dwellPost = engine.getLastDwelledPostId() > 0 ? engine.getLastDwelledPostId() : engine.getLastActivePostId();
            int glideImages = engine.getPreloadedImageCount();
            int cacheEntries = engine.getCache().size();
            long spentKb = engine.getRadioNetworkMonitor().getCellularPrefetchBytesSpent() / 1024;
            String modeTag = engine.isSimulationMode()
                    ? ("Sim:" + engine.getCurrentNetwork().code)
                    : ("Real:" + engine.getRadioNetworkMonitor().detectLiveNetworkProfile(getContext()).code);

            String radioInfo = String.format(
                    Locale.US,
                    "👁 Post #%d | 🖼 Glide: %d ảnh | 🗄 Cache: %d mục | 📶 %s (%d/2048 KB)",
                    dwellPost,
                    glideImages,
                    cacheEntries,
                    modeTag,
                    spentKb
            );
            tvRadioAndMediaStatus.setText(radioInfo);
        }

        // 5. Tóm tắt ngắn gọn: Hit%, Trễ TB, Lãng phí
        int total = logger.getTotalSteps();
        int hits = logger.getHitCount();
        double hitRate = total > 0 ? ((double) hits / total * 100.0) : 0.0;
        long avgLatency = logger.getAvgLatencyMs();
        long wastedKb = logger.getTotalWastedBytes() / 1024;

        String stats = String.format(
                Locale.US,
                "Hit: %.1f%% (%d/%d)  ·  Trễ TB: %d ms  ·  Thừa: %d KB",
                hitRate, hits, total, avgLatency, wastedKb
        );
        PrefetchEngine.LambdaSnapshot lambda = engine.getP5Snapshot();
        if (lambda != null) {
            stats += String.format(
                    Locale.US,
                    "\n%s | λ=%.7f ms/B | Waste %s | MISS %s",
                    lambda.mode, lambda.current, percent(lambda.wasteRatio), percent(lambda.missRate)
            );
        }
        tvStatsSummary.setText(stats);
    }

    private void handleReplayToggle() {
        AppContainer container = getAppContainer();
        if (container == null) return;

        ReplayEngine replay = container.getReplayEngine();
        if (replay.isActive()) {
            replay.stopReplay();
            Toast.makeText(getContext(), "Đã dừng Replay", Toast.LENGTH_SHORT).show();
            refreshUI();
            return;
        }

        String[] sessionOptions = new String[]{
                "3 phiên (Demo nhanh)",
                "10 phiên (Đánh giá đầy đủ)",
                "T4 — Calibration (10 sessions)",
                "T4 — Evaluation (21 sessions)",
                "Kết quả Replay gần nhất",
                "So sánh và xuất CSV demo T4"
        };
        final int[] counts = new int[]{3, 10};

        new AlertDialog.Builder(getContext())
                .setTitle("Chạy kịch bản tự động")
                .setItems(sessionOptions, (dialog, which) -> {
                    if (which == 5) {
                        showDemoResults(replay);
                        return;
                    }
                    if (which == 4) {
                        ReplayResult result = replay.getLatestResult();
                        if (result == null) {
                            Toast.makeText(getContext(), "Chưa có kết quả Replay.", Toast.LENGTH_SHORT).show();
                        } else {
                            showReplayResult(result);
                        }
                        return;
                    }
                    boolean started;
                    if (which == 2) {
                        started = replay.startT4CalibrationReplay();
                    } else if (which == 3) {
                        started = replay.startT4EvaluationReplay();
                    } else {
                        replay.loadSessionsFromAsset(ReplayEngine.DEFAULT_ASSET_FILE);
                        started = replay.startReplay(counts[which]);
                    }
                    if (started && replay.isActive()) {
                        Toast.makeText(
                                getContext(),
                                "Bắt đầu chạy Replay " + replay.getTotalSessions() + " phiên...",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                    refreshUI();
                })
                .setNegativeButton("Đóng", null)
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

    /**
     * Hộp thoại Cài đặt: ưu tiên 4 mục thiết yếu của Kịch bản demo 1 ở đầu,
     * các tùy chọn T4/Batch nâng cao nằm ngay bên dưới.
     */
    private void showConfigDialog() {
        AppContainer container = getAppContainer();
        if (container == null) return;

        PrefetchEngine engine = container.getPrefetchEngine();
        MetricsLogger logger = container.getMetricsLogger();

        String[] options = new String[]{
                "Chính sách:  " + engine.getCurrentPolicy().code,
                "Mạng giả lập:  " + engine.getCurrentNetwork().code,
                "Chế độ Mô phỏng mạng:  " + (engine.isSimulationMode() ? "BẬT" : "TẮT"),
                "Đặt lại phiên hiện tại",
                "Tốc độ Tua nhanh:  " + engine.getTimeScale() + "x",
                "Chạy Lô Ma trận (Batch 16 cấu hình)",
                String.format(Locale.US, "Payload mô phỏng:  %.1fx (đặt 1.0x)", engine.getPayloadScale())
        };

        new AlertDialog.Builder(getContext())
                .setTitle("Cài đặt")
                .setItems(options, (dialog, which) -> {
                    if (container.getReplayEngine().isActive() && which != 5) {
                        Toast.makeText(getContext(), "Dừng Replay trước khi đổi cấu hình.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    switch (which) {
                        case 0:
                            showPolicySelectionDialog(engine, logger);
                            break;
                        case 1:
                            showNetworkSelectionDialog(engine, logger);
                            break;
                        case 2:
                            engine.setSimulationMode(!engine.isSimulationMode());
                            Toast.makeText(getContext(), "Chế độ mô phỏng mạng: " + (engine.isSimulationMode() ? "BẬT" : "TẮT"), Toast.LENGTH_SHORT).show();
                            refreshUI();
                            break;
                        case 3:
                            logger.saveCurrentRunSnapshot(
                                    engine.getCurrentPolicy().code,
                                    engine.getCurrentNetwork().code
                            );
                            container.getProfileManager().resetModel();
                            logger.resetSessionStats();
                            engine.resetSession();
                            Repository.setSessionId(Repository.getSessionId() + 1);
                            Toast.makeText(getContext(), "Đã lưu & đặt lại phiên mới", Toast.LENGTH_SHORT).show();
                            refreshUI();
                            break;
                        case 4:
                            showTimeScaleDialog(engine);
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

    private void showPolicySelectionDialog(PrefetchEngine engine, MetricsLogger logger) {
        int selectedIndex = 0;
        for (int i = 0; i < DEMO_POLICIES.length; i++) {
            if (DEMO_POLICIES[i] == engine.getCurrentPolicy()) {
                selectedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(getContext())
                .setTitle("Chọn chính sách")
                .setSingleChoiceItems(DEMO_POLICY_LABELS, selectedIndex, (dialog, which) -> {
                    dialog.dismiss();
                    if (logger.getTotalSteps() > 0) {
                        logger.saveCurrentRunSnapshot(
                                engine.getCurrentPolicy().code,
                                engine.getCurrentNetwork().code
                        );
                        logger.resetSessionStats();
                        engine.resetSession();
                    }
                    PrefetchEngine.Policy chosen = DEMO_POLICIES[which];
                    if (chosen == PrefetchEngine.Policy.P5_ADAPTIVE_NETWORK) {
                        showP5ModeDialog(engine);
                        return;
                    }
                    engine.setCurrentPolicy(chosen);
                    Toast.makeText(getContext(), "Đã chọn: " + chosen.code, Toast.LENGTH_SHORT).show();
                    refreshUI();
                })
                .setNegativeButton("Đóng", null)
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

    private void showNetworkSelectionDialog(PrefetchEngine engine, MetricsLogger logger) {
        PrefetchEngine.NetworkProfile[] networks = PrefetchEngine.NetworkProfile.values();
        String[] names = new String[networks.length];
        int selectedIndex = 0;
        for (int i = 0; i < networks.length; i++) {
            names[i] = networks[i].code + " (RTT " + networks[i].rttMs + " ms)";
            if (networks[i] == engine.getCurrentNetwork()) {
                selectedIndex = i;
            }
        }

        new AlertDialog.Builder(getContext())
                .setTitle("Chọn hồ sơ mạng")
                .setSingleChoiceItems(names, selectedIndex, (dialog, which) -> {
                    if (logger.getTotalSteps() > 0) {
                        logger.saveCurrentRunSnapshot(
                                engine.getCurrentPolicy().code,
                                engine.getCurrentNetwork().code
                        );
                        logger.resetSessionStats();
                        engine.resetSession();
                    }
                    engine.setCurrentNetwork(networks[which]);
                    refreshUI();
                    dialog.dismiss();
                })
                .setNegativeButton("Đóng", null)
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

    /**
     * Xuất toàn bộ kết quả các lần chạy ra file CSV bên ngoài (Download/prefetch_comparison.csv)
     * để mở bằng Excel / Python so sánh trực tiếp, không hiện bảng popup trong App.
     */
    private void exportComparisonFile() {
        AppContainer container = getAppContainer();
        if (container == null) return;

        PrefetchEngine engine = container.getPrefetchEngine();
        MetricsLogger logger = container.getMetricsLogger();

        if (logger.getTotalSteps() > 0) {
            logger.saveCurrentRunSnapshot(
                    engine.getCurrentPolicy().code,
                    engine.getCurrentNetwork().code
            );
        }
        if (logger.getComparisonHistory().isEmpty()) {
            logger.saveReplayResultSnapshot(container.getReplayEngine().getLatestResult());
        }

        String exportedPath = logger.exportComparisonCsv();
        if (exportedPath == null) {
            Toast.makeText(getContext(), "Chưa có dữ liệu chạy để xuất CSV", Toast.LENGTH_SHORT).show();
        } else {
            int rows = logger.getComparisonHistory().size();
            Toast.makeText(
                    getContext(),
                    "Đã xuất " + rows + " cấu hình → " + exportedPath,
                    Toast.LENGTH_LONG
            ).show();
        }
    }
}
