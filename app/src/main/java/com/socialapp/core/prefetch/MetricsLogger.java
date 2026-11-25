package com.socialapp.core.prefetch;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bộ ghi log thực nghiệm hiệu năng ra file CSV chi tiết từng bước
 * và xuất file tổng hợp (prefetch_comparison.csv) ra bên ngoài để đối chiếu bằng Excel/Python.
 */
public class MetricsLogger {

    private static final String TAG = "MetricsLogger";
    private static final String CSV_HEADER = "session_id,step_index,from_screen,to_screen,hit_type,latency_ms,ui_latency_ms,fetched_bytes,wasted_bytes,policy,network_profile,payload_scale,timestamp\n";
    private static final String COMPARISON_HEADER = "timestamp,policy,network,total_steps,full_hits,late_hits,misses,hit_rate_pct,avg_latency_ms,total_wasted_kb\n";
    public static final String COMPARISON_FILE_NAME = "prefetch_comparison.csv";

    public static class RunSummary {
        public final String timestamp;
        public final String policy;
        public final String network;
        public final int totalSteps;
        public final int fullHits;
        public final int lateHits;
        public final int misses;
        public final double hitRatePct;
        public final long avgLatencyMs;
        public final long wastedKb;

        public RunSummary(
                String timestamp,
                String policy,
                String network,
                int totalSteps,
                int fullHits,
                int lateHits,
                int misses,
                long avgLatencyMs,
                long wastedKb
        ) {
            this.timestamp = timestamp;
            this.policy = policy;
            this.network = network;
            this.totalSteps = totalSteps;
            this.fullHits = fullHits;
            this.lateHits = lateHits;
            this.misses = misses;
            this.hitRatePct = totalSteps > 0 ? ((fullHits + lateHits) * 100.0 / totalSteps) : 0.0;
            this.avgLatencyMs = avgLatencyMs;
            this.wastedKb = wastedKb;
        }
    }

    public interface OnMetricsUpdatedListener {
        void onMetricsUpdated(int totalSteps, int hitCount, int missCount, long lastLatencyMs, long totalWastedBytes);
    }

    private final Context context;
    private final ExecutorService diskExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private File currentLogFile;
    private OnMetricsUpdatedListener listener;

    // Thống kê in-memory phiên hiện tại
    private int totalSteps = 0;
    private int fullHitCount = 0;
    private int lateHitCount = 0;
    private int missCount = 0;
    private long lastLatencyMs = 0;
    private long totalLatencyMs = 0;
    private long totalWastedBytes = 0;

    // Danh sách các lần chạy để xuất ra file prefetch_comparison.csv
    private final List<RunSummary> comparisonHistory = new ArrayList<>();

    public MetricsLogger(@NonNull Context context) {
        this.context = context.getApplicationContext();
        initLogFile("default");
    }

    public synchronized void setConfigName(@NonNull String configName) {
        initLogFile(configName);
    }

    public void setOnMetricsUpdatedListener(@Nullable OnMetricsUpdatedListener listener) {
        this.listener = listener;
    }

    private File getMetricsDir() {
        File dir = context.getExternalFilesDir(null);
        if (dir == null) {
            dir = context.getFilesDir();
        }
        File metricsDir = new File(dir, "metrics");
        if (!metricsDir.exists()) {
            metricsDir.mkdirs();
        }
        return metricsDir;
    }

    private void initLogFile(@NonNull String configName) {
        File metricsDir = getMetricsDir();
        currentLogFile = new File(metricsDir, "metrics_" + configName + ".csv");
        if (!currentLogFile.exists()) {
            diskExecutor.execute(() -> {
                try (FileWriter writer = new FileWriter(currentLogFile, false)) {
                    writer.write(CSV_HEADER);
                    writer.flush();
                } catch (IOException e) {
                    Log.e(TAG, "Lỗi tạo file CSV header: ", e);
                }
            });
        }
    }

