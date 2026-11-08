package com.socialapp.core.prefetch;

import androidx.annotation.NonNull;

/**
 * Tri-state outcome when an application screen attempts to acquire data.
 */
public enum PrefetchHitType {

    /**
     * Data was already fully preloaded in RAM and is still valid (TTL unexpired).
     * Yields instant UI transition with T_Data = 0ms.
     */
    FULL_HIT("FULL_HIT", "Trúng đích 100% (Sẵn trong RAM)"),

    /**
     * Prefetch request was already in-flight across the network when the user opened the screen.
     * The consumer attaches to the running future, saving the time already spent in-flight.
     */
    LATE_HIT("LATE_HIT", "Đón đầu nạp dở (Bắt nối luồng đang chạy)"),

    /**
     * Data was neither cached nor in-flight; standard foreground network load was required.
     */
    MISS("MISS", "Hụt (Chưa nạp trước)");

    private final String id;
    private final String description;

    PrefetchHitType(String id, String description) {
        this.id = id;
        this.description = description;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getDescription() {
        return description;
    }
}
