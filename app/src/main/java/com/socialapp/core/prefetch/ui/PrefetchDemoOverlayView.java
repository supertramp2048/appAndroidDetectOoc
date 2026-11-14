package com.socialapp.core.prefetch.ui;

import android.app.AlertDialog;
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
        if (replay != null && replay.isRunning()) {
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
            int count = 0;
            for (Map.Entry<ScreenId, Double> entry : predictions) {
                if (count > 0) sb.append(", ");
                sb.append(String.format(Locale.US, "%s (%.0f%%)", entry.getKey().getId(), entry.getValue() * 100));
                count++;
                if (count >= 2) break; // Hiển thị top 2 để thanh gọn gàng
            }
            tvPredictions.setText(sb.toString());
        }

        // 4. Trạng thái bước vừa rồi
        HitType lastHit = Repository.getLastHitType();
        long lastLatency = Repository.getLastLatencyMs();
        String hitText = String.format(Locale.US, "%s (%d ms)", lastHit.name(), lastLatency);
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

        String stats = String.format(Locale.US, "Hit: %d/%d (%.1f%%) | Lãng phí: %d KB", hits, total, hitRate, wastedKb);
        tvStatsSummary.setText(stats);
    }

    private void handleReplayToggle() {
        AppContainer container = MyApplication.getInstance() != null ? MyApplication.getInstance().getAppContainer() : null;
        if (container == null) return;

        ReplayEngine replay = container.getReplayEngine();
        if (replay.isRunning()) {
            replay.stopReplay();
            Toast.makeText(getContext(), "Đã dừng Replay!", Toast.LENGTH_SHORT).show();
            refreshUI();
            return;
        }

        String[] sessionOptions = new String[]{
                "Chạy thử 5 phiên (Nhanh)",
                "Chạy thử 20 phiên (Trung bình)",
                "Chạy toàn bộ 100 phiên (Đầy đủ kịch bản)"
        };
        final int[] counts = new int[]{5, 20, 100};

        new AlertDialog.Builder(getContext())
                .setTitle("▶ Bắt đầu Robot Replay")
                .setItems(sessionOptions, (dialog, which) -> {
                    int selectedCount = counts[which];
                    Toast.makeText(getContext(), "Bắt đầu chạy Replay " + selectedCount + " phiên...", Toast.LENGTH_SHORT).show();
                    replay.startReplay(selectedCount);
                    refreshUI();
                })
                .setNegativeButton("Hủy", null)
                .show();
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
                "🚀 Chạy Lô Thử nghiệm Ma trận (Batch Run 16 cấu hình)"
        };

        new AlertDialog.Builder(getContext())
                .setTitle("⚙ Cấu hình Thử nghiệm")
                .setItems(options, (dialog, which) -> {
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
                            if (!engine.isSimulationMode()) {
                                engine.getCache().clear();
                            }
                            Toast.makeText(getContext(), "Chế độ mô phỏng mạng: " + (engine.isSimulationMode() ? "BẬT" : "TẮT"), Toast.LENGTH_SHORT).show();
                            refreshUI();
                            break;
                        case 4:
                            logger.resetSessionStats();
                            engine.resetSession();
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
                                runner.setupKeyBenchmarkMatrix(5);
                                runner.startBatch();
                                Toast.makeText(getContext(), "Đang chạy tự động 16 cấu hình ma trận...", Toast.LENGTH_LONG).show();
                            }
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
                    engine.setCurrentPolicy(policies[which]);
                    Toast.makeText(getContext(), "Đã chọn: " + policies[which].code, Toast.LENGTH_SHORT).show();
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

