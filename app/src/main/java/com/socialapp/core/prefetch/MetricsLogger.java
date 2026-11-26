package com.socialapp.core.prefetch;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.socialapp.core.telemetry.DataLoadEvent;
import com.socialapp.core.telemetry.EventSource;
import com.socialapp.core.telemetry.LoadContext;
import com.socialapp.core.telemetry.NavigationEvent;
import com.socialapp.core.telemetry.TelemetryCsvWriter;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Bộ ghi log thực nghiệm hiệu năng ra file CSV và lưu thống kê trong RAM cho thanh Demo.
 */
public class MetricsLogger {

    private static final String TAG = "MetricsLogger";
    private static final int SCHEMA_VERSION = 2;

    private enum Column {
        SCHEMA_VERSION, ROW_TYPE, RUN_ID, SESSION_ID, REPLAY_SESSION_ID, LOAD_ID, NAVIGATION_ID, LOAD_INDEX,
        SCREEN, HIT_TYPE, LOAD_LATENCY_MS, LOAD_MODE, SIMULATED_FOREGROUND_BYTES,
        SIMULATED_WASTED_PREFETCH_BYTES, CANCELLED_PREFETCH_COUNT, POLICY, NETWORK_PROFILE, PAYLOAD_SCALE,
        RUN_STATUS, RUN_VALID, CONFIRMED_STEPS, NAVIGATION_EVENTS
    }

    private static final String CSV_HEADER = csvHeader();
    private static final String NAVIGATION_FILE = "navigation_v1.csv";
    private static final String NAVIGATION_HEADER = "schema_version,navigation_id,source,run_id,session_id,"
            + "replay_session_id,sequence,from_screen,to_screen,timestamp_elapsed_ms\n";

    public interface OnMetricsUpdatedListener {
        void onMetricsUpdated(int totalSteps, int hitCount, int missCount, long lastLatencyMs, long totalWastedBytes);
    }

    static final class LoadTarget {
        final Supplier<File> output;
        final TelemetryCsvWriter.Stream stream;
        final UUID replayRunId;
        final Float timeScale;
        TelemetryCsvWriter.Stream navigation;
        TelemetryCsvWriter.Stream p5;
        TelemetryCsvWriter.Terminal terminal;
        boolean sealed;
        int fullHits;
        int lateHits;
        int misses;
        long latencySumMs;
        Long simulatedWastedBytes;

        LoadTarget(Supplier<File> output, UUID replayRunId, @Nullable Float timeScale) {
            this.output = output;
            String header = timeScale == null ? CSV_HEADER : CSV_HEADER.trim() + ",time_scale\n";
            this.stream = new TelemetryCsvWriter.Stream(output, header);
            this.replayRunId = replayRunId;
            this.timeScale = timeScale;
        }

        File file() { return output.get(); }

        String formatRow(String[] row) {
            String line = String.join(",", row);
            if (timeScale != null) {
                line += "RUN_END".equals(row[Column.ROW_TYPE.ordinal()]) ? "," + timeScale : ",";
            }
            return line + "\n";
        }
    }

    private final Supplier<File> metricsDir;
    private volatile File resolvedDirectory;
    private final TelemetryCsvWriter writer;
    private final TelemetryCsvWriter.Stream liveNavigation;
    private final Consumer<Runnable> postToMain;
    private volatile LoadTarget currentTarget;
    private volatile OnMetricsUpdatedListener listener;

    // Thống kê in-memory phục vụ UI Demo
    private int totalSteps = 0;
    private int hitCount = 0;
    private int missCount = 0;
    private long lastLatencyMs = 0;
    private long totalWastedBytes = 0;

    public MetricsLogger(@NonNull Context context) {
        this(() -> metricsDirectory(context.getApplicationContext()), Executors.newSingleThreadExecutor(),
                new Handler(Looper.getMainLooper())::post, 512);
    }

    MetricsLogger(File metricsDir, Executor diskExecutor, Consumer<Runnable> postToMain) {
        this(metricsDir, diskExecutor, postToMain, 512);
    }

    MetricsLogger(File metricsDir, Executor executor, Consumer<Runnable> postToMain, int capacity) {
        this(() -> metricsDir, executor, postToMain, capacity);
    }

    private MetricsLogger(Supplier<File> metricsDir, Executor executor,
                          Consumer<Runnable> postToMain, int capacity) {
        this.metricsDir = metricsDir;
        writer = new TelemetryCsvWriter(executor, capacity, message -> Log.w(TAG, message));
        liveNavigation = newNavigationStream();
        this.postToMain = postToMain;
        setConfigName("default");
    }

    public void setConfigName(@NonNull String configName) {
        setConfigName(configName, null);
    }

    boolean setConfigName(@NonNull String configName, @Nullable UUID replayRunId) {
        return setConfigName(configName, replayRunId, null);
    }

