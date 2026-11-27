package com.socialapp.core.prefetch;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.atomic.AtomicLong;

/**
 * [Ý 1 - Mảnh ghép 5] Cảm biến trạng thái sóng/mạng thật & Bộ quản lý ngân sách 4G (Radio-Aware Budget Controller).
 *
 * Nhiệm vụ:
 * 1. Đọc trực tiếp ConnectivityManager & NetworkCapabilities của hệ điều hành Android:
 *    - Phân biệt Wi-Fi không giới hạn (Unmetered) với mạng di động 3G/4G/5G tính phí (Metered Cellular).
 *    - Phát hiện chế độ Tiết kiệm dữ liệu (Data Saver / Restrict Background Data) của người dùng.
 *    - Ước lượng hồ sơ mạng thực tế (WIFI, LTE, 3G, EDGE) dựa trên loại sóng và băng thông downstream.
 * 2. Quản lý ngân sách dung lượng tải trước trên mạng di động (Cellular Byte Budget):
 *    - Trên Wi-Fi: cho phép prefetch API và preload ảnh Glide thoải mái.
 *    - Trên 4G/Cellular: giới hạn tổng số byte prefetch trong mỗi phiên (mặc định 2 MB/phiên) và
 *      tăng hệ số phạt chi phí (CostFactor) trong hàm hữu ích Pareto để tránh lãng phí data 4G.
 */
public class RadioNetworkMonitor {

    private static volatile RadioNetworkMonitor instance;

    @NonNull
    public static RadioNetworkMonitor getInstance() {
        if (instance == null) {
            synchronized (RadioNetworkMonitor.class) {
                if (instance == null) {
                    instance = new RadioNetworkMonitor();
                }
            }
        }
        return instance;
    }

    // Tổng số byte đã dùng cho prefetch trên mạng di động (4G/3G) trong phiên hiện tại
    private final AtomicLong cellularPrefetchBytesSpent = new AtomicLong(0);

    // Hạn mức ngân sách prefetch tối đa trên mạng 4G mỗi phiên (mặc định 2 MB)
    private volatile long cellularBudgetBytes = PrefetchConfig.DEFAULT_CELLULAR_PREFETCH_BUDGET_BYTES;

    /**
     * Kiểm tra thiết bị hiện có kết nối mạng khả dụng hay không.
     */
    public boolean isOnline(@Nullable Context context) {
        if (context == null) return true;
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return true;
        Network activeNetwork = cm.getActiveNetwork();
        if (activeNetwork == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    /**
     * Kiểm tra mạng hiện tại có phải mạng không giới hạn dung lượng (Wi-Fi / Unmetered) hay không.
     */
    public boolean isUnmeteredNetwork(@Nullable Context context) {
        if (context == null) return true;
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return true;
        Network activeNetwork = cm.getActiveNetwork();
        if (activeNetwork == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
        if (caps == null) return false;
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
    }

    /**
     * Kiểm tra người dùng có đang bật chế độ Tiết kiệm dữ liệu (Data Saver) của Android hay không.
     */
    public boolean isDataSaverEnabled(@Nullable Context context) {
        if (context == null) return false;
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && cm.isActiveNetworkMetered()) {
            return cm.getRestrictBackgroundStatus() == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED;
        }
        return false;
    }

    /**
     * Suy ra hồ sơ mạng thực tế (WIFI / LTE / 3G / EDGE) từ NetworkCapabilities của máy.
     */
    @NonNull
    public PrefetchEngine.NetworkProfile detectLiveNetworkProfile(@Nullable Context context) {
        if (context == null) {
            return PrefetchEngine.NetworkProfile.LTE;
        }
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return PrefetchEngine.NetworkProfile.LTE;
        }
        Network activeNetwork = cm.getActiveNetwork();
        if (activeNetwork == null) {
            return PrefetchEngine.NetworkProfile.EDGE;
        }
        NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
        if (caps == null) {
            return PrefetchEngine.NetworkProfile.LTE;
        }

        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return PrefetchEngine.NetworkProfile.WIFI;
        }

        int downKbps = caps.getLinkDownstreamBandwidthKbps();
        if (downKbps <= 0) {
            return PrefetchEngine.NetworkProfile.LTE;
        } else if (downKbps >= 8_000) {
            return PrefetchEngine.NetworkProfile.LTE;
        } else if (downKbps >= 800) {
            return PrefetchEngine.NetworkProfile.G3;
        } else {
            return PrefetchEngine.NetworkProfile.EDGE;
        }
    }

