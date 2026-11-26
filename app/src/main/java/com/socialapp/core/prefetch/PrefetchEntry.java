package com.socialapp.core.prefetch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An individual in-memory cache entry holding prefetched application data.
 *
 * [Ý 1 - SWR Upgrade]: Hỗ trợ cơ chế Stale-While-Revalidate (SWR) với 2 mốc thời gian:
 * - softExpiresAtMs (Hạn mềm): Dưới mốc này dữ liệu là FRESH (Tươi hoàn toàn).
 * - expiresAtMs (Hạn cứng): Giữa [softExpiresAtMs, expiresAtMs) dữ liệu ở trạng thái STALE
 *   (vẫn trả ngay lập tức 0ms cho UI, đồng thời kích hoạt 1 request làm mới dưới nền).
 *   Từ expiresAtMs trở đi dữ liệu mới thực sự EXPIRED (bị loại bỏ).
 *
 * @param <T> The data type (e.g., PostResource, List<CommentResource>, etc.)
 */
public class PrefetchEntry<T> {

    public enum FreshnessState {
        FRESH,            // Dữ liệu còn tươi mới (< softExpiresAtMs)
        STALE_REVALIDATE, // Hết hạn mềm nhưng còn hạn cứng -> trả ngay 0ms + làm mới ngầm
        EXPIRED           // Quá hạn cứng (>= expiresAtMs) -> không dùng nữa
    }

    @NonNull
    private final String key;

    @NonNull
    private final String screenName;

    @Nullable
    private final T data;

    private final long payloadSizeBytes;
    private final long createdAtMs;
    private final long softExpiresAtMs;
    private final long expiresAtMs;

    private volatile boolean isUsed;
    private volatile long lastAccessedAtMs;
    private final AtomicBoolean isRevalidating = new AtomicBoolean(false);

    public PrefetchEntry(
            @NonNull String key,
            @NonNull String screenName,
            @Nullable T data,
            long payloadSizeBytes,
            long ttlMs) {
        this(
                key,
                screenName,
                data,
                payloadSizeBytes,
                Math.min(PrefetchConfig.DEFAULT_SOFT_TTL_MS, Math.max(1L, ttlMs / 2)),
                ttlMs
        );
    }

    public PrefetchEntry(
            @NonNull String key,
            @NonNull String screenName,
            @Nullable T data,
            long payloadSizeBytes,
            long softTtlMs,
            long hardTtlMs) {
        this.key = key;
        this.screenName = screenName;
        this.data = data;
        this.payloadSizeBytes = Math.max(0, payloadSizeBytes);
        this.createdAtMs = System.currentTimeMillis();
        long validHardTtl = Math.max(1L, hardTtlMs);
        long validSoftTtl = Math.max(1L, Math.min(softTtlMs, validHardTtl));
        this.softExpiresAtMs = this.createdAtMs + validSoftTtl;
        this.expiresAtMs = this.createdAtMs + validHardTtl;
        this.isUsed = false;
        this.lastAccessedAtMs = this.createdAtMs;
    }

    @NonNull
    public String getKey() {
        return key;
    }

    @NonNull
    public String getScreenName() {
        return screenName;
    }

    @Nullable
    public T getData() {
        return data;
    }

    public long getPayloadSizeBytes() {
        return payloadSizeBytes;
    }

    public double getPayloadSizeKb() {
        return payloadSizeBytes / 1024.0;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }

    public long getExpiresAtMs() {
        return expiresAtMs;
    }

    public long getSoftExpiresAtMs() {
        return softExpiresAtMs;
    }

    /**
     * [Ý 1 - SWR] Kiểm tra trạng thái độ tươi 3 mức (FRESH / STALE_REVALIDATE / EXPIRED).
     */
    @NonNull
    public FreshnessState getFreshnessState() {
        long now = System.currentTimeMillis();
        if (now >= expiresAtMs) {
            return FreshnessState.EXPIRED;
        }
        if (now >= softExpiresAtMs) {
            return FreshnessState.STALE_REVALIDATE;
        }
        return FreshnessState.FRESH;
    }

    /**
     * [Ý 1 - SWR] Dữ liệu đã qua hạn mềm (softTtl) nhưng vẫn còn trong hạn cứng (hardTtl).
     * Khi true: trả ngay bản ghi này (0ms) và kích hoạt làm mới dưới nền.
     */
    public boolean isStaleButUsable() {
        return getFreshnessState() == FreshnessState.STALE_REVALIDATE;
    }

    /**
     * Đánh dấu bắt đầu tác vụ làm mới ngầm (chỉ cho phép 1 luồng revalidate chạy cùng lúc).
     */
    public boolean tryMarkRevalidating() {
        return isRevalidating.compareAndSet(false, true);
    }

    public void clearRevalidating() {
        isRevalidating.set(false);
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAtMs;
    }

    public boolean isExpiredAt(long nowMs) {
        return nowMs >= expiresAtMs;
    }

    public boolean isUsed() {
        return isUsed;
    }

    public void markUsed() {
        this.isUsed = true;
        this.lastAccessedAtMs = System.currentTimeMillis();
    }

    public long getLastAccessedAtMs() {
        return lastAccessedAtMs;
    }

    public void updateAccessTime() {
        this.lastAccessedAtMs = System.currentTimeMillis();
    }

    public long getRemainingTtlMs() {
        long remaining = expiresAtMs - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    @NonNull
    @Override
    public String toString() {
        return "PrefetchEntry{" +
                "key='" + key + '\'' +
                ", screenName='" + screenName + '\'' +
                ", payloadSizeBytes=" + payloadSizeBytes +
                ", isUsed=" + isUsed +
                ", remainingTtl=" + getRemainingTtlMs() + "ms" +
                '}';
    }
}
