package com.socialapp.core.prefetch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Định danh tất cả các màn hình trong ứng dụng và dung lượng dữ liệu cơ sở (KB/Bytes).
 */
public enum ScreenId {
    FEED_NEWEST("FEED_NEWEST", "Trang chủ - Mới nhất", "home?tab=newest", 120 * 1024),
    FEED_HOT("FEED_HOT", "Trang chủ - Nổi bật", "home?tab=hot", 120 * 1024),
    FEED_FOLLOWING("FEED_FOLLOWING", "Trang chủ - Đang theo dõi", "home?tab=following", 100 * 1024),
    SEARCH("SEARCH", "Tìm kiếm", "search", 60 * 1024),
    POST_DETAIL("POST_DETAIL", "Chi tiết bài viết", "post/1", 180 * 1024),
    COMMENTS("COMMENTS", "Bình luận bài viết", "post/1/comments", 90 * 1024),
    PROFILE("PROFILE", "Trang cá nhân", "profile/1", 110 * 1024),
    FACT_CHECK("FACT_CHECK", "Kiểm chứng tin giả", "factcheck", 150 * 1024),
    SETTINGS("SETTINGS", "Cài đặt tài khoản", "settings", 40 * 1024);

    private final String id;
    private final String displayName;
    private final String defaultRoute;
    private final long baseSizeBytes;

    ScreenId(String id, String displayName, String defaultRoute, long baseSizeBytes) {
        this.id = id;
        this.displayName = displayName;
        this.defaultRoute = defaultRoute;
        this.baseSizeBytes = baseSizeBytes;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDefaultRoute() {
        return defaultRoute;
    }

    public long getBaseSizeBytes() {
        return baseSizeBytes;
    }

    @NonNull
    public static ScreenId fromRoute(@Nullable String route) {
        if (route == null || route.isEmpty()) {
            return FEED_NEWEST;
        }

        String lower = route.toLowerCase().trim();
        if (lower.contains("tab=hot")) return FEED_HOT;
        if (lower.contains("tab=following")) return FEED_FOLLOWING;
        if (lower.contains("comments")) return COMMENTS;
        if (lower.startsWith("post/")) return POST_DETAIL;
        if (lower.startsWith("profile")) return PROFILE;
        if (lower.startsWith("factcheck")) return FACT_CHECK;
        if (lower.startsWith("search")) return SEARCH;
        if (lower.startsWith("settings")) return SETTINGS;

        return FEED_NEWEST;
    }
}

