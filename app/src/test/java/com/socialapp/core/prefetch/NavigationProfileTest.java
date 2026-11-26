package com.socialapp.core.prefetch;

import com.socialapp.core.prefetch.profile.NavigationProfile;
import com.socialapp.core.telemetry.TelemetryCollector;

import org.junit.After;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.socialapp.core.prefetch.ScreenId.*;
import static org.junit.Assert.*;

public class NavigationProfileTest {
    private final PrefetchEngine engine = newEngine();
    private final PrefetchEngine restored = newEngine();
    private final TelemetryCollector collector = new TelemetryCollector(() -> 100);

    @After
    public void shutdown() {
        engine.getCache().shutdown();
        restored.getCache().shutdown();
    }

    @Test
    public void snapshotCapturesAllThreeTablesAndIsIndependentOfLaterLearning() {
        trace(engine, collector, FEED_NEWEST, POST_DETAIL, COMMENTS);
        NavigationProfile profile = engine.snapshotProfile(8);
        List<ScreenId> feed = Collections.singletonList(FEED_NEWEST);
        List<ScreenId> post = Collections.singletonList(POST_DETAIL);
        List<ScreenId> pair = Arrays.asList(FEED_NEWEST, POST_DETAIL);
        assertEquals(8, profile.profileVersion);
        assertEquals(3, profile.minSupport);
        assertEquals(Integer.valueOf(7), profile.order1.get(feed).get(POST_DETAIL));
        assertEquals(Integer.valueOf(6), profile.order1.get(post).get(COMMENTS));
        assertEquals(Integer.valueOf(9), profile.order2Backoff.get(post).get(COMMENTS));
        assertEquals(Integer.valueOf(4), profile.order2.get(pair).get(COMMENTS));

        collector.confirmOutOfScopeScreen();
        trace(engine, collector, FEED_NEWEST, POST_DETAIL, COMMENTS);
        assertEquals(5, engine.getTransitionCount(pair, COMMENTS));
        assertEquals(Integer.valueOf(4), profile.order2.get(pair).get(COMMENTS));
        assertThrows(UnsupportedOperationException.class, () -> profile.order2.clear());
        assertThrows(UnsupportedOperationException.class, () -> profile.order1.get(feed).put(POST_DETAIL, 99));
        assertThrows(UnsupportedOperationException.class, () -> profile.order1.keySet().iterator().next().clear());
    }

    @Test
    public void restoreReplacesWarmStartAndPreservesOrderTwoPredictionsAndBackoff() {
        engine.setCurrentPolicy(PrefetchEngine.Policy.P4_MARKOV_ORDER2);
        restored.setCurrentPolicy(PrefetchEngine.Policy.P4_MARKOV_ORDER2);
        trace(engine, collector, FEED_NEWEST, POST_DETAIL);
        List<Map.Entry<ScreenId, Double>> pairPrediction = engine.getLastPredictions();
        NavigationProfile profile = engine.snapshotProfile(2);
        restored.restoreProfile(profile);
        restored.restoreProfile(profile);
        assertEquals(7, restored.getTransitionCount(Collections.singletonList(FEED_NEWEST), POST_DETAIL));

        TelemetryCollector other = new TelemetryCollector(() -> 100);
        trace(restored, other, FEED_NEWEST, POST_DETAIL);
        assertEquals(pairPrediction, restored.getLastPredictions());
        collector.confirmOutOfScopeScreen();
        other.confirmOutOfScopeScreen();
        trace(engine, collector, POST_DETAIL);
        trace(restored, other, POST_DETAIL);
        assertEquals(engine.getLastPredictions(), restored.getLastPredictions());
        assertEquals(COMMENTS, restored.getLastPredictions().get(0).getKey());
        assertEquals(8.0 / 17.0, restored.getLastPredictions().get(0).getValue(), 0.000001);
    }

    @Test
    public void restoreClearsEvenTheCurrentRuntimeContext() {
        trace(engine, collector, FEED_NEWEST, POST_DETAIL);
        NavigationProfile profile = engine.snapshotProfile(1);
        engine.restoreProfile(profile);
        trace(engine, collector, COMMENTS);
        assertEquals(5, engine.getTransitionCount(Collections.singletonList(POST_DETAIL), COMMENTS));
        assertEquals(3, engine.getTransitionCount(Arrays.asList(FEED_NEWEST, POST_DETAIL), COMMENTS));
        trace(engine, collector, PROFILE);
        assertEquals(1, engine.getTransitionCount(Collections.singletonList(COMMENTS), PROFILE));
        assertEquals(0, engine.getTransitionCount(Arrays.asList(POST_DETAIL, COMMENTS), PROFILE));
    }

    static PrefetchEngine newEngine() {
        PrefetchEngine result = new PrefetchEngine(new PrefetchCache(10, 0), Runnable::run, ignored -> {});
        result.setSimulationMode(false);
        return result;
    }

    static void trace(PrefetchEngine engine, TelemetryCollector collector, ScreenId... screens) {
        for (ScreenId screen : screens) engine.onNavigationEvent(collector.confirmScreen(screen).get());
    }
}
