package com.socialapp.core.util;

import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Tiện ích xử lý và định dạng thời gian tập trung cho toàn bộ ứng dụng.
 * Hỗ trợ parse định dạng ISO 8601 UTC từ Laravel Backend và chuyển đổi sang giờ địa phương.
 */
public final class DateTimeUtils {

    private static final String ISO_FORMAT_1 = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    private static final String ISO_FORMAT_2 = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private static final String SQL_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private DateTimeUtils() {
        // Utility class
    }

    /**
     * Parse chuỗi thời gian bất kỳ (ISO 8601 UTC, SQL Timestamp) sang java.util.Date.
     */
    @Nullable
    public static Date parseDate(@Nullable String rawDate) {
        if (rawDate == null || rawDate.trim().isEmpty()) {
            return null;
        }

        String clean = rawDate.trim();

        // Xử lý chuỗi có microsecond (ví dụ: 2026-10-11T10:20:25.000000Z -> 2026-10-11T10:20:25.000Z)
        if (clean.contains(".") && clean.endsWith("Z")) {
            int dotIndex = clean.indexOf('.');
            int zIndex = clean.lastIndexOf('Z');
            if (dotIndex < zIndex) {
                String millisPart = clean.substring(dotIndex + 1, zIndex);
                if (millisPart.length() > 3) {
                    millisPart = millisPart.substring(0, 3);
                }
                clean = clean.substring(0, dotIndex + 1) + millisPart + "Z";
            }
        }

        // Thử parse theo ISO có millisecond
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(ISO_FORMAT_1, Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf.parse(clean);
        } catch (Exception ignored) {
        }

        // Thử parse theo ISO không có millisecond
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(ISO_FORMAT_2, Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf.parse(clean);
        } catch (Exception ignored) {
        }

        // Thử parse theo SQL datetime
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(SQL_FORMAT, Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf.parse(clean);
        } catch (Exception ignored) {
        }

        return null;
    }

    /**
     * Định dạng thời gian tương đối cho News Feed và Bình luận ("Vừa xong", "15 phút trước", "2 giờ trước", "Hôm qua").
     */
    public static String formatRelativeTime(@Nullable String rawDate) {
        if (rawDate == null || rawDate.trim().isEmpty()) {
            return "";
        }

        Date date = parseDate(rawDate);
        if (date == null) {
            return rawDate; // Fallback an toàn nếu không parse được
        }

        long now = System.currentTimeMillis();
        long diffMillis = now - date.getTime();

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
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", new Locale("vi", "VN"));
            timeFormat.setTimeZone(TimeZone.getDefault());
            return "Hôm qua lúc " + timeFormat.format(date);
        }

        if (days < 7) {
            return days + " ngày trước";
        }

        // Quá 7 ngày hiển thị ngày/tháng/năm
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", new Locale("vi", "VN"));
        dateFormat.setTimeZone(TimeZone.getDefault());
        return dateFormat.format(date);
    }

    /**
     * Định dạng thời gian đầy đủ, trang trọng cho màn hình chi tiết bài viết ("17:20 • 11/10/2026").
     */
    public static String formatFullDateTime(@Nullable String rawDate) {
        if (rawDate == null || rawDate.trim().isEmpty()) {
            return "";
        }

        Date date = parseDate(rawDate);
        if (date == null) {
            return rawDate;
        }

        SimpleDateFormat fullFormat = new SimpleDateFormat("HH:mm • dd/MM/yyyy", new Locale("vi", "VN"));
        fullFormat.setTimeZone(TimeZone.getDefault());
        return fullFormat.format(date);
    }
}
