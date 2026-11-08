package com.socialapp.core.network.sim;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.socialapp.core.mock.FakeApi;
import com.socialapp.core.prefetch.IPrefetchPolicy;
import com.socialapp.core.prefetch.IPrefetchTelemetryListener;
import com.socialapp.core.prefetch.PrefetchCache;
import com.socialapp.core.prefetch.PrefetchHitType;
import com.socialapp.core.prefetch.PrefetchManager;
import com.socialapp.core.prefetch.PrefetchResult;
import com.socialapp.core.prefetch.PrefetchRuntimeConfig;
import com.socialapp.core.prefetch.policy.PolicyType;
import com.socialapp.databinding.ActivityMockNetworkLabBinding;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * In-App Developer Workbench for Task C1-C4:
 * Covers Network Profiles, Bandwidth Concurrency, Prefetch Policies (P0-P3),
 * Tri-state Acquisition (FULL_HIT, LATE_HIT, MISS), and Wasted Bytes Monitoring.
 */
public class MockNetworkLabActivity extends AppCompatActivity implements IPrefetchTelemetryListener {

    private ActivityMockNetworkLabBinding binding;
    private MockNetworkEngine engine;
    private PrefetchManager prefetchManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMockNetworkLabBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        engine = MockNetworkEngine.getInstance();
        prefetchManager = PrefetchManager.getInstance();