    synchronized boolean setConfigName(@NonNull String configName, @Nullable UUID replayRunId, @Nullable Float timeScale) {
        String fileName = metricsFileName(configName);
        LoadTarget target = new LoadTarget(outputFile(fileName), replayRunId, timeScale);
        if (replayRunId != null) {
            target.terminal = writer.reserveTerminal(target.stream);
            if (target.terminal == null) return false;
            // The file is shared, but loss counters belong only to this run.
            target.navigation = newNavigationStream();
            target.p5 = new TelemetryCsvWriter.Stream(outputFile(P5Experiment.FILE_NAME), P5Experiment.HEADER);
        }
        currentTarget = target;
        clearStats();
        return true;
    }

    public TelemetryCsvWriter getWriter() { return writer; }

    Consumer<String> captureP5Sink(UUID runId) {
        LoadTarget target = captureLoadTarget(runId);
        if (target == null) throw new IllegalStateException("No metrics target for P5 run");
        return row -> writer.offer(target.p5, row);
    }

    private TelemetryCsvWriter.Stream newNavigationStream() {
        return new TelemetryCsvWriter.Stream(outputFile(NAVIGATION_FILE), NAVIGATION_HEADER);
    }

    public synchronized void recordNavigation(NavigationEvent event) {
        TelemetryCsvWriter.Stream stream = liveNavigation;
        if (event.source == EventSource.REPLAY) {
            LoadTarget target = captureLoadTarget(event.runId);
            if (target == null || target.sealed) return;
            stream = target.navigation;
        }
        writer.offer(stream, String.join(",", "1", event.navigationId.toString(), event.source.name(),
                event.runId.toString(), event.sessionId.toString(),
                event.replaySessionId == null ? "" : event.replaySessionId.toString(),
                Long.toString(event.sequence), event.fromScreen == null ? "" : event.fromScreen.getId(),
                event.toScreen.getId(), Long.toString(event.timestampMs)) + "\n");
    }

    public Supplier<File> outputFile(String fileName) {
        return () -> {
            File directory = resolvedDirectory;
            if (directory == null) {
                directory = metricsDir.get();
                resolvedDirectory = directory;
            }
            return new File(directory, fileName);
        };
    }

    public void setOnMetricsUpdatedListener(@Nullable OnMetricsUpdatedListener listener) {
        this.listener = listener;
    }

    private static File metricsDirectory(Context context) {
        File dir = context.getExternalFilesDir(null);
        if (dir == null) {
            dir = context.getFilesDir();
        }
        return new File(dir, "metrics");
    }

    @Nullable
    LoadTarget captureLoadTarget(@Nullable UUID replayRunId) {
        LoadTarget target = currentTarget;
        return Objects.equals(target.replayRunId, replayRunId) ? target : null;
    }

    static String metricsFileName(String configName) {
        return "metrics_v2_" + configName + ".csv";
    }

    synchronized boolean logLoad(LoadTarget target, boolean currentSession, int loadIndex, DataLoadEvent event,
                                 HitType hitType, @Nullable Long simulatedBytes, String policy, String network,
                                 float payloadScale) {
        if (target.sealed || event.outcome != DataLoadEvent.Outcome.SUCCESS) return false;
        if (target.replayRunId != null) {
            switch (hitType) {
                case FULL_HIT: target.fullHits++; break;
                case LATE_HIT: target.lateHits++; break;
                case MISS: target.misses++; break;
            }
            target.latencySumMs += event.loadLatencyMs;
        }
        boolean active = currentSession && target == currentTarget;
        if (active) {
            totalSteps++;
            if (hitType == HitType.FULL_HIT || hitType == HitType.LATE_HIT) {
                hitCount++;
            } else {
                missCount++;
            }
            lastLatencyMs = event.loadLatencyMs;
            notifyStats(target);
        }

        LoadContext load = event.context;
        String[] row = newRow("FOREGROUND_LOAD", load.runId, load.sessionId, load.replaySessionId);
        put(row, Column.LOAD_INDEX, loadIndex);
        put(row, Column.LOAD_ID, load.loadId);
        put(row, Column.NAVIGATION_ID, load.navigationId);
        put(row, Column.SCREEN, load.screen.getId());
        put(row, Column.HIT_TYPE, hitType.getCode());
        put(row, Column.LOAD_LATENCY_MS, event.loadLatencyMs);
        put(row, Column.LOAD_MODE, simulatedBytes == null ? "REAL" : "SIMULATED");
        put(row, Column.SIMULATED_FOREGROUND_BYTES, simulatedBytes);
        put(row, Column.POLICY, policy);
        put(row, Column.NETWORK_PROFILE, network);
        put(row, Column.PAYLOAD_SCALE, payloadScale);
        appendRow(target, row);
        return active;
    }

    @Nullable
    public BiConsumer<Long, Integer> captureWasteSink(UUID runId, UUID sessionId, @Nullable Integer replaySessionId) {
        LoadTarget target = captureLoadTarget(replaySessionId == null ? null : runId);
        return target == null ? null : (wasted, cancelled) ->
                logCacheWaste(target, runId, sessionId, replaySessionId, wasted, cancelled);
    }

