package com.socialapp.core.prefetch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Result returned to caller (Repository / ViewModel) after an acquire request.
 * Contains both payload and telemetry breakdown (saved time, hit type).
 *
 * @param <T> Payload type
 */
public class PrefetchResult<T> {

    @Nullable
    private final T data;

    @NonNull
    private final PrefetchHitType hitType;

    @NonNull
    private final String screenName;

    @NonNull
    private final String cacheKey;

    private final long waitMs;
    private final long savedMs;
    private final long payloadSizeBytes;

    public PrefetchResult(
            @Nullable T data,
            @NonNull PrefetchHitType hitType,
            @NonNull String screenName,
            @NonNull String cacheKey,
            long waitMs,
            long savedMs,
            long payloadSizeBytes) {
        this.data = data;
        this.hitType = hitType;
        this.screenName = screenName;
        this.cacheKey = cacheKey;
        this.waitMs = Math.max(0, waitMs);
        this.savedMs = Math.max(0, savedMs);
        this.payloadSizeBytes = Math.max(0, payloadSizeBytes);
    }

    @Nullable
    public T getData() {
        return data;
    }

    @NonNull
    public PrefetchHitType getHitType() {
        return hitType;
    }

    @NonNull
    public String getScreenName() {
        return screenName;
    }

    @NonNull
    public String getCacheKey() {
        return cacheKey;
    }

    public long getWaitMs() {
        return waitMs;
    }

    public long getSavedMs() {
        return savedMs;
    }

    public long getPayloadSizeBytes() {
        return payloadSizeBytes;
    }

    public double getPayloadSizeKb() {
        return payloadSizeBytes / 1024.0;
    }

    public boolean isHit() {
        return hitType == PrefetchHitType.FULL_HIT || hitType == PrefetchHitType.LATE_HIT;
    }

    @NonNull
    @Override
    public String toString() {
        return "PrefetchResult{" +
                "hitType=" + hitType +
                ", screenName='" + screenName + '\'' +
                ", waitMs=" + waitMs +
                ", savedMs=" + savedMs +
                ", payloadSizeKb=" + String.format("%.1f", getPayloadSizeKb()) +
                '}';
    }
}
