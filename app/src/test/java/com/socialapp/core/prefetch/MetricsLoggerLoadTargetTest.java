package com.socialapp.core.prefetch;

import com.socialapp.core.telemetry.DataLoadEvent;
import com.socialapp.core.telemetry.LoadContext;
import com.socialapp.core.telemetry.TelemetryCollector;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

public class MetricsLoggerLoadTargetTest {
    @Rule public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void replayTerminalExportsTheInitialTimeScaleAndKeepsOtherRowsAligned() throws Exception {
        MetricsLogger logger = new MetricsLogger(folder.newFolder(), Runnable::run, Runnable::run);
        UUID run = UUID.randomUUID();
        assertTrue(logger.setConfigName("evaluation", run, 2.0f));
        MetricsLogger.LoadTarget target = logger.captureLoadTarget(run);
        logger.captureWasteSink(run, UUID.randomUUID(), 8).accept(0L, 0);
        logger.finishReplayRun(run, "RUN_FINISHED", true, 109, 109);
        List<Map<String, String>> rows = readRows(target.file());
        assertEquals(2, rows.size());
        assertEquals("", rows.get(0).get("time_scale"));
        assertEquals("RUN_END", rows.get(1).get("row_type"));
        assertEquals("2.0", rows.get(1).get("time_scale"));
    }

    @Test
    public void runEndSealsTheOriginalFileAndRecordsValidity() throws Exception {
        MetricsLogger logger = new MetricsLogger(folder.newFolder(), Runnable::run, Runnable::run);
        TelemetryCollector collector = new TelemetryCollector(() -> 100);
        UUID firstRun = collector.beginReplayRun();
        collector.beginReplaySession(9);
        logger.setConfigName("first", firstRun);
        MetricsLogger.LoadTarget first = logger.captureLoadTarget(firstRun);
        LoadContext late = collector.beginLoad(ScreenId.POST_DETAIL, LoadContext.Trigger.UNKNOWN, null);
        logger.captureWasteSink(firstRun, late.sessionId, 9).accept(125L, 1);
        ReplayResult.Metrics firstMetrics = logger.finishReplayRun(firstRun, "TIMEOUT", false, 1, 1);
        assertNull(firstMetrics.hitRate);
        assertNull(firstMetrics.averageLoadLatencyMs);
        assertEquals(Long.valueOf(125), firstMetrics.simulatedWastedBytes);
        collector.endReplayRun();

        UUID secondRun = collector.beginReplayRun();
        collector.beginReplaySession(5);
        logger.setConfigName("second", secondRun);
        MetricsLogger.LoadTarget second = logger.captureLoadTarget(secondRun);
        LoadContext current = collector.beginLoad(ScreenId.FEED_HOT, LoadContext.Trigger.UNKNOWN, null);
        assertTrue(logger.logLoad(second, true, 1, current.complete(102, DataLoadEvent.Outcome.SUCCESS),
                HitType.FULL_HIT, 0L, "P0", "WIFI", 1));
        LoadContext cancelled = collector.beginLoad(ScreenId.POST_DETAIL, LoadContext.Trigger.UNKNOWN, null);
        assertFalse(logger.logLoad(second, true, 2, cancelled.complete(103, DataLoadEvent.Outcome.CANCELLED),
                HitType.LATE_HIT, 0L, "P0", "WIFI", 1));
        assertFalse(logger.logLoad(first, false, 2, late.complete(400, DataLoadEvent.Outcome.SUCCESS),
                HitType.MISS, 17L, "P3", "LTE", 2));
        ReplayResult.Metrics secondMetrics = logger.finishReplayRun(secondRun, "RUN_FINISHED", true, 3, 2);
        assertEquals(1, secondMetrics.fullHits);
        assertEquals(0, secondMetrics.lateHits);
        assertEquals(0, secondMetrics.misses);
        assertEquals(2, secondMetrics.averageLoadLatencyMs, 0);
        assertNull(secondMetrics.simulatedWastedBytes);

        List<Map<String, String>> oldRows = readRows(first.file());
        List<Map<String, String>> currentRows = readRows(second.file());
        assertEquals("metrics_v2_first.csv", first.file().getName());
        assertEquals(2, oldRows.size());
        assertEquals("125", oldRows.get(0).get("simulated_wasted_prefetch_bytes"));
        assertEquals("1", oldRows.get(0).get("cancelled_prefetch_count"));
        assertEquals("false", oldRows.get(1).get("run_valid"));
        assertEquals("TIMEOUT", oldRows.get(1).get("run_status"));
        assertEquals(2, currentRows.size());
        assertEquals("2", currentRows.get(0).get("load_latency_ms"));
        assertEquals("0", currentRows.get(0).get("simulated_foreground_bytes"));
        assertEquals("", currentRows.get(0).get("simulated_wasted_prefetch_bytes"));
        assertEquals("true", currentRows.get(1).get("run_valid"));
        assertEquals("3", currentRows.get(1).get("confirmed_steps"));
        assertEquals("2", currentRows.get(1).get("navigation_events"));
        assertEquals(1, logger.getTotalSteps());
        assertEquals(1, logger.getHitCount());
        assertEquals(0, logger.getTotalWastedBytes());
    }

