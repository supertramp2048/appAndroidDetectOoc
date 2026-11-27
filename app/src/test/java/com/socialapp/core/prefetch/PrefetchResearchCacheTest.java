package com.socialapp.core.prefetch;

import com.socialapp.core.telemetry.DataLoadEvent;
import com.socialapp.core.telemetry.LoadContext;
import com.socialapp.core.telemetry.TelemetryCollector;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

import static com.socialapp.core.prefetch.ScreenId.*;
import static org.junit.Assert.*;

public class PrefetchResearchCacheTest {
    @Rule public final TemporaryFolder folder = new TemporaryFolder();
    private final Queue<Runnable> work = new ArrayDeque<>();
    private final PrefetchCache cache = new PrefetchCache(3, 0);
    private final PrefetchEngine engine = new PrefetchEngine(cache, Runnable::run, ignored -> {}, work::add);

    @Before
    public void speedUpSimulatedTransfers() {
        engine.setTimeScale(1000);
    }

    @After
    public void shutDownCache() {
        cache.shutdown();
    }

    @Test
    public void foregroundMetricsLeavePrefetchAvailableForFullHit() throws Exception {
        MetricsLogger logger = new MetricsLogger(folder.newFolder(), Runnable::run, Runnable::run);
        TelemetryCollector collector = connectedCollector(logger);
        UUID run = collector.beginReplayRun();
        logger.setConfigName("trace", run);
        collector.beginReplaySession(1);
        LoadContext first = collector.beginLoad(FEED_NEWEST, LoadContext.Trigger.INITIAL, null);
        MetricsLogger.LoadTarget target = logger.captureLoadTarget(run);
        engine.triggerBackgroundPrefetch(POST_DETAIL);
        runNextPrefetch();
        assertTrue(logger.logLoad(target, true, 1, first.complete(110, DataLoadEvent.Outcome.SUCCESS),
                HitType.MISS, FEED_NEWEST.getBaseSizeBytes(), "P3", "WIFI", 1));
        assertTrue(cache.contains(POST_DETAIL));

        LoadContext second = collector.beginLoad(POST_DETAIL, LoadContext.Trigger.INITIAL, null);
        Result full = new Result();
        assertTrue(engine.tryLoadPrefetched(POST_DETAIL, second.sessionId, full));
        assertEquals(HitType.FULL_HIT, full.hit);
        assertNotNull(full.data);
        logger.logLoad(target, true, 2, second.complete(101, DataLoadEvent.Outcome.SUCCESS),
                full.hit, 0L, "P3", "WIFI", 1);

        engine.triggerBackgroundPrefetch(COMMENTS);
        LoadContext third = collector.beginLoad(COMMENTS, LoadContext.Trigger.INITIAL, null);
        Result late = new Result();
        assertTrue(engine.tryLoadPrefetched(COMMENTS, third.sessionId, late));
        assertNull(late.hit);
        runNextPrefetch();
        assertEquals(HitType.LATE_HIT, late.hit);
        assertNotNull(late.data);
        logger.logLoad(target, true, 3, third.complete(115, DataLoadEvent.Outcome.SUCCESS),
                late.hit, 0L, "P3", "WIFI", 1);

        engine.triggerBackgroundPrefetch(SEARCH);
        runNextPrefetch();
        assertEquals(0, logger.getTotalWastedBytes());
        collector.endReplaySession();
        logger.finishReplayRun(run, "RUN_FINISHED", true, 3, 3);

        List<Map<String, String>> rows = MetricsLoggerLoadTargetTest.readRows(target.file());
        assertEquals(5, rows.size());
        assertEquals(Arrays.asList("MISS", "FULL_HIT", "LATE_HIT"), Arrays.asList(
                rows.get(0).get("hit_type"), rows.get(1).get("hit_type"), rows.get(2).get("hit_type")));
        assertEquals("CACHE_WASTE", rows.get(3).get("row_type"));
        assertEquals("61440", rows.get(3).get("simulated_wasted_prefetch_bytes"));
        assertEquals("RUN_END", rows.get(4).get("row_type"));
        assertEquals(3, logger.getTotalSteps());
        assertEquals(2, logger.getHitCount());
        assertEquals(1, logger.getMissCount());
        assertEquals(61_440, logger.getTotalWastedBytes());
        assertEquals(0, cache.size());
    }

    @Test
    public void expiryCountsUnusedBytesOnceAndNeverCountsUsedBytes() {
        cache.putPrefetched(POST_DETAIL, "used", 120, false);
        cache.putPrefetched(SEARCH, "unused", 60, false);
        assertNotNull(cache.takePrefetched(POST_DETAIL));
        long expires = Math.max(cache.get(POST_DETAIL).getExpiresAtMs(), cache.get(SEARCH).getExpiresAtMs());
        assertEquals(2, cache.cleanupExpiredAt(expires));
        assertEquals(60, cache.getWastedBytes());
        assertEquals(0, cache.cleanupExpiredAt(expires));
        assertEquals(60, cache.closeResearchScope());
        assertEquals(0, cache.closeResearchScope());
    }