    private synchronized void logCacheWaste(LoadTarget target, UUID runId, UUID sessionId,
                                            Integer replaySessionId, long wasted, int cancelled) {
        if (target.sealed) return;
        if (target.replayRunId != null) {
            target.simulatedWastedBytes = target.simulatedWastedBytes == null ? wasted : target.simulatedWastedBytes + wasted;
        }
        if (target == currentTarget) {
            totalWastedBytes += wasted;
            notifyStats(target);
        }
        String[] row = newRow("CACHE_WASTE", runId, sessionId, replaySessionId);
        put(row, Column.SIMULATED_WASTED_PREFETCH_BYTES, wasted);
        put(row, Column.CANCELLED_PREFETCH_COUNT, cancelled);
        appendRow(target, row);
    }

    @Nullable
    synchronized ReplayResult.Metrics finishReplayRun(UUID runId, String status, boolean valid, int confirmedSteps, int navigationEvents) {
        return finishReplayRun(runId, status, valid, confirmedSteps, navigationEvents, ignored -> {});
    }

    @Nullable
    synchronized ReplayResult.Metrics finishReplayRun(UUID runId, String status, boolean valid, int confirmedSteps,
                                                       int navigationEvents, Consumer<ReplayResult.ExportStatus> callback) {
        LoadTarget target = captureLoadTarget(runId);
        if (target == null || target.sealed) return null;
        target.sealed = true;
        writer.finish(target.terminal, () -> {
            boolean complete = droppedRows(target) == 0 && failedRows(target) == 0;
            String[] row = newRow("RUN_END", runId, null, null);
            put(row, Column.RUN_STATUS, complete ? status : status + "_TELEMETRY_INCOMPLETE");
            put(row, Column.RUN_VALID, valid && complete);
            put(row, Column.CONFIRMED_STEPS, confirmedSteps);
            put(row, Column.NAVIGATION_EVENTS, navigationEvents);
            return target.formatRow(row);
        }, written -> {
            ReplayResult.ExportStatus export = new ReplayResult.ExportStatus(
                    written, droppedRows(target), failedRows(target));
            postToMain.accept(() -> callback.accept(export));
        });
        return new ReplayResult.Metrics(target.fullHits, target.lateHits, target.misses,
                target.latencySumMs, target.simulatedWastedBytes);
    }

    private static long droppedRows(LoadTarget target) {
        return target.stream.dropped() + target.navigation.dropped() + target.p5.dropped();
    }

    private static long failedRows(LoadTarget target) {
        return target.stream.failed() + target.navigation.failed() + target.p5.failed();
    }

    private static String csvHeader() {
        String[] names = new String[Column.values().length];
        for (Column column : Column.values()) names[column.ordinal()] = column.name().toLowerCase(Locale.ROOT);
        return String.join(",", names) + "\n";
    }

    private static String[] newRow(String type, UUID runId, UUID sessionId, Integer replaySessionId) {
        String[] row = new String[Column.values().length];
        Arrays.fill(row, "");
        put(row, Column.SCHEMA_VERSION, SCHEMA_VERSION);
        put(row, Column.ROW_TYPE, type);
        put(row, Column.RUN_ID, runId);
        put(row, Column.SESSION_ID, sessionId);
        put(row, Column.REPLAY_SESSION_ID, replaySessionId);
        return row;
    }

    private static void put(String[] row, Column column, Object value) {
        if (value != null) row[column.ordinal()] = value.toString();
    }

    private void appendRow(LoadTarget target, String[] row) {
        writer.offer(target.stream, target.formatRow(row));
    }

    @Nullable
    public File getCurrentLogFile() {
        return currentTarget.file();
    }

    public synchronized void resetSessionStats() {
        clearStats();
    }

    private void clearStats() {
        totalSteps = 0;
        hitCount = 0;
        missCount = 0;
        lastLatencyMs = 0;
        totalWastedBytes = 0;
        notifyStats(currentTarget);
    }

    private void notifyStats(LoadTarget target) {
        OnMetricsUpdatedListener callback = listener;
        if (callback == null) return;
        int steps = totalSteps;
        int hits = hitCount;
        int misses = missCount;
        long latency = lastLatencyMs;
        long waste = totalWastedBytes;
        postToMain.accept(() -> {
            if (target == currentTarget && listener == callback) {
                callback.onMetricsUpdated(steps, hits, misses, latency, waste);
            }
        });
    }

    public synchronized int getTotalSteps() {
        return totalSteps;
    }

    public synchronized int getHitCount() {
        return hitCount;
    }

    public synchronized int getMissCount() {
        return missCount;
    }

    public synchronized long getLastLatencyMs() {
        return lastLatencyMs;
    }

    public synchronized long getTotalWastedBytes() {
        return totalWastedBytes;
    }

    @Nullable
    public synchronized ReplayResult.Metrics getCurrentReplayMetrics() {
        LoadTarget target = currentTarget;
        if (target.replayRunId == null || target.sealed) return null;
        return new ReplayResult.Metrics(target.fullHits, target.lateHits, target.misses,
                target.latencySumMs, target.simulatedWastedBytes);
    }
}

