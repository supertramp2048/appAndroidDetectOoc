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

        // Cố định tốc độ thời gian thực 1.0x để độ trễ đo được phản ánh trung thực 100%
        AppContainer container = MyApplication.getInstance() != null ? MyApplication.getInstance().getAppContainer() : null;
        if (container != null) {
            container.getPrefetchEngine().setTimeScale(1.0f);
        }

        applyExpandState();

        btnToggleExpand.setOnClickListener(v -> {
            isExpanded = !isExpanded;
            applyExpandState();
        });

        findViewById(R.id.headerBar).setOnClickListener(v -> {
            isExpanded = !isExpanded;
            applyExpandState();
        });

        tvConfigBadge.setOnClickListener(v -> showConfigDialog());
        btnConfig.setOnClickListener(v -> showConfigDialog());
        btnCompare.setOnClickListener(v -> exportComparisonFile());
        btnReplayToggle.setOnClickListener(v -> handleReplayToggle());

        refreshUI();
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
        AppContainer container = MyApplication.getInstance() != null ? MyApplication.getInstance().getAppContainer() : null;
        if (container == null) return;

        PrefetchEngine engine = container.getPrefetchEngine();
        MetricsLogger logger = container.getMetricsLogger();
        ReplayEngine replay = container.getReplayEngine();

        // 1. Nhãn cấu hình tối giản: chỉ hiển thị Chính sách · Mạng
        if (replay != null && replay.isRunning()) {
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
            String badge = String.format(Locale.US, "%s · %s",
                    engine.getCurrentPolicy().code,
                    engine.getCurrentNetwork().code
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

        tvStatsSummary.setText(String.format(
                Locale.US,
                "Hit: %.1f%% (%d/%d)  ·  Trễ TB: %d ms  ·  Thừa: %d KB",
                hitRate, hits, total, avgLatency, wastedKb
        ));
    }

    private void handleReplayToggle() {
        AppContainer container = MyApplication.getInstance() != null ? MyApplication.getInstance().getAppContainer() : null;
        if (container == null) return;

        ReplayEngine replay = container.getReplayEngine();
        if (replay.isRunning()) {
            replay.stopReplay();
            Toast.makeText(getContext(), "Đã dừng Replay", Toast.LENGTH_SHORT).show();
            refreshUI();
            return;
        }

        String[] sessionOptions = new String[]{
                "3 phiên (Demo nhanh)",
                "10 phiên (Đánh giá đầy đủ)"
        };
        final int[] counts = new int[]{3, 10};

        new AlertDialog.Builder(getContext())
                .setTitle("Chạy kịch bản tự động")
                .setItems(sessionOptions, (dialog, which) -> {
                    replay.startReplay(counts[which]);
                    refreshUI();
                })
                .setNegativeButton("Đóng", null)
                .show();
    }

    /**
     * Hộp thoại Cài đặt siêu tối giản: chỉ đúng 3 mục thiết yếu (Chính sách, Mạng, Đặt lại).
     */
    private void showConfigDialog() {
        AppContainer container = MyApplication.getInstance() != null ? MyApplication.getInstance().getAppContainer() : null;
        if (container == null) return;

        PrefetchEngine engine = container.getPrefetchEngine();
        MetricsLogger logger = container.getMetricsLogger();

        String[] options = new String[]{
                "Chính sách:  " + engine.getCurrentPolicy().code,
                "Mạng giả lập:  " + engine.getCurrentNetwork().code,
                "Chế độ Mô phỏng mạng:  " + (engine.isSimulationMode() ? "BẬT" : "TẮT"),
                "Đặt lại phiên hiện tại"
        };

        new AlertDialog.Builder(getContext())
                .setTitle("Cài đặt")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showPolicySelectionDialog(engine, logger);
                            break;
                        case 1:
                            showNetworkSelectionDialog(engine, logger);
                            break;
                        case 2:
                            engine.setSimulationMode(!engine.isSimulationMode());
                            if (!engine.isSimulationMode()) {
                                engine.getCache().clear();
                            }
                            Toast.makeText(getContext(), "Chế độ mô phỏng mạng: " + (engine.isSimulationMode() ? "BẬT" : "TẮT"), Toast.LENGTH_SHORT).show();
                            refreshUI();
                            break;
                        case 3:
                            logger.saveCurrentRunSnapshot(
                                    engine.getCurrentPolicy().code,
                                    engine.getCurrentNetwork().code
                            );
                            logger.resetSessionStats();
                            engine.resetSession();
                            Repository.setSessionId(Repository.getSessionId() + 1);
                            Toast.makeText(getContext(), "Đã lưu & đặt lại phiên mới", Toast.LENGTH_SHORT).show();
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
                    if (logger.getTotalSteps() > 0) {
                        logger.saveCurrentRunSnapshot(
                                engine.getCurrentPolicy().code,
                                engine.getCurrentNetwork().code
                        );
                        logger.resetSessionStats();
                        engine.resetSession();
                    }
                    engine.setCurrentPolicy(DEMO_POLICIES[which]);
                    refreshUI();
                    dialog.dismiss();
                })
                .setNegativeButton("Đóng", null)
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

    /**
     * Xuất toàn bộ kết quả các lần chạy ra file CSV bên ngoài (Download/prefetch_comparison.csv)
     * để mở bằng Excel / Python so sánh trực tiếp, không hiện bảng popup trong App.
     */
    private void exportComparisonFile() {
        AppContainer container = MyApplication.getInstance() != null ? MyApplication.getInstance().getAppContainer() : null;
        if (container == null) return;

        PrefetchEngine engine = container.getPrefetchEngine();
        MetricsLogger logger = container.getMetricsLogger();

        if (logger.getTotalSteps() > 0) {
            logger.saveCurrentRunSnapshot(
                    engine.getCurrentPolicy().code,
                    engine.getCurrentNetwork().code
            );
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