    @Test
    public void lruAndUnusedResidentsAreSummedOnceAtSessionClose() {
        List<Long> waste = new ArrayList<>();
        UUID session = UUID.randomUUID();
        engine.setCacheSession(session, (bytes, cancelled) -> waste.add(bytes));
        cache.putPrefetched(SEARCH, "evicted", 60, false);
        cache.putPrefetched(POST_DETAIL, "used", 120, false);
        cache.putPrefetched(PROFILE, "unused", 20, false);
        assertNotNull(cache.takePrefetched(POST_DETAIL));
        cache.putPrefetched(COMMENTS, "unused", 30, false);
        assertFalse(cache.contains(SEARCH));
        assertEquals(60, cache.getWastedBytes());
        engine.setCacheSession(null, null);
        engine.setCacheSession(null, null);
        assertEquals(Arrays.asList(110L), waste);
    }

    @Test
    public void retiredTaskCannotPublishOrRemoveAReplacementForTheSameScreen() {
        UUID old = UUID.randomUUID();
        UUID next = UUID.randomUUID();
        List<Integer> cancelled = new ArrayList<>();
        List<Long> waste = new ArrayList<>();
        engine.setCacheSession(old, (bytes, tasks) -> {
            waste.add(bytes);
            cancelled.add(tasks);
        });
        engine.triggerBackgroundPrefetch(POST_DETAIL);
        Result previousLoad = new Result();
        assertTrue(engine.tryLoadPrefetched(POST_DETAIL, old, previousLoad));

        engine.setCacheSession(next, null);
        engine.triggerBackgroundPrefetch(POST_DETAIL);
        runNextPrefetch();
        assertFalse(cache.contains(POST_DETAIL));
        assertFalse(engine.tryLoadPrefetched(POST_DETAIL, old, new Result()));
        assertEquals(1, previousLoad.cancellations);
        assertNull(previousLoad.hit);
        assertEquals(Arrays.asList(1), cancelled);
        assertEquals(Arrays.asList(0L), waste);

        Result newLoad = new Result();
        assertTrue(engine.tryLoadPrefetched(POST_DETAIL, next, newLoad));
        runNextPrefetch();
        assertEquals(HitType.LATE_HIT, newLoad.hit);
        assertTrue(cache.contains(POST_DETAIL));
    }

    @Test
    public void liveTimeoutRollsTheCacheScopeEvenBeforeNewScreenConfirmation() throws Exception {
        MetricsLogger logger = new MetricsLogger(folder.newFolder(), Runnable::run, Runnable::run);
        long[] now = {100};
        TelemetryCollector collector = new TelemetryCollector(() -> now[0], (run, session, trace) ->
                engine.setCacheSession(session, session == null ? null : logger.captureWasteSink(run, session, trace)));
        LoadContext first = collector.beginLoad(FEED_NEWEST, LoadContext.Trigger.INITIAL, null);
        engine.triggerBackgroundPrefetch(SEARCH);
        runNextPrefetch();
        now[0] += 30 * 60_000;
        LoadContext next = collector.beginLoad(SEARCH, LoadContext.Trigger.INITIAL, null);
        assertNotEquals(first.sessionId, next.sessionId);
        assertFalse(engine.tryLoadPrefetched(SEARCH, next.sessionId, new Result()));
        assertEquals(61_440, logger.getTotalWastedBytes());
        Map<String, String> waste = MetricsLoggerLoadTargetTest.readRows(logger.getCurrentLogFile()).get(0);
        assertEquals(first.sessionId.toString(), waste.get("session_id"));
    }

    @Test
    public void p0DoesNotScheduleAnyBackgroundWork() {
        TelemetryCollector collector = new TelemetryCollector(() -> 100, (run, session, trace) ->
                engine.setCacheSession(session, null));
        engine.setCurrentPolicy(PrefetchEngine.Policy.P0_NONE);
        engine.onNavigationEvent(collector.confirmScreen(FEED_NEWEST).get());
        assertTrue(work.isEmpty());
    }

    private TelemetryCollector connectedCollector(MetricsLogger logger) {
        return new TelemetryCollector(() -> 100, (run, session, trace) ->
                engine.setCacheSession(session, session == null ? null : logger.captureWasteSink(run, session, trace)));
    }

    private void runNextPrefetch() {
        work.remove().run();
    }

    private static final class Result implements ScreenLoadCallback<Object> {
        HitType hit;
        Object data;
        int cancellations;

        @Override public void onLoaded(HitType hit, long latency, Object data) {
            this.hit = hit;
            this.data = data;
        }

        @Override public void onError(Throwable error) {
            throw new AssertionError(error);
        }

        @Override public void onCancelled(Throwable error) {
            cancellations++;
        }
    }
}
