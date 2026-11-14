package com.socialapp.core.prefetch;

/**
 * Phân loại kết quả nạp màn hình:
 * - FULL_HIT: Đã tải trước hoàn chỉnh trong Cache, hiển thị ngay (độ trễ = 0ms).
 * - LATE_HIT: Đang được tải trước trong nền, người dùng bấm vào thì đợi phần còn lại, không gửi trùng request.
 * - MISS: Chưa được tải trước, phải tải từ đầu qua mạng.
 */
public enum HitType {
    FULL_HIT("FULL_HIT", "Đã sẵn sàng (Full Hit)"),
    LATE_HIT("LATE_HIT", "Đang tải dở (Late Hit)"),
    MISS("MISS", "Chưa tải (Miss)");

    private final String code;
    private final String description;

    HitType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}

