package com.socialapp.core.prefetch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bộ nhớ tạm (In-Memory Cache) lưu trữ các gói dữ liệu màn hình đã được tải trước.
 * Có cơ chế theo dõi xem mục đã tải có được người dùng bấm vào dùng hay chưa để tính byte lãng phí (wasted bytes).
 */
public class PrefetchCache {

    public static class CacheEntry {
        public final ScreenId screenId;
        public final Object data;
        public final long sizeBytes;
        public final long timestamp;
        private boolean isUsed;

        public CacheEntry(@NonNull ScreenId screenId, @Nullable Object data, long sizeBytes) {
            this.screenId = screenId;
            this.data = data;
            this.sizeBytes = sizeBytes;
            this.timestamp = System.currentTimeMillis();
            this.isUsed = false;
        }

        public boolean isUsed() {
            return isUsed;
        }

        public void markUsed() {
            this.isUsed = true;
        }
    }

    private final Map<ScreenId, CacheEntry> cacheMap = new ConcurrentHashMap<>();

    public void put(@NonNull ScreenId screenId, @Nullable Object data, long sizeBytes) {
        cacheMap.put(screenId, new CacheEntry(screenId, data, sizeBytes));
    }

    @Nullable
    public CacheEntry get(@NonNull ScreenId screenId) {
        return cacheMap.get(screenId);
    }

    public boolean contains(@NonNull ScreenId screenId) {
        return cacheMap.containsKey(screenId);
    }

    public void markUsed(@NonNull ScreenId screenId) {
        CacheEntry entry = cacheMap.get(screenId);
        if (entry != null) {
            entry.markUsed();
        }
    }

    /**
     * Thu hồi và tính tổng số byte của các mục tải trước nhưng người dùng không dùng tới (wasted bytes).
     * Thường gọi khi kết thúc phiên hoặc khi chuyển sang bước mới và xóa cache cũ.
     */
    public long flushUnusedWastedBytes() {
        long wasted = 0;
        for (CacheEntry entry : cacheMap.values()) {
            if (!entry.isUsed()) {
                wasted += entry.sizeBytes;
            }
        }
        cacheMap.clear();
        return wasted;
    }

    public void clear() {
        cacheMap.clear();
    }

    public int size() {
        return cacheMap.size();
    }
}