    @Test
    public void replayMetricsIncludeAllCsvRowsUntilSealAndSurviveSwitchToLive() throws Exception {
        MetricsLogger logger = new MetricsLogger(folder.newFolder(), Runnable::run, Runnable::run);
        TelemetryCollector collector = new TelemetryCollector(() -> 100);
        UUID run = collector.beginReplayRun();
        collector.beginReplaySession(1);
        logger.setConfigName("replay", run);
        MetricsLogger.LoadTarget target = logger.captureLoadTarget(run);
        LoadContext full = collector.beginLoad(ScreenId.FEED_HOT, LoadContext.Trigger.UNKNOWN, null);
        LoadContext late = collector.beginLoad(ScreenId.POST_DETAIL, LoadContext.Trigger.UNKNOWN, null);
        logger.logLoad(target, true, 1, full.complete(104, DataLoadEvent.Outcome.SUCCESS),
                HitType.FULL_HIT, 0L, "P4", "WIFI", 1);
        logger.captureWasteSink(run, full.sessionId, 1).accept(17L, 0);
        collector.endReplaySession();
        collector.beginReplaySession(2);
        LoadContext miss = collector.beginLoad(ScreenId.SEARCH, LoadContext.Trigger.UNKNOWN, null);
        logger.logLoad(target, false, 2, late.complete(114, DataLoadEvent.Outcome.SUCCESS),
                HitType.LATE_HIT, 0L, "P4", "WIFI", 1);
        logger.logLoad(target, true, 1, miss.complete(130, DataLoadEvent.Outcome.SUCCESS),
                HitType.MISS, 60L, "P4", "WIFI", 1);
        logger.captureWasteSink(run, miss.sessionId, 2).accept(25L, 0);
        ReplayResult.Metrics metrics = logger.finishReplayRun(run, "STOPPED", false, 3, 3);
        logger.setConfigName("default");

        assertEquals(0, logger.getTotalSteps());
        assertEquals(1, metrics.fullHits);
        assertEquals(1, metrics.lateHits);
        assertEquals(1, metrics.misses);
        assertEquals(2.0 / 3, metrics.hitRate, 1e-10);
        assertEquals(16, metrics.averageLoadLatencyMs, 0);
        assertEquals(Long.valueOf(42), metrics.simulatedWastedBytes);
        assertEquals("STOPPED", readRows(target.file()).get(5).get("run_status"));
    }

    @Test
    public void lateSessionKeepsItsOriginalIdentityAndUnknownRealBytesAreBlank() throws Exception {
        MetricsLogger logger = new MetricsLogger(folder.newFolder(), Runnable::run, Runnable::run);
        TelemetryCollector collector = new TelemetryCollector(() -> 100);
        UUID run = collector.beginReplayRun();
        collector.beginReplaySession(7);
        logger.setConfigName("replay", run);
        MetricsLogger.LoadTarget target = logger.captureLoadTarget(run);
        LoadContext load = collector.beginLoad(ScreenId.POST_DETAIL, LoadContext.Trigger.UNKNOWN, null);
        collector.endReplaySession();
        collector.beginReplaySession(8);
        assertFalse(logger.logLoad(target, false, 2, load.complete(200, DataLoadEvent.Outcome.SUCCESS),
                HitType.MISS, null, "P0", "WIFI", 1));
        Map<String, String> row = readRows(target.file()).get(0);
        assertEquals("2", row.get("schema_version"));
        assertEquals("7", row.get("replay_session_id"));
        assertEquals(load.sessionId.toString(), row.get("session_id"));
        assertEquals("REAL", row.get("load_mode"));
        assertEquals("", row.get("simulated_foreground_bytes"));
        assertEquals(0, logger.getTotalSteps());
    }

