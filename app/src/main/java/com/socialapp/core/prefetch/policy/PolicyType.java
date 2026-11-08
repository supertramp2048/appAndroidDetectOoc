package com.socialapp.core.prefetch.policy;

import androidx.annotation.NonNull;

/**
 * Enumeration of the 6 formal prefetch policies (P0 through P5).
 */
public enum PolicyType {

    P0_NO_PREFETCH("P0", "P0: Không nạp trước (Baseline)", "Vô hiệu hoá hoàn toàn nạp ngầm để đo độ trễ đối chứng."),
    P1_PREFETCH_ALL("P1", "P1: Nạp tất cả (Greedy All)", "Nạp toàn bộ các màn hình kế cận có xác suất > 0 để đo trần lãng phí."),
    P2_MOST_FREQUENT("P2", "P2: Thói quen phổ biến nhất (Top-1)", "Nạp 1 màn hình có xác suất chuyển tiếp cao nhất (Home -> PostDetail -> Comments)."),
    P3_MARKOV_ORDER_1("P3", "P3: Markov Bậc 1 (Ngưỡng θ)", "Mô hình xác suất Markov 1 bước P(S_t+1 | S_t) >= theta."),
    P4_MARKOV_ORDER_2("P4", "P4: Markov Bậc 2 (Chuỗi 2 bước + Backoff)", "Mô hình xác suất có điều kiện trên 2 bước liên tiếp P(S_t+1 | S_t, S_t-1)."),
    P5_NETWORK_ADAPTIVE("P5", "P5: Thích ứng chi phí mạng (Pareto Utility)", "Tối ưu hóa kinh tế mạng: Chỉ nạp khi Lợi ích thời gian > Tổn thất băng thông.");

    private final String code;
    private final String displayName;
    private final String description;

    PolicyType(String code, String displayName, String description) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
    }

    @NonNull
    public String getCode() {
        return code;
    }

    @NonNull
    public String getDisplayName() {
        return displayName;
    }

    @NonNull
    public String getDescription() {
        return description;
    }
}
