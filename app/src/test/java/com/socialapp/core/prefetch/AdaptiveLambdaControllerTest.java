package com.socialapp.core.prefetch;

import static com.socialapp.core.prefetch.AdaptiveLambdaController.Reason.*;
import static com.socialapp.core.prefetch.ScreenId.*;
import static org.junit.Assert.*;

import com.socialapp.core.telemetry.LoadContext;
import com.socialapp.core.telemetry.NavigationEvent;
import com.socialapp.core.telemetry.TelemetryCollector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.Test;

public class AdaptiveLambdaControllerTest {
    private static final double START = AdaptiveLambdaController.Config.DEFAULT.initial;

    @Test
    public void normalizedUtilityChangesWithPayloadAndIgnoresReplaySpeed() {
        PrefetchCache cache = new PrefetchCache();
        try {
            PrefetchEngine engine = new PrefetchEngine(cache, Runnable::run, message -> {}, task -> {});
            engine.setCurrentNetwork(PrefetchEngine.NetworkProfile.WIFI);
            long single = engine.scaledPayloadBytes(POST_DETAIL);
            long estimate = engine.estimatedLoadMs(POST_DETAIL);
            assertEquals(184320, single);
            assertEquals(25, estimate);
            assertTrue(P5Experiment.utility(0.60, estimate, START, single) > 0);
            assertTrue(P5Experiment.utility(0.20, estimate, START, single) < 0);
            engine.setTimeScale(10);
            assertEquals(estimate, engine.estimatedLoadMs(POST_DETAIL));
            assertEquals(10, engine.calculateSimulatedNetworkDelayMs(POST_DETAIL));
            engine.setPayloadScale(2);
            assertEquals(2 * single, engine.scaledPayloadBytes(POST_DETAIL));
            assertEquals(31, engine.estimatedLoadMs(POST_DETAIL));
            assertTrue(P5Experiment.utility(0.60, engine.estimatedLoadMs(POST_DETAIL), START,
                    engine.scaledPayloadBytes(POST_DETAIL)) < 0);
        } finally {
            cache.shutdown();
        }
    }

    @Test
    public void legacyRetainsTimeScaleBaseBytesAndProbabilityOverride() {
        assertTrue(PrefetchEngine.legacySelected(0.40, 10, 10_000_000));
        assertFalse(PrefetchEngine.legacySelected(0.39, 10, 10_000_000));
        assertTrue(PrefetchEngine.legacySelected(0.05, 25, POST_DETAIL.getBaseSizeBytes()));
        assertFalse(PrefetchEngine.legacySelected(0.05, 10, POST_DETAIL.getBaseSizeBytes()));
        assertFalse(PrefetchEngine.legacySelected(0.05, 10, 100_000)); // Equal benefit/cost.
    }

    @Test
    public void appliesDeadbandBoundsAndSampleRequirements() {
        AdaptiveLambdaController controller = new AdaptiveLambdaController(
                new AdaptiveLambdaController.Config(0.10, 0.08, 0.11, 8, 3));
        check(controller.evaluate(0.10, 100, 80, 4, 8, 7), 0.11, HIGH_WASTE);
        check(controller.evaluate(0.11, 100, 80, 4, 8, 7), 0.11, AT_MAXIMUM);
        check(controller.evaluate(0.085, 100, 10, 4, 8, 7), 0.08, LOW_WASTE_HIGH_MISS);
        check(controller.evaluate(0.08, 100, 10, 4, 8, 7), 0.08, AT_MINIMUM);
        check(controller.evaluate(0.10, 100, 35, 4, 8, 7), 0.10, STABLE);
        check(controller.evaluate(0.10, 100, 25, 4, 8, 7), 0.10, STABLE);
        check(controller.evaluate(0.10, 100, 10, 4, 8, 4), 0.10, STABLE);
        check(controller.evaluate(0.10, 100, 90, 2, 8, 7), 0.10, INSUFFICIENT_SAMPLES);
        check(controller.evaluate(0.10, 100, 90, 5, 2, 2), 0.10, INSUFFICIENT_SAMPLES);
        check(controller.evaluate(0.10, 0, 0, 0, 8, 7), 0.10, NO_RESOLVED_PREFETCH);
        check(controller.evaluate(0.10, 100, 90, 8, 0, 0), 0.10, NO_SUCCESSFUL_LOADS);
    }

