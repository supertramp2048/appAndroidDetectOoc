package com.socialapp.core.prefetch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Callback nhận kết quả từ Repository.load(screen).
 */
public interface ScreenLoadCallback<T> {
    /**
     * Gọi khi nạp dữ liệu thành công.
     *
     * @param hitType   Loại Hit (FULL_HIT, LATE_HIT, MISS)
     * @param latencyMs Độ trễ nạp dữ liệu (ms)
     * @param data      Dữ liệu trả về (có thể ép kiểu về DTO tương ứng)
     */
    void onLoaded(@NonNull HitType hitType, long latencyMs, @Nullable T data);

    /**
     * Gọi khi có lỗi.
     *
     * @param error Ngoại lệ / Lỗi phát sinh
     */
    void onError(@NonNull Throwable error);
}

