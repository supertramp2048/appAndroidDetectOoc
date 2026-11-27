package com.socialapp.core.prefetch;

import com.socialapp.core.telemetry.DataLoadEvent;
import com.socialapp.core.telemetry.LoadContext;
import com.socialapp.core.telemetry.NavigationEvent;
import com.socialapp.core.telemetry.TelemetryCollector;

import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.socialapp.core.prefetch.PrefetchEngine.Policy.*;
import static com.socialapp.core.prefetch.ScreenId.*;
import static org.junit.Assert.*;

public class NavigationLearningTest {
    private long timeMs = 100;
    private final TelemetryCollector collector = new TelemetryCollector(() -> timeMs);
    private final List<String> logs = new ArrayList<>();
    private final List<ScreenId> dispatched = new ArrayList<>();
    private final PrefetchEngine engine = new PrefetchEngine(new PrefetchCache(10, 0), Runnable::run, logs::add) {
        @Override
        void triggerBackgroundPrefetch(ScreenId screen) {
            dispatched.add(screen);
        }
    };

    @After
    public void shutDownCache() {
        engine.getCache().shutdown();
    }

    @Test
    public void threeScreenTraceIncrementsBothMarkovOrders() {
        engine.setCurrentPolicy(P0_NONE);
        visit(FEED_NEWEST);
        visit(POST_DETAIL);
        NavigationEvent last = visit(COMMENTS);

        assertCount(7, POST_DETAIL, FEED_NEWEST);
        assertCount(6, COMMENTS, POST_DETAIL);
        assertCount(4, COMMENTS, FEED_NEWEST, POST_DETAIL);
        assertTrue(logs.get(0).contains("learnedMaxOrder=0"));
        assertTrue(logs.get(1).contains("learnedMaxOrder=1"));
        assertTrue(logs.get(2).contains("navigationId=" + last.navigationId));
        assertTrue(logs.get(2).contains("context=[FEED_NEWEST, POST_DETAIL]"));
        assertTrue(logs.get(2).contains("learnedMaxOrder=2"));
        assertTrue(dispatched.isEmpty());
    }

    @Test
    public void sessionsKeepCountsAndNeverLearnAcrossTheBoundary() {
        collector.beginReplayRun();
        collector.beginReplaySession(11);
        visit(FEED_NEWEST);
        visit(POST_DETAIL);
        collector.endReplaySession();
        collector.beginReplaySession(12);
        visit(COMMENTS);
        visit(PROFILE);

        assertCount(7, POST_DETAIL, FEED_NEWEST);
        assertCount(5, COMMENTS, POST_DETAIL);
        assertCount(3, COMMENTS, FEED_NEWEST, POST_DETAIL);
        assertCount(1, PROFILE, COMMENTS);
        assertCount(0, PROFILE, POST_DETAIL, COMMENTS);
    }

    @Test
    public void outOfScopeBreakReseedsContextWithoutClearingCounts() {
        visit(FEED_NEWEST);
        visit(POST_DETAIL);
        collector.confirmOutOfScopeScreen();
        assertNull(visit(COMMENTS).fromScreen);
        visit(PROFILE);

        assertCount(7, POST_DETAIL, FEED_NEWEST);
        assertCount(5, COMMENTS, POST_DETAIL);
        assertCount(3, COMMENTS, FEED_NEWEST, POST_DETAIL);
        assertCount(1, PROFILE, COMMENTS);
        assertCount(0, PROFILE, POST_DETAIL, COMMENTS);
    }

    @Test
    public void refreshRetryAndOutOfOrderLoadCompletionsDoNotDriveLearning() {
        NavigationEvent home = visit(FEED_NEWEST);
        LoadContext refresh = collector.beginLoad(FEED_NEWEST, LoadContext.Trigger.REFRESH, home);
        LoadContext retry = collector.beginLoad(FEED_NEWEST, LoadContext.Trigger.RETRY, home);
        visit(POST_DETAIL);
        retry.complete(++timeMs, DataLoadEvent.Outcome.FAILURE);
        refresh.complete(++timeMs, DataLoadEvent.Outcome.SUCCESS);

        assertEquals(POST_DETAIL, engine.getLastCurrentScreen());
        assertEquals(2, logs.size());
        assertCount(0, FEED_NEWEST, FEED_NEWEST);
        visit(COMMENTS);
        assertCount(7, POST_DETAIL, FEED_NEWEST);
        assertCount(4, COMMENTS, FEED_NEWEST, POST_DETAIL);
    }

    @Test
    public void duplicateEventsAndOldSessionRedeliveryHaveNoEffect() {
        collector.beginReplayRun();
        collector.beginReplaySession(11);
        visit(FEED_NEWEST);
        NavigationEvent post = visit(POST_DETAIL);
        int beforeDuplicate = dispatched.size();
        engine.onNavigationEvent(post);
        assertEquals(beforeDuplicate, dispatched.size());
        collector.endReplaySession();
        collector.beginReplaySession(12);
        visit(COMMENTS);
        engine.onNavigationEvent(post);

        assertCount(7, POST_DETAIL, FEED_NEWEST);
        assertEquals(3, logs.size());
        assertEquals(COMMENTS, engine.getLastCurrentScreen());
    }