        setupToolbar();
        setupInitialControls();
        setupListeners();
        setupPrefetchLab();
    }

    @Override
    protected void onResume() {
        super.onResume();
        prefetchManager.addTelemetryListener(this);
        updatePrefetchDashboardUI();
    }

    @Override
    protected void onPause() {
        super.onPause();
        prefetchManager.removeTelemetryListener(this);
    }

    private void setupToolbar() {
        binding.toolbarNetworkLab.setNavigationOnClickListener(v -> finish());
    }

    private void setupInitialControls() {
        // Init Switch
        binding.switchEnableSimulation.setChecked(engine.isSimulationEnabled());

        // Init Profile Radio
        NetworkProfile currentProfile = engine.getCurrentProfile();
        if (currentProfile == NetworkProfile.WIFI) {
            binding.radioProfileWifi.setChecked(true);
        } else if (currentProfile == NetworkProfile.LTE_4G) {
            binding.radioProfileLte.setChecked(true);
        } else if (currentProfile == NetworkProfile.NETWORK_3G) {
            binding.radioProfile3g.setChecked(true);
        } else if (currentProfile == NetworkProfile.EDGE_2G) {
            binding.radioProfileEdge.setChecked(true);
        }

        // Init Weight Radio
        PayloadWeight currentWeight = engine.getCurrentWeight();
        if (currentWeight == PayloadWeight.LIGHT_0_5) {
            binding.radioWeightLight.setChecked(true);
        } else if (currentWeight == PayloadWeight.NORMAL_1_0) {
            binding.radioWeightNormal.setChecked(true);
        } else if (currentWeight == PayloadWeight.HEAVY_2_0) {
            binding.radioWeightHeavy.setChecked(true);
        }

        updateNetworkStatusText("SẴN SÀNG", false, "Chưa có thao tác kiểm tra nào");
    }

    private void setupListeners() {
        // Toggle simulation
        binding.switchEnableSimulation.setOnCheckedChangeListener((buttonView, isChecked) -> {
            engine.setSimulationEnabled(isChecked);
            Toast.makeText(this, isChecked ? "Đã bật giả lập độ trễ mạng" : "Đã tắt giả lập mạng (0ms)", Toast.LENGTH_SHORT).show();
        });

        // Profile Selection
        binding.radioGroupProfiles.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == binding.radioProfileWifi.getId()) {
                engine.setProfile(NetworkProfile.WIFI);
            } else if (checkedId == binding.radioProfileLte.getId()) {
                engine.setProfile(NetworkProfile.LTE_4G);
            } else if (checkedId == binding.radioProfile3g.getId()) {
                engine.setProfile(NetworkProfile.NETWORK_3G);
            } else if (checkedId == binding.radioProfileEdge.getId()) {
                engine.setProfile(NetworkProfile.EDGE_2G);
            }
            Toast.makeText(this, "Profile: " + engine.getCurrentProfile().getDisplayName(), Toast.LENGTH_SHORT).show();
        });

        // Weight Selection
        binding.radioGroupWeights.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == binding.radioWeightLight.getId()) {
                engine.setWeight(PayloadWeight.LIGHT_0_5);
            } else if (checkedId == binding.radioWeightNormal.getId()) {
                engine.setWeight(PayloadWeight.NORMAL_1_0);
            } else if (checkedId == binding.radioWeightHeavy.getId()) {
                engine.setWeight(PayloadWeight.HEAVY_2_0);
            }
            Toast.makeText(this, "Hệ số tải: " + engine.getCurrentWeight().getDisplayName(), Toast.LENGTH_SHORT).show();
        });

        // Single Screen Test Buttons
        binding.btnTestHome.setOnClickListener(v -> executeSingleTest(
                ScreenPayloadConfig.SCREEN_HOME,
                () -> "Home Feed (10 Categories): " + FakeApi.getSampleCategories().size() + " mục"
        ));

        binding.btnTestPostDetail.setOnClickListener(v -> executeSingleTest(
                ScreenPayloadConfig.SCREEN_POST_DETAIL,
                () -> "PostDetail: " + FakeApi.getSamplePosts().get(0).title
        ));

        binding.btnTestComments.setOnClickListener(v -> executeSingleTest(
                ScreenPayloadConfig.SCREEN_COMMENTS,
                () -> "Comments: 25 bình luận thảo luận"
        ));

        binding.btnTestProfile.setOnClickListener(v -> executeSingleTest(
                ScreenPayloadConfig.SCREEN_PROFILE,
                () -> "Profile: Ban Biên Tập (101 bài viết, 1500 theo dõi)"
        ));

        // Concurrent Test (Task C2 Bandwidth Contention)
        binding.btnTestConcurrent.setOnClickListener(v -> executeConcurrentTest());
    }

    // --- Task C3 & C4 Prefetch Lab Setup ---

    private void setupPrefetchLab() {
        // Init policy radio from PrefetchRuntimeConfig
        PolicyType current = PrefetchRuntimeConfig.getInstance().getActivePolicyType();
        if (current == PolicyType.P0_NO_PREFETCH) {
            binding.radioPolicyP0.setChecked(true);
        } else if (current == PolicyType.P1_PREFETCH_ALL) {
            binding.radioPolicyP1.setChecked(true);
        } else if (current == PolicyType.P2_MOST_FREQUENT) {
            binding.radioPolicyP2.setChecked(true);
        } else if (current == PolicyType.P3_MARKOV_ORDER_1) {
            binding.radioPolicyP3.setChecked(true);
        } else if (current == PolicyType.P4_MARKOV_ORDER_2) {
            binding.radioPolicyP4.setChecked(true);
        } else if (current == PolicyType.P5_NETWORK_ADAPTIVE) {
            binding.radioPolicyP5.setChecked(true);
        }

        binding.radioGroupPolicies.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == binding.radioPolicyP0.getId()) {
                PrefetchRuntimeConfig.getInstance().setPolicy(PolicyType.P0_NO_PREFETCH);
            } else if (checkedId == binding.radioPolicyP1.getId()) {
                PrefetchRuntimeConfig.getInstance().setPolicy(PolicyType.P1_PREFETCH_ALL);
            } else if (checkedId == binding.radioPolicyP2.getId()) {
                PrefetchRuntimeConfig.getInstance().setPolicy(PolicyType.P2_MOST_FREQUENT);
            } else if (checkedId == binding.radioPolicyP3.getId()) {
                PrefetchRuntimeConfig.getInstance().setPolicy(PolicyType.P3_MARKOV_ORDER_1);
            } else if (checkedId == binding.radioPolicyP4.getId()) {
                PrefetchRuntimeConfig.getInstance().setPolicy(PolicyType.P4_MARKOV_ORDER_2);
            } else if (checkedId == binding.radioPolicyP5.getId()) {
                PrefetchRuntimeConfig.getInstance().setPolicy(PolicyType.P5_NETWORK_ADAPTIVE);
            }
            binding.textPrefetchPolicyLabel.setText("[" + prefetchManager.getPolicy().getPolicyId() + "]");
            Toast.makeText(this, "Đã đổi chính sách: " + prefetchManager.getPolicy().getDisplayName(), Toast.LENGTH_SHORT).show();
            updatePrefetchDashboardUI();
        });

        // 1. Visit HOME (Proactive prefetch trigger)
        binding.btnSimVisitHome.setOnClickListener(v -> {
            prefetchManager.onScreenVisited(ScreenPayloadConfig.SCREEN_HOME);
            binding.textPrefetchBadge.setText("ĐANG NẠP NGẦM...");
            binding.textPrefetchBadge.setBackgroundColor(0xFFFEF3C7); // amber
            binding.textPrefetchBadge.setTextColor(0xFF92400E);
            binding.textPrefetchResultTitle.setText("Đang âm thầm nạp POSTDETAIL");
            binding.textPrefetchTimeSaved.setText("Tác vụ đang bay ngầm dưới nền...");
            binding.textPrefetchLogSummary.setText("Nhật ký: Đã mở HOME. Theo chính sách " + prefetchManager.getPolicy().getPolicyId()
                    + ", hệ thống đang nạp trước POSTDETAIL (250KB). Bấm nút (2) ngay để thử LATE_HIT, hoặc chờ 1-2s để thử FULL_HIT!");
            updatePrefetchDashboardUI();
        });

        // 2. Acquire POSTDETAIL (Test Full Hit vs Late Hit vs Miss)
        binding.btnSimAcquirePost.setOnClickListener(v -> {
            long reqStart = System.currentTimeMillis();
            binding.textPrefetchResultTitle.setText("Đang yêu cầu PostDetail...");

            CompletableFuture<PrefetchResult<Object>> future = prefetchManager.acquire(
                    ScreenPayloadConfig.SCREEN_POST_DETAIL,
                    "default_" + ScreenPayloadConfig.SCREEN_POST_DETAIL,
                    () -> FakeApi.getSamplePosts().get(0)
            );

            future.thenAccept(res -> runOnUiThread(() -> {
                displayAcquireResult(res);
                updatePrefetchDashboardUI();
            }));
        });

        // 3. Cleanup Cache (Measure Wasted Bytes)
        binding.btnSimCleanupWasted.setOnClickListener(v -> {
            int evicted = prefetchManager.getCache().cleanupExpired();
            Toast.makeText(this, "Đã dọn dẹp " + evicted + " mục hết hạn", Toast.LENGTH_SHORT).show();
            updatePrefetchDashboardUI();
        });

        // 4. Reset stats
        binding.btnSimResetStats.setOnClickListener(v -> {
            prefetchManager.getCache().resetStats();
            prefetchManager.cancelAllInFlight();
            binding.textPrefetchBadge.setText("SẴN SÀNG");
            binding.textPrefetchBadge.setBackgroundColor(0xFFF1F5F9);
            binding.textPrefetchBadge.setTextColor(0xFF475569);
            binding.textPrefetchResultTitle.setText("Thời gian chờ: 0 ms");
            binding.textPrefetchTimeSaved.setText("Thời gian tiết kiệm: 0 ms");
            updatePrefetchDashboardUI();
            Toast.makeText(this, "Đã đặt lại toàn bộ thống kê", Toast.LENGTH_SHORT).show();
        });
    }

    @SuppressLint("SetTextI18n")
    private void displayAcquireResult(@NonNull PrefetchResult<?> res) {
        PrefetchHitType hit = res.getHitType();

        if (hit == PrefetchHitType.FULL_HIT) {
            binding.textPrefetchBadge.setText("FULL_HIT (0ms)");
            binding.textPrefetchBadge.setBackgroundColor(0xFFDCFCE7); // green-100
            binding.textPrefetchBadge.setTextColor(0xFF166534); // green-800
            binding.textPrefetchResultTitle.setText("Thời gian chờ: 0 ms (Tức thì!)");
            binding.textPrefetchTimeSaved.setText("Tiết kiệm trọn vẹn: " + res.getSavedMs() + " ms");
            binding.textPrefetchLogSummary.setText("Nhật ký: TRÚNG ĐÍCH 100%! Dữ liệu đã có sẵn trong RAM. Người dùng không phải chờ mạng!");
        } else if (hit == PrefetchHitType.LATE_HIT) {
            binding.textPrefetchBadge.setText("LATE_HIT (Bắt nối)");
            binding.textPrefetchBadge.setBackgroundColor(0xFFFEF3C7); // amber-100
            binding.textPrefetchBadge.setTextColor(0xFF92400E); // amber-800
            binding.textPrefetchResultTitle.setText("Thời gian chờ: " + res.getWaitMs() + " ms");
            binding.textPrefetchTimeSaved.setText("Đã tiết kiệm được: " + res.getSavedMs() + " ms");
            binding.textPrefetchLogSummary.setText("Nhật ký: ĐÓN ĐẦU THÀNH CÔNG! Request đang nạp dở, ứng dụng bắt nối ngay vào luồng, không nạp lại từ đầu!");
        } else {
            binding.textPrefetchBadge.setText("MISS (Hụt)");
            binding.textPrefetchBadge.setBackgroundColor(0xFFFEE2E2); // red-100
            binding.textPrefetchBadge.setTextColor(0xFF991B1B); // red-800
            binding.textPrefetchResultTitle.setText("Thời gian chờ: " + res.getWaitMs() + " ms");
            binding.textPrefetchTimeSaved.setText("Thời gian tiết kiệm: 0 ms");
            binding.textPrefetchLogSummary.setText("Nhật ký: HỤT PREFETCH! Màn hình này chưa được nạp trước nên phải chờ nạp mạng bình thường.");
        }
    }

    @SuppressLint("SetTextI18n")
    private void updatePrefetchDashboardUI() {
        runOnUiThread(() -> {
            PrefetchCache cache = prefetchManager.getCache();
            double hitRatePct = cache.getHitRate() * 100.0;

            binding.textPrefetchHitBreakdown.setText(String.format(
                    Locale.US,
                    "Tỷ lệ Trúng: %.1f%% | Full: %d | Late: %d | Miss: %d",
                    hitRatePct,
                    cache.getFullHitCount(),
                    cache.getLateHitCount(),
                    cache.getMissCount()
            ));

            binding.textPrefetchWastedInfo.setText(String.format(
                    Locale.US,
                    "Băng thông lãng phí: %.1f KB | Hữu ích: %.1f KB",
                    cache.getWastedKb(),
                    cache.getUsefulKb()
            ));

            binding.textPrefetchCacheStatus.setText(String.format(
                    Locale.US,
                    "RAM Cache: %d mục | Đang bay dở (In-flight): %d",
                    cache.size(),
                    prefetchManager.getInFlightCount()
            ));
        });
    }

    // --- Telemetry Callbacks ---

    @Override
    public void onPrefetchTriggered(@NonNull String s, @NonNull String k, long b) {
        updatePrefetchDashboardUI();
    }

    @Override
    public void onPrefetchCompleted(@NonNull String s, @NonNull String k, long d, long b) {
        updatePrefetchDashboardUI();
    }

    @Override
    public void onPrefetchFailed(@NonNull String s, @NonNull String k, @NonNull Throwable e) {
        updatePrefetchDashboardUI();
    }

    @Override
    public void onAcquireResult(@NonNull PrefetchResult<?> result) {
        updatePrefetchDashboardUI();
    }

    @Override
    public void onCacheEvicted(@NonNull String cacheKey, long wastedBytes, boolean wasExpired, boolean wasUsed) {
        updatePrefetchDashboardUI();
    }

    // --- Task C1 & C2 Network Tests ---

    @SuppressLint("SetTextI18n")
    private <T> void executeSingleTest(@NonNull String screenName, @NonNull Supplier<T> dataSupplier) {
        String reqId = "test-" + UUID.randomUUID().toString().substring(0, 6);
        updateNetworkStatusText("ĐANG NẠP MẠNG...", true, "Đang tính toán trễ: " + screenName + " (" + engine.getCurrentProfile().getDisplayName() + ")");
        binding.textSimulatedScreen.setText("Màn hình mô phỏng: " + screenName);

        CompletableFuture<SimulatedNetworkResponse<T>> future =
                engine.executeWithDataSupplier(reqId, screenName, true, dataSupplier);

        future.thenAccept(response -> runOnUiThread(() -> {
            updateNetworkStatusText("HOÀN TẤT", false, String.valueOf(response.getData()));
            binding.textLatencyResult.setText("Độ trễ: " + response.getTotalLatencyMs() + " ms");
            binding.textBreakdown.setText(String.format(
                    Locale.US,
                    "Phân rã: RTT %dms + Truyền tải %dms",
                    response.getRttMs(),
                    response.getTransferTimeMs()
            ));
            binding.textPayloadInfo.setText(String.format(
                    Locale.US,
                    "Gói tin payload: %.1f KB",
                    response.getPayloadSizeKb()
            ));
            binding.textBandwidthConcurrency.setText(String.format(
                    Locale.US,
                    "Băng thông chia sẻ: %.1f KB/s (N = %d)",
                    response.getEffectiveBandwidthKbps(),
                    response.getConcurrentRequestsAtFinish()
            ));
        })).exceptionally(throwable -> {
            runOnUiThread(() -> updateNetworkStatusText("LỖI", false, "Thất bại: " + throwable.getMessage()));
            return null;
        });
    }

    @SuppressLint("SetTextI18n")
    private void executeConcurrentTest() {
        updateNetworkStatusText("ĐANG CHẠY 2 REQUEST ĐỒNG THỜI...", true, "Băng thông đang được chia đều (BW / 2)...");
        binding.textSimulatedScreen.setText("Màn hình: HOME (120KB) & POST_DETAIL (250KB)");

        String id1 = "c1-" + UUID.randomUUID().toString().substring(0, 4);
        String id2 = "c2-" + UUID.randomUUID().toString().substring(0, 4);

        CompletableFuture<SimulatedNetworkResponse<String>> f1 = engine.executeWithDataSupplier(
                id1, ScreenPayloadConfig.SCREEN_HOME, true, () -> "Request 1 (Home - 120KB)");

        CompletableFuture<SimulatedNetworkResponse<String>> f2 = engine.executeWithDataSupplier(
                id2, ScreenPayloadConfig.SCREEN_POST_DETAIL, true, () -> "Request 2 (PostDetail - 250KB)");

        CompletableFuture.allOf(f1, f2).thenAccept(v -> {
            SimulatedNetworkResponse<String> r1 = f1.join();
            SimulatedNetworkResponse<String> r2 = f2.join();

            runOnUiThread(() -> {
                updateNetworkStatusText("HOÀN TẤT CẢ 2", false, "Cả 2 request đã hoàn tất và cùng chia sẻ băng thông!");
                binding.textLatencyResult.setText(String.format(Locale.US, "Req 1: %dms | Req 2: %dms", r1.getTotalLatencyMs(), r2.getTotalLatencyMs()));
                binding.textBreakdown.setText(String.format(
                        Locale.US,
                        "R1 (120KB): RTT %dms + Tx %dms | R2 (250KB): RTT %dms + Tx %dms",
                        r1.getRttMs(), r1.getTransferTimeMs(),
                        r2.getRttMs(), r2.getTransferTimeMs()
                ));
                binding.textPayloadInfo.setText("Tổng payload 2 request: " + (r1.getPayloadSizeKb() + r2.getPayloadSizeKb()) + " KB");
                binding.textBandwidthConcurrency.setText(String.format(
                        Locale.US,
                        "Chia đôi băng thông: R1: %.1f KB/s | R2: %.1f KB/s (N=2)",
                        r1.getEffectiveBandwidthKbps(),
                        r2.getEffectiveBandwidthKbps()
                ));
            });
        });
    }

    private void updateNetworkStatusText(@NonNull String status, boolean isLoading, @NonNull String summary) {
        binding.textStatusBadge.setText(status);
        binding.progressLoading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.textLogSummary.setText("Trạng thái: " + summary);
        if ("HOÀN TẤT".equals(status) || "HOÀN TẤT CẢ 2".equals(status)) {
            binding.textStatusBadge.setBackgroundColor(0xFFDCFCE7); // green-100
            binding.textStatusBadge.setTextColor(0xFF166534); // green-800
        } else if ("ĐANG NẠP MẠNG...".equals(status) || "ĐANG CHẠY 2 REQUEST ĐỒNG THỜI...".equals(status)) {
            binding.textStatusBadge.setBackgroundColor(0xFFFEF3C7); // amber-100
            binding.textStatusBadge.setTextColor(0xFF92400E); // amber-800
        } else {
            binding.textStatusBadge.setBackgroundColor(0xFFF1F5F9);
            binding.textStatusBadge.setTextColor(0xFF475569);
        }
    }
}