    public synchronized void logTransition(
            int sessionId,
            int stepIndex,
            @Nullable ScreenId fromScreen,
            @NonNull ScreenId toScreen,
            @NonNull HitType hitType,
            long latencyMs,
            long uiLatencyMs,
            long fetchedBytes,
            long wastedBytes,
            @NonNull String policy,
            @NonNull String networkProfile,
            float payloadScale
    ) {
        totalSteps++;
        if (hitType == HitType.FULL_HIT) {
            fullHitCount++;
        } else if (hitType == HitType.LATE_HIT) {
            lateHitCount++;
        } else {
            missCount++;
        }
        lastLatencyMs = latencyMs;
        totalLatencyMs += latencyMs;
        totalWastedBytes += wastedBytes;

        final int snapSteps = totalSteps;
        final int snapHits = fullHitCount + lateHitCount;
        final int snapMiss = missCount;
        final long snapLastLat = lastLatencyMs;
        final long snapWasted = totalWastedBytes;

        if (listener != null) {
            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onMetricsUpdated(snapSteps, snapHits, snapMiss, snapLastLat, snapWasted);
                }
            });
        }

        diskExecutor.execute(() -> {
            if (currentLogFile == null) return;

            String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
            String from = fromScreen != null ? fromScreen.getId() : "NONE";
            String line = String.format(Locale.US, "%d,%d,%s,%s,%s,%d,%d,%d,%d,%s,%s,%.2f,%s\n",
                    sessionId,
                    stepIndex,
                    from,
                    toScreen.getId(),
                    hitType.getCode(),
                    latencyMs,
                    uiLatencyMs,
                    fetchedBytes,
                    wastedBytes,
                    policy,
                    networkProfile,
                    payloadScale,
                    timeStamp
            );

            try (FileWriter writer = new FileWriter(currentLogFile, true)) {
                writer.write(line);
                writer.flush();
            } catch (IOException e) {
                Log.e(TAG, "Lỗi ghi log CSV: ", e);
            }
        });
    }

    /**
     * Lưu tóm tắt phiên hiện tại vào danh sách đối chiếu (ghi đè nếu trùng cặp Policy + Network).
     */
    public synchronized boolean saveCurrentRunSnapshot(@NonNull String policyCode, @NonNull String networkCode) {
        if (totalSteps <= 0) {
            return false;
        }
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        RunSummary summary = new RunSummary(
                ts,
                policyCode,
                networkCode,
                totalSteps,
                fullHitCount,
                lateHitCount,
                missCount,
                getAvgLatencyMs(),
                totalWastedBytes / 1024
        );
        for (int i = 0; i < comparisonHistory.size(); i++) {
            RunSummary existing = comparisonHistory.get(i);
            if (existing.policy.equals(policyCode) && existing.network.equals(networkCode)) {
                comparisonHistory.set(i, summary);
                return true;
            }
        }
        comparisonHistory.add(summary);
        return true;
    }

    /**
     * Xuất toàn bộ bảng tổng hợp các lần chạy ra file CSV bên ngoài:
     * 1) /sdcard/Android/data/com.socialapp/files/metrics/prefetch_comparison.csv
     * 2) /sdcard/Download/prefetch_comparison.csv (trên Android 10+ qua MediaStore)
     *
     * @return Đường dẫn hiển thị ngắn gọn cho người dùng, hoặc null nếu chưa có dữ liệu.
     */
    @Nullable
    public synchronized String exportComparisonCsv() {
        if (comparisonHistory.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder(COMPARISON_HEADER);
        for (RunSummary r : comparisonHistory) {
            sb.append(String.format(
                    Locale.US,
                    "%s,%s,%s,%d,%d,%d,%d,%.1f,%d,%d\n",
                    r.timestamp,
                    r.policy,
                    r.network,
                    r.totalSteps,
                    r.fullHits,
                    r.lateHits,
                    r.misses,
                    r.hitRatePct,
                    r.avgLatencyMs,
                    r.wastedKb
            ));
        }
        final String csvContent = sb.toString();

        // In kèm ra Logcat (filter: PREFETCH_CSV) để xem ngay trong Android Studio nếu cần
        Log.i("PREFETCH_CSV", "\n" + csvContent);

        // 1. Luôn ghi vào thư mục metrics của App (dễ lấy qua lay_ket_qua_so_sanh.bat)
        File appCsv = new File(getMetricsDir(), COMPARISON_FILE_NAME);
        try (FileWriter writer = new FileWriter(appCsv, false)) {
            writer.write(csvContent);
            writer.flush();
        } catch (IOException e) {
            Log.e(TAG, "Lỗi ghi app metrics CSV: ", e);
        }

        // 2. Đồng thời xuất bản sao ra thư mục Download công khai của thiết bị (Android 10+)
        boolean exportedToDownloads = writeToPublicDownloads(csvContent);
        if (exportedToDownloads) {
            return "Download/" + COMPARISON_FILE_NAME;
        }
        return appCsv.getAbsolutePath();
    }

    private boolean writeToPublicDownloads(@NonNull String csvContent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false;
        }
        try {
            ContentResolver resolver = context.getContentResolver();
            Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);

            // Xóa file cũ trùng tên để luôn ghi đè 1 file sạch duy nhất
            String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=?";
            String[] selectionArgs = new String[]{COMPARISON_FILE_NAME};
            try (Cursor cursor = resolver.query(collection, new String[]{MediaStore.MediaColumns._ID}, selection, selectionArgs, null)) {
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        long id = cursor.getLong(0);
                        Uri existingUri = Uri.withAppendedPath(collection, String.valueOf(id));
                        resolver.delete(existingUri, null, null);
                    }
                }
            }

            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, COMPARISON_FILE_NAME);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

            Uri uri = resolver.insert(collection, values);
            if (uri == null) return false;

            try (OutputStream os = resolver.openOutputStream(uri)) {
                if (os == null) return false;
                os.write(csvContent.getBytes(StandardCharsets.UTF_8));
                os.flush();
                return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "Không thể ghi vào thư mục Download công khai: " + e.getMessage());
            return false;
        }
    }

    @NonNull
    public synchronized List<RunSummary> getComparisonHistory() {
        return Collections.unmodifiableList(new ArrayList<>(comparisonHistory));
    }

    public synchronized void clearComparisonHistory() {
        comparisonHistory.clear();
    }

    @Nullable
    public File getCurrentLogFile() {
        return currentLogFile;
    }

    public synchronized void resetSessionStats() {
        totalSteps = 0;
        fullHitCount = 0;
        lateHitCount = 0;
        missCount = 0;
        lastLatencyMs = 0;
        totalLatencyMs = 0;
        totalWastedBytes = 0;
        if (listener != null) {
            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onMetricsUpdated(0, 0, 0, 0, 0);
                }
            });
        }
    }

    public synchronized int getTotalSteps() {
        return totalSteps;
    }

    public synchronized int getHitCount() {
        return fullHitCount + lateHitCount;
    }

    public synchronized int getFullHitCount() {
        return fullHitCount;
    }

    public synchronized int getLateHitCount() {
        return lateHitCount;
    }

    public synchronized int getMissCount() {
        return missCount;
    }

    public synchronized long getLastLatencyMs() {
        return lastLatencyMs;
    }

    public synchronized long getAvgLatencyMs() {
        return totalSteps > 0 ? (totalLatencyMs / totalSteps) : 0;
    }

    public synchronized long getTotalWastedBytes() {
        return totalWastedBytes;
    }
}