    @Test
    public void traceUsesOnlyResolvedBytesAndIncreasesDecreasesThenHoldsAcrossSessions() {
        TelemetryCollector collector = new TelemetryCollector(() -> 100);
        UUID runId = collector.beginReplayRun();
        collector.beginReplaySession(4);
        List<String> rows = new ArrayList<>();
        P5Experiment run = new P5Experiment(runId, PrefetchEngine.P5Mode.ADAPTIVE_LAMBDA,
                new AdaptiveLambdaController.Config(START, 0.000015, 0.004, 3, 1), rows::add);
        NavigationEvent event = collector.confirmScreen(FEED_NEWEST).get();
        P5Experiment.Attempt pending = candidate(run, event, 100);
        pending.complete();
        assertNull(run.snapshot().wasteRatio);
        assertEquals(100, run.snapshot().completedBytes);
        pending.waste();
        pending.waste();
        pending.use();
        recordLoads(run, collector, HitType.MISS, HitType.MISS, HitType.FULL_HIT);
        assertEquals(START * 1.2, run.lambda(), 1e-12);
        assertEquals(HIGH_WASTE, run.snapshot().reason);

        collector.endReplaySession();
        collector.beginReplaySession(5);
        event = collector.confirmScreen(FEED_HOT).get();
        P5Experiment.Attempt used = candidate(run, event, 100);
        used.complete();
        used.use();
        used.use();
        P5Experiment.Attempt cancelled = candidate(run, event, 70);
        cancelled.cancel();
        cancelled.complete();
        recordLoads(run, collector, HitType.MISS, HitType.MISS, HitType.LATE_HIT);
        assertEquals(START * 1.2 * 0.8, run.lambda(), 1e-12);
        assertEquals(LOW_WASTE_HIGH_MISS, run.snapshot().reason);

        P5Experiment.Attempt small = candidate(run, event, 30);
        small.complete();
        small.waste();
        P5Experiment.Attempt large = candidate(run, event, 70);
        large.complete();
        large.use();
        recordLoads(run, collector, HitType.MISS, HitType.MISS, HitType.FULL_HIT);
        PrefetchEngine.LambdaSnapshot result = run.finish();
        assertEquals(STABLE, result.reason);
        assertEquals(2, result.updates);
        assertEquals(300, result.completedBytes);
        assertEquals(130, result.wastedBytes);
        assertEquals(130.0 / 300, result.wasteRatio, 0);
        assertEquals(2.0 / 3, result.missRate, 0);
        assertEquals(4, rows.stream().filter(row -> row.contains(",COMPLETED,")).count());
        assertEquals(2, rows.stream().filter(row -> row.contains(",USED,")).count());
        assertEquals(2, rows.stream().filter(row -> row.contains(",WASTED,")).count());
        assertEquals(1, rows.stream().filter(row -> row.contains(",CANCELLED,")).count());
        for (String row : rows) {
            assertEquals(P5Experiment.HEADER.trim().split(",", -1).length,
                    row.replace("\n", "").split(",", -1).length);
        }
        assertEquals(3, rows.stream().filter(row -> row.contains(",OBSERVATION,")).count());
        System.out.printf(java.util.Locale.US, "lambda trace: %.6f -> %.6f -> %.6f -> %.6f%n",
                START, START * 1.2, START * 1.2 * 0.8, result.current);
    }

    @Test
    public void engineResolvesCacheUseOnceAndCannotCarryOldCallbacksIntoReplay() {
        PrefetchCache cache = new PrefetchCache();
        try {
            ArrayDeque<Runnable> executor = new ArrayDeque<>();
            PrefetchEngine engine = new PrefetchEngine(cache, Runnable::run, message -> {}, executor::add);
            engine.setCurrentPolicy(PrefetchEngine.Policy.P5_ADAPTIVE_NETWORK);
            engine.setNextP5Mode(PrefetchEngine.P5Mode.ADAPTIVE_LAMBDA);
            engine.setCurrentNetwork(PrefetchEngine.NetworkProfile.WIFI);
            engine.setTimeScale(10);
            TelemetryCollector collector = new TelemetryCollector(() -> 100,
                    (run, session, id) -> engine.setCacheSession(session, null));
            List<String> oldRows = new ArrayList<>();
            UUID first = collector.beginReplayRun();
            engine.beginP5Replay(first, oldRows::add);
            collector.beginReplaySession(1);
            NavigationEvent nav = collector.confirmScreen(FEED_NEWEST).get();
            engine.onNavigationEvent(nav);
            assertEquals(1, executor.size());
            executor.remove().run();
            assertNull(engine.getP5Snapshot().wasteRatio);
            ScreenLoadCallback<Object> hit = new ScreenLoadCallback<Object>() {
                @Override public void onLoaded(HitType type, long latency, Object data) {
                    assertEquals(HitType.FULL_HIT, type);
                }
                @Override public void onError(Throwable error) { throw new AssertionError(error); }
            };
            assertTrue(engine.tryLoadPrefetched(POST_DETAIL, nav.sessionId, hit));
            assertTrue(engine.tryLoadPrefetched(POST_DETAIL, nav.sessionId, hit));
            LoadContext late = collector.beginLoad(POST_DETAIL, LoadContext.Trigger.INITIAL, null);
            P5Experiment old = engine.p5ForRun(first);
            collector.endReplaySession();
            collector.endReplayRun();
            assertEquals(0.0, engine.finishP5Replay(first).wasteRatio, 0);
            assertEquals(1, oldRows.stream().filter(row -> row.contains(",USED,")).count());
            assertEquals(0, oldRows.stream().filter(row -> row.contains(",WASTED,")).count());

            UUID second = collector.beginReplayRun();
            engine.beginP5Replay(second, ignored -> {});
            old.foreground(late, HitType.MISS);
            engine.p5ForRun(second).foreground(late, HitType.MISS);
            assertNull(engine.getP5Snapshot().missRate);
            assertEquals(START, engine.getP5Snapshot().current, 0);
            assertNull(engine.p5ForRun(first));
        } finally {
            cache.shutdown();
        }
    }

    private static P5Experiment.Attempt candidate(P5Experiment run, NavigationEvent event, long bytes) {
        P5Experiment.Attempt attempt = run.consider(event, POST_DETAIL, 0.6, 40,
                bytes, run.lambda(), 24, true);
        attempt.dispatch();
        return attempt;
    }

    private static void recordLoads(P5Experiment run, TelemetryCollector collector, HitType... hits) {
        for (HitType hit : hits) run.foreground(collector.beginLoad(POST_DETAIL, LoadContext.Trigger.INITIAL, null), hit);
    }

    private static void check(AdaptiveLambdaController.Decision decision, double lambda, AdaptiveLambdaController.Reason reason) {
        assertEquals(lambda, decision.lambda, 1e-12);
        assertEquals(reason, decision.reason);
    }
}