    @Test
    public void replayConfirmationOfTheSameScreenDoesNotCreateSelfTransition() {
        collector.beginReplayRun();
        collector.beginReplaySession(11);
        visit(FEED_NEWEST);
        assertFalse(collector.confirmScreen(FEED_NEWEST).isPresent());
        visit(POST_DETAIL);
        assertFalse(collector.confirmScreen(POST_DETAIL).isPresent());
        visit(COMMENTS);

        assertCount(0, FEED_NEWEST, FEED_NEWEST);
        assertCount(0, POST_DETAIL, POST_DETAIL);
        assertCount(4, COMMENTS, FEED_NEWEST, POST_DETAIL);
        assertEquals(3, logs.size());
    }

    @Test
    public void replayRunResetRestoresWarmStartAndKeepsOldEventRejected() {
        collector.beginReplayRun();
        collector.beginReplaySession(11);
        visit(FEED_NEWEST);
        NavigationEvent oldPost = visit(POST_DETAIL);
        visit(COMMENTS);
        assertCount(4, COMMENTS, FEED_NEWEST, POST_DETAIL);

        collector.endReplayRun();
        collector.beginReplayRun();
        engine.resetSession();
        collector.beginReplaySession(11);
        visit(FEED_NEWEST);
        engine.onNavigationEvent(oldPost);
        assertCount(6, POST_DETAIL, FEED_NEWEST);
        assertCount(5, COMMENTS, POST_DETAIL);
        assertCount(3, COMMENTS, FEED_NEWEST, POST_DETAIL);
    }

    @Test
    public void predictionAndDispatchKeepTheSixPolicyRules() {
        for (PrefetchEngine.Policy policy : PrefetchEngine.Policy.values()) {
            engine.resetSession();
            engine.setCurrentPolicy(policy);
            engine.setCurrentNetwork(PrefetchEngine.NetworkProfile.WIFI);
            collector.confirmOutOfScopeScreen();
            dispatched.clear();
            visit(FEED_NEWEST);
            assertEquals(POST_DETAIL, engine.getLastPredictions().get(0).getKey());
            assertEquals(0.6, engine.getLastPredictions().get(0).getValue(), 0.000001);
            List<ScreenId> expected = policy == P0_NONE ? Collections.emptyList()
                    : policy == P1_ALL || policy == P5_ADAPTIVE_NETWORK ? Arrays.asList(POST_DETAIL, FEED_HOT, SEARCH)
                    : Collections.singletonList(POST_DETAIL);
            assertEquals(policy.name(), expected, dispatched);
        }

        // With 150 more detail transitions, the two other targets each have probability 2/160.
        engine.setCurrentPolicy(P0_NONE);
        for (int i = 0; i < 150; i++) {
            collector.confirmOutOfScopeScreen();
            visit(FEED_NEWEST);
            visit(POST_DETAIL);
        }
        engine.setCurrentPolicy(P5_ADAPTIVE_NETWORK);
        collector.confirmOutOfScopeScreen();
        dispatched.clear();
        visit(FEED_NEWEST);
        assertEquals(Collections.singletonList(POST_DETAIL), dispatched);

        engine.setCurrentNetwork(PrefetchEngine.NetworkProfile.EDGE);
        collector.confirmOutOfScopeScreen();
        dispatched.clear();
        visit(FEED_NEWEST);
        assertEquals(Arrays.asList(POST_DETAIL, FEED_HOT, SEARCH), dispatched);
    }

    @Test
    public void p4PredictsFromOrderTwoBeforeDispatchingItsTargets() {
        engine.setCurrentPolicy(P4_MARKOV_ORDER2);
        visit(FEED_NEWEST);
        dispatched.clear();
        visit(POST_DETAIL);

        List<Map.Entry<ScreenId, Double>> prediction = engine.getLastPredictions();
        assertEquals(FACT_CHECK, prediction.get(0).getKey());
        assertEquals(4.0 / 7.0, prediction.get(0).getValue(), 0.000001);
        assertEquals(Arrays.asList(FACT_CHECK, COMMENTS), dispatched);
    }

    @Test
    public void realModeStillPredictsWithoutDispatchingSimulatedPrefetch() {
        engine.setCurrentPolicy(P1_ALL);
        engine.setSimulationMode(false);
        visit(FEED_NEWEST);
        assertFalse(engine.getLastPredictions().isEmpty());
        assertTrue(dispatched.isEmpty());
    }

    private NavigationEvent visit(ScreenId screen) {
        NavigationEvent event = collector.confirmScreen(screen).get();
        engine.onNavigationEvent(event);
        timeMs++;
        return event;
    }

    private void assertCount(int expected, ScreenId next, ScreenId... context) {
        assertEquals(Arrays.toString(context) + " -> " + next, expected,
                engine.getTransitionCount(Arrays.asList(context), next));
    }
}
