package com.socialapp.core.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tiện ích xử lý và định dạng thời gian tập trung cho toàn bộ ứng dụng.
 * Hỗ trợ parse định dạng ISO 8601 UTC từ Laravel Backend và chuyển đổi sang giờ địa phương.
 */
public final class DateTimeUtils {

    private static final String ISO_FORMAT_1 = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    private static final String ISO_FORMAT_2 = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private static final String SQL_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final int MAX_EPOCH_CACHE_SIZE = 512;
    private static final long UNPARSEABLE_EPOCH = Long.MIN_VALUE;

    private static final TimeZone UTC_TZ = TimeZone.getTimeZone("UTC");
    private static final Locale VI_LOCALE = new Locale("vi", "VN");

    private static final ConcurrentHashMap<String, Long> PARSED_EPOCH_CACHE = new ConcurrentHashMap<>();

    private static final ThreadLocal<SimpleDateFormat> ISO_MILLIS_PARSER =
            ThreadLocal.withInitial(() -> createUtcFormat(ISO_FORMAT_1));
    private static final ThreadLocal<SimpleDateFormat> ISO_SEC_PARSER =
            ThreadLocal.withInitial(() -> createUtcFormat(ISO_FORMAT_2));
    private static final ThreadLocal<SimpleDateFormat> SQL_PARSER =
            ThreadLocal.withInitial(() -> createUtcFormat(SQL_FORMAT));
    private static final ThreadLocal<SimpleDateFormat> VI_TIME_FORMATTER =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("HH:mm", VI_LOCALE));
    private static final ThreadLocal<SimpleDateFormat> VI_DATE_FORMATTER =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("dd/MM/yyyy", VI_LOCALE));
    private static final ThreadLocal<SimpleDateFormat> VI_FULL_FORMATTER =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("HH:mm • dd/MM/yyyy", VI_LOCALE));

    private DateTimeUtils() {
        // Utility class
    }

    @NonNull
    private static SimpleDateFormat createUtcFormat(@NonNull String pattern) {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
        sdf.setTimeZone(UTC_TZ);
        return sdf;
    }

    /**
     * Parse chuỗi thời gian bất kỳ (ISO 8601 UTC, SQL Timestamp) sang java.util.Date.
     */
    @Nullable
    public static Date parseDate(@Nullable String rawDate) {
        long epochMs = parseEpochMillis(rawDate);
        return epochMs == UNPARSEABLE_EPOCH ? null : new Date(epochMs);
    }

    private static long parseEpochMillis(@Nullable String rawDate) {
        if (rawDate == null || rawDate.trim().isEmpty()) {
            return UNPARSEABLE_EPOCH;
        }

        String clean = rawDate.trim();
        Long cached = PARSED_EPOCH_CACHE.get(clean);
        if (cached != null) {
            return cached;
        }

        String normalized = clean;
        // Xử lý chuỗi có microsecond (ví dụ: 2026-10-11T10:20:25.000000Z -> 2026-10-11T10:20:25.000Z)
        int zIndex = normalized.lastIndexOf('Z');
        if (zIndex >= 0 && zIndex == normalized.length() - 1) {
            int dotIndex = normalized.indexOf('.');
            if (dotIndex >= 0 && dotIndex < zIndex) {
                int millisLen = zIndex - dotIndex - 1;
                if (millisLen > 3) {
                    normalized = normalized.substring(0, dotIndex + 4) + "Z";
                }
            }
        }

        Date parsed = parseWithFastDispatch(normalized);
        long result = parsed != null ? parsed.getTime() : UNPARSEABLE_EPOCH;
        if (PARSED_EPOCH_CACHE.size() >= MAX_EPOCH_CACHE_SIZE) {
            PARSED_EPOCH_CACHE.clear();
        }
        PARSED_EPOCH_CACHE.putIfAbsent(clean, result);
        return result;
    }

    @Nullable
    private static Date parseWithFastDispatch(@NonNull String clean) {
        boolean hasT = clean.indexOf('T') >= 0;
        if (hasT) {
            if (clean.indexOf('.') >= 0) {
                Date d = tryParse(ISO_MILLIS_PARSER.get(), clean);
                if (d != null) return d;
                d = tryParse(ISO_SEC_PARSER.get(), clean);
                if (d != null) return d;
                return tryParse(SQL_PARSER.get(), clean);
            } else {
                Date d = tryParse(ISO_SEC_PARSER.get(), clean);
                if (d != null) return d;
                d = tryParse(ISO_MILLIS_PARSER.get(), clean);
                if (d != null) return d;
                return tryParse(SQL_PARSER.get(), clean);
            }
        } else {
            Date d = tryParse(SQL_PARSER.get(), clean);
            if (d != null) return d;
            d = tryParse(ISO_MILLIS_PARSER.get(), clean);
            if (d != null) return d;
            return tryParse(ISO_SEC_PARSER.get(), clean);
        }
    }

    @Nullable
    private static Date tryParse(@Nullable SimpleDateFormat sdf, @NonNull String text) {
        if (sdf == null) return null;
        try {
            return sdf.parse(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Định dạng thời gian tương đối cho News Feed và Bình luận ("Vừa xong", "15 phút trước", "2 giờ trước", "Hôm qua").
     */
    public static String formatRelativeTime(@Nullable String rawDate) {
        if (rawDate == null || rawDate.trim().isEmpty()) {
            return "";
        }

        long parsedEpochMs = parseEpochMillis(rawDate);
        if (parsedEpochMs == UNPARSEABLE_EPOCH) {
            return rawDate; // Fallback an toàn nếu không parse được
        }

        long now = System.currentTimeMillis();
        long diffMillis = now - parsedEpochMs;

        if (diffMillis < 0) {
            // Chênh lệch đồng hồ client-server nhẹ
            return "Vừa xong";
        }

        long seconds = diffMillis / 1000;
        if (seconds < 60) {
            return "Vừa xong";
        }

        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + " phút trước";
        }

        long hours = minutes / 60;
        if (hours < 24) {
            return hours + " giờ trước";
        }

        long days = hours / 24;
        if (days == 1) {
            SimpleDateFormat timeFormat = VI_TIME_FORMATTER.get();
            if (timeFormat != null) {
                timeFormat.setTimeZone(TimeZone.getDefault());
                return "Hôm qua lúc " + timeFormat.format(new Date(parsedEpochMs));
            }
        }

        if (days < 7) {
            return days + " ngày trước";
        }

        // Quá 7 ngày hiển thị ngày/tháng/năm
        SimpleDateFormat dateFormat = VI_DATE_FORMATTER.get();
        if (dateFormat != null) {
            dateFormat.setTimeZone(TimeZone.getDefault());
            return dateFormat.format(new Date(parsedEpochMs));
        }
        return rawDate;
    }

    /**
     * Định dạng thời gian đầy đủ, trang trọng cho màn hình chi tiết bài viết ("17:20 • 11/10/2026").
     */
    public static String formatFullDateTime(@Nullable String rawDate) {
        if (rawDate == null || rawDate.trim().isEmpty()) {
            return "";
        }

        long parsedEpochMs = parseEpochMillis(rawDate);
        if (parsedEpochMs == UNPARSEABLE_EPOCH) {
            return rawDate;
        }

        SimpleDateFormat fullFormat = VI_FULL_FORMATTER.get();
        if (fullFormat != null) {
            fullFormat.setTimeZone(TimeZone.getDefault());
            return fullFormat.format(new Date(parsedEpochMs));
        }
        return rawDate;
    }
}
