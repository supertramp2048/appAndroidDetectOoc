package com.socialapp.core.prefetch;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bộ ghi log thực nghiệm hiệu năng ra file CSV và lưu thống kê trong RAM cho thanh Demo.
 */
public class MetricsLogger {

    private static final String TAG = "MetricsLogger";
    private static final String CSV_HEADER = "session_id,step_index,from_screen,to_screen,hit_type,latency_ms,ui_latency_ms,fetched_bytes,wasted_bytes,policy,network_profile,payload_scale,timestamp\n";

    public interface OnMetricsUpdatedListener {
        void onMetricsUpdated(int totalSteps, int hitCount, int missCount, long lastLatencyMs, long totalWastedBytes);
    }

    private final Context context;
    private final ExecutorService diskExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private File currentLogFile;
    private OnMetricsUpdatedListener listener;

    // Thống kê in-memory phục vụ UI Demo
    private int totalSteps = 0;
    private int hitCount = 0;
    private int missCount = 0;
    private long lastLatencyMs = 0;
    private long totalWastedBytes = 0;

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

    private void initLogFile(@NonNull String configName) {
        File dir = context.getExternalFilesDir(null);
        if (dir == null) {
            dir = context.getFilesDir();
        }
        File metricsDir = new File(dir, "metrics");
        if (!metricsDir.exists()) {
            metricsDir.mkdirs();
        }

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

    public void logTransition(
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
        if (hitType == HitType.FULL_HIT || hitType == HitType.LATE_HIT) {
            hitCount++;
        } else {
            missCount++;
        }
        lastLatencyMs = latencyMs;
        totalWastedBytes += wastedBytes;

        // Báo cho UI Overlay trên main thread
        if (listener != null) {
            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onMetricsUpdated(totalSteps, hitCount, missCount, lastLatencyMs, totalWastedBytes);
                }
            });
        }

        // Ghi xuống file CSV ở background thread
        diskExecutor.execute(() -> {
            if (currentLogFile == null) return;

            String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
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

    @Nullable
    public File getCurrentLogFile() {
        return currentLogFile;
    }

    public void resetSessionStats() {
        totalSteps = 0;
        hitCount = 0;
        missCount = 0;
        lastLatencyMs = 0;
        totalWastedBytes = 0;
        if (listener != null) {
            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onMetricsUpdated(0, 0, 0, 0, 0);
                }
            });
        }
    }

    public int getTotalSteps() {
        return totalSteps;
    }

    public int getHitCount() {
        return hitCount;
    }

    public int getMissCount() {
        return missCount;
    }

    public long getLastLatencyMs() {
        return lastLatencyMs;
    }

    public long getTotalWastedBytes() {
        return totalWastedBytes;
    }
}