    /**
     * Hệ số phạt chi phí mạng (CostFactor) dùng cho hàm hữu ích Pareto P5:
     * - Wi-Fi (Unmetered): 0.15 (chi phí rất thấp, ưu tiên tải trước để giảm độ trễ)
     * - 4G/LTE (Metered): 1.00 (cân bằng giữa độ trễ và dung lượng)
     * - 3G: 3.50 (mạng chậm, siết chặt ngưỡng xác suất)
     * - EDGE/2G: 12.0 (rất nghẽn, gần như chỉ tải khi chắc chắn)
     */
    public double getCostFactorForProfile(@NonNull PrefetchEngine.NetworkProfile profile) {
        switch (profile) {
            case WIFI:
                return 0.15;
            case LTE:
                return 1.00;
            case G3:
                return 3.50;
            case EDGE:
            default:
                return 12.00;
        }
    }

    /**
     * Kiểm tra và trừ ngân sách prefetch cho một request có kích thước dự kiến (expectedBytes).
     * - Ở chế độ mô phỏng (isSimulationMode = true): luôn cho phép để chạy thực nghiệm chuẩn theo policy P0-P5.
     * - Ở chế độ mạng thật:
     *   + Nếu mất mạng hoặc đang bật Data Saver trên mạng 4G -> Từ chối.
     *   + Nếu đang dùng Wi-Fi (Unmetered) -> Cho phép không giới hạn.
     *   + Nếu đang dùng 4G/3G (Metered) -> Kiểm tra xem còn đủ ngân sách 4G trong phiên hay không.
     */
    public boolean tryConsumePrefetchBudget(@Nullable Context context, long expectedBytes, boolean isSimulationMode) {
        if (isSimulationMode) {
            return true;
        }
        if (!isOnline(context)) {
            return false;
        }
        if (isDataSaverEnabled(context)) {
            return false;
        }
        if (isUnmeteredNetwork(context)) {
            return true;
        }

        // Đang dùng mạng di động tính phí (4G/3G): kiểm tra hạn mức ngân sách phiên
        long cost = Math.max(0L, expectedBytes);
        while (true) {
            long currentSpent = cellularPrefetchBytesSpent.get();
            if (currentSpent + cost > cellularBudgetBytes) {
                return false; // Vượt ngân sách 4G cho phép -> chặn prefetch để tiết kiệm data
            }
            if (cellularPrefetchBytesSpent.compareAndSet(currentSpent, currentSpent + cost)) {
                return true;
            }
        }
    }

    /**
     * Quyết định có cho phép preload ảnh Glide (thumbnail/avatar) của bài viết kế tiếp hay không.
     * Chỉ preload ảnh khi:
     * - Đang ở Wi-Fi, HOẶC
     * - Đang ở mạng 4G khỏe (LTE) và ngân sách 4G trong phiên vẫn còn trống > 30%.
     */
    public boolean shouldAllowImagePreload(@Nullable Context context, boolean isSimulationMode) {
        if (isSimulationMode) {
            return true;
        }
        if (!isOnline(context) || isDataSaverEnabled(context)) {
            return false;
        }
        if (isUnmeteredNetwork(context)) {
            return true;
        }
        PrefetchEngine.NetworkProfile liveProfile = detectLiveNetworkProfile(context);
        long remainingBudget = getRemainingCellularBudgetBytes();
        return liveProfile == PrefetchEngine.NetworkProfile.LTE
                && remainingBudget >= (cellularBudgetBytes * 0.30);
    }

    public long getCellularPrefetchBytesSpent() {
        return cellularPrefetchBytesSpent.get();
    }

    public long getRemainingCellularBudgetBytes() {
        return Math.max(0L, cellularBudgetBytes - cellularPrefetchBytesSpent.get());
    }

    public void setCellularBudgetBytes(long budgetBytes) {
        this.cellularBudgetBytes = Math.max(64 * 1024L, budgetBytes);
    }

    public void resetSessionBudget() {
        this.cellularPrefetchBytesSpent.set(0);
    }
}