    @Test
    public void fullBufferStillWritesRunEndAndMarksOnlyTheAffectedRunIncomplete() throws Exception {
        java.util.ArrayDeque<Runnable> disk = new java.util.ArrayDeque<>();
        MetricsLogger logger = new MetricsLogger(folder.newFolder(), disk::add, Runnable::run, 1);
        UUID run = UUID.randomUUID();
        assertTrue(logger.setConfigName("overflow", run));
        MetricsLogger.LoadTarget overflow = logger.captureLoadTarget(run);
        java.util.function.BiConsumer<Long, Integer> waste = logger.captureWasteSink(run, UUID.randomUUID(), 1);
        waste.accept(10L, 0);
        waste.accept(20L, 0);
        java.util.concurrent.atomic.AtomicReference<ReplayResult.ExportStatus> export = new java.util.concurrent.atomic.AtomicReference<>();
        assertEquals(Long.valueOf(30), logger.finishReplayRun(run, "RUN_FINISHED", true, 2, 2,
                export::set).simulatedWastedBytes);
        assertNull(export.get());
        assertEquals(1, disk.size());
        disk.remove().run();

        assertFalse(export.get().isComplete());
        assertTrue(export.get().terminalWritten);
        assertEquals(1, export.get().droppedRows);
        List<Map<String, String>> first = readRows(overflow.file());
        assertEquals(2, first.size());
        assertEquals("10", first.get(0).get("simulated_wasted_prefetch_bytes"));
        assertEquals("RUN_END", first.get(1).get("row_type"));
        assertEquals("false", first.get(1).get("run_valid"));
        assertEquals("RUN_FINISHED_TELEMETRY_INCOMPLETE", first.get(1).get("run_status"));

        UUID nextRun = UUID.randomUUID();
        assertTrue(logger.setConfigName("complete", nextRun));
        MetricsLogger.LoadTarget complete = logger.captureLoadTarget(nextRun);
        waste.accept(99L, 0); // The old producer still has its old, sealed target.
        logger.captureWasteSink(nextRun, UUID.randomUUID(), 2).accept(5L, 0);
        logger.finishReplayRun(nextRun, "RUN_FINISHED", true, 2, 2, export::set);
        disk.remove().run();
        assertTrue(export.get().isComplete());
        List<Map<String, String>> second = readRows(complete.file());
        assertEquals(2, second.size());
        assertEquals(nextRun.toString(), second.get(0).get("run_id"));
        assertEquals("5", second.get(0).get("simulated_wasted_prefetch_bytes"));
        assertEquals("true", second.get(1).get("run_valid"));
    }

