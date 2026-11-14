package com.socialapp.core.prefetch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * An individual in-memory cache entry holding prefetched application data.
 *
 * @param <T> The data type (e.g., PostResource, List<CommentResource>, etc.)
 */
public class PrefetchEntry<T> {

    @NonNull
    private final String key;

    @NonNull
    private final String screenName;

    @Nullable
    private final T data;

    private final long payloadSizeBytes;
    private final long createdAtMs;
    private final long expiresAtMs;

    private volatile boolean isUsed;
    private volatile long lastAccessedAtMs;

    public PrefetchEntry(
            @NonNull String key,
            @NonNull String screenName,
            @Nullable T data,
            long payloadSizeBytes,
            long ttlMs) {
        this.key = key;
        this.screenName = screenName;
        this.data = data;
        this.payloadSizeBytes = Math.max(0, payloadSizeBytes);
        this.createdAtMs = System.currentTimeMillis();
        this.expiresAtMs = this.createdAtMs + Math.max(1L, ttlMs);
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