    @Test
    public void replayTerminalWaitsForNavigationAndUsesStableScreenIds() throws Exception {
        java.util.ArrayDeque<Runnable> disk = new java.util.ArrayDeque<>();
        File root = folder.newFolder();
        MetricsLogger logger = new MetricsLogger(root, disk::add, Runnable::run, 4);
        TelemetryCollector collector = new TelemetryCollector(() -> 50);
        logger.recordNavigation(collector.confirmScreen(ScreenId.SEARCH).get());
        UUID run = collector.beginReplayRun();
        assertTrue(logger.setConfigName("navigation", run));
        MetricsLogger.LoadTarget target = logger.captureLoadTarget(run);
        collector.beginReplaySession(7);
        logger.recordNavigation(collector.confirmScreen(ScreenId.FEED_NEWEST).get());
        logger.recordNavigation(collector.confirmScreen(ScreenId.POST_DETAIL).get());
        java.util.concurrent.atomic.AtomicBoolean exported = new java.util.concurrent.atomic.AtomicBoolean();
        logger.finishReplayRun(run, "RUN_FINISHED", true, 2, 2, status -> exported.set(status.isComplete()));
        File navigation = new File(root, "navigation_v1.csv");
        assertFalse(navigation.exists());
        disk.remove().run();
        assertTrue(exported.get());

        List<Map<String, String>> events = readRows(navigation);
        assertEquals(3, events.size());
        assertEquals("LIVE", events.get(0).get("source"));
        assertEquals("", events.get(0).get("replay_session_id"));
        assertEquals(run.toString(), events.get(1).get("run_id"));
        assertEquals("7", events.get(1).get("replay_session_id"));
        assertEquals("", events.get(1).get("from_screen"));
        assertEquals("FEED_NEWEST", events.get(2).get("from_screen"));
        assertEquals("POST_DETAIL", events.get(2).get("to_screen"));
        assertEquals("2", events.get(2).get("sequence"));
        assertEquals("50", events.get(2).get("timestamp_elapsed_ms"));
        assertEquals("true", readRows(target.file()).get(0).get("run_valid"));
    }

    @Test
    public void droppedNavigationInvalidatesItsRunWithoutDroppingRunEnd() throws Exception {
        java.util.ArrayDeque<Runnable> disk = new java.util.ArrayDeque<>();
        MetricsLogger logger = new MetricsLogger(folder.newFolder(), disk::add, Runnable::run, 1);
        TelemetryCollector collector = new TelemetryCollector(() -> 50);
        UUID run = collector.beginReplayRun();
        assertTrue(logger.setConfigName("navigation_overflow", run));
        MetricsLogger.LoadTarget target = logger.captureLoadTarget(run);
        collector.beginReplaySession(3);
        logger.recordNavigation(collector.confirmScreen(ScreenId.FEED_NEWEST).get());
        logger.recordNavigation(collector.confirmScreen(ScreenId.POST_DETAIL).get());
        logger.finishReplayRun(run, "RUN_FINISHED", true, 2, 2);
        disk.remove().run();
        Map<String, String> end = readRows(target.file()).get(0);
        assertEquals("RUN_END", end.get("row_type"));
        assertEquals("false", end.get("run_valid"));
        assertEquals("2", end.get("navigation_events"));
        assertEquals("RUN_FINISHED_TELEMETRY_INCOMPLETE", end.get("run_status"));
    }

    @Test
    public void navigationWriteFailureMarksTheTerminalAndExportIncomplete() throws Exception {
        File root = folder.newFolder();
        assertTrue(new File(root, "navigation_v1.csv").mkdir());
        MetricsLogger logger = new MetricsLogger(root, Runnable::run, Runnable::run);
        TelemetryCollector collector = new TelemetryCollector(() -> 50);
        UUID run = collector.beginReplayRun();
        logger.setConfigName("navigation_io_error", run);
        MetricsLogger.LoadTarget target = logger.captureLoadTarget(run);
        collector.beginReplaySession(2);
        logger.recordNavigation(collector.confirmScreen(ScreenId.FEED_HOT).get());
        java.util.concurrent.atomic.AtomicReference<ReplayResult.ExportStatus> export =
                new java.util.concurrent.atomic.AtomicReference<>();
        logger.finishReplayRun(run, "RUN_FINISHED", true, 1, 1, export::set);
        assertTrue(export.get().terminalWritten);
        assertEquals(1, export.get().failedRows);
        assertFalse(export.get().isComplete());
        assertEquals("false", readRows(target.file()).get(0).get("run_valid"));
    }

    static List<Map<String, String>> readRows(File file) throws IOException {
        List<String> lines = Files.readAllLines(file.toPath());
        String[] headers = lines.get(0).split(",", -1);
        List<Map<String, String>> rows = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] cells = line.split(",", -1);
            assertEquals(headers.length, cells.length);
            Map<String, String> row = new HashMap<>();
            for (int i = 0; i < headers.length; i++) row.put(headers[i], cells[i]);
            rows.add(row);
        }
        return rows;
    }
}
