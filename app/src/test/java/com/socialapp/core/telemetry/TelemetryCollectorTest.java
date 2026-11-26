package com.socialapp.core.telemetry;

import com.socialapp.core.prefetch.ScreenId;

import org.junit.Test;

import java.util.UUID;

import static com.socialapp.core.prefetch.ScreenId.COMMENTS;
import static com.socialapp.core.prefetch.ScreenId.FEED_NEWEST;
import static com.socialapp.core.prefetch.ScreenId.POST_DETAIL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class TelemetryCollectorTest {
    private static final long THIRTY_MINUTES_MS = 30 * 60 * 1000L;

    private long nowMs = 100;
    private final TelemetryCollector collector = new TelemetryCollector(() -> nowMs);

    @Test
    public void firstScreenStartsLiveSessionWithoutPredecessor() {
        NavigationEvent event = confirm(FEED_NEWEST);

        assertNull(event.fromScreen);
        assertEquals(FEED_NEWEST, event.toScreen);
        assertEquals(1, event.sequence);
        assertEquals(nowMs, event.timestampMs);
        assertEquals(EventSource.LIVE, event.source);
        assertNull(event.replaySessionId);
        assertNotNull(event.navigationId);
        assertNotNull(event.sessionId);
        assertNotNull(event.runId);
    }

    @Test
    public void screenChangeEmitsTransitionInSameSession() {
        NavigationEvent first = confirm(FEED_NEWEST);
        nowMs += 10;
        NavigationEvent second = confirm(POST_DETAIL);

        assertEquals(FEED_NEWEST, second.fromScreen);
        assertEquals(POST_DETAIL, second.toScreen);
        assertEquals(first.sessionId, second.sessionId);
        assertEquals(first.runId, second.runId);
        assertNotEquals(first.navigationId, second.navigationId);
        assertEquals(nowMs, second.timestampMs);
    }

    @Test
    public void duplicateScreenDoesNotConsumeSequence() {
        confirm(FEED_NEWEST);
        nowMs++;
        assertFalse(collector.confirmScreen(FEED_NEWEST).isPresent());

        NavigationEvent next = confirm(POST_DETAIL);
        assertEquals(FEED_NEWEST, next.fromScreen);
        assertEquals(2, next.sequence);
    }

    @Test
    public void backToPreviousScreenEmitsSecondTransition() {
        NavigationEvent first = confirm(FEED_NEWEST);
        NavigationEvent second = confirm(POST_DETAIL);
        NavigationEvent back = confirm(FEED_NEWEST);

        assertEquals(FEED_NEWEST, second.fromScreen);
        assertEquals(POST_DETAIL, second.toScreen);
        assertEquals(POST_DETAIL, back.fromScreen);
        assertEquals(FEED_NEWEST, back.toScreen);
        assertEquals(first.sessionId, back.sessionId);
        assertEquals(2, second.sequence);
        assertEquals(3, back.sequence);
    }

    @Test
    public void newReplaySessionBreaksChainAndResetsSequence() {
        UUID runId = collector.beginReplayRun();
        collector.beginReplaySession(7);
        NavigationEvent first = confirm(POST_DETAIL);

        collector.endReplaySession();
        assertFalse(collector.confirmScreen(FEED_NEWEST).isPresent());
        collector.beginReplaySession(8);
        NavigationEvent second = confirm(POST_DETAIL);

        assertEquals(runId, first.runId);
        assertEquals(first.runId, second.runId);
        assertNotEquals(first.sessionId, second.sessionId);
        assertEquals(EventSource.REPLAY, second.source);
        assertEquals(Integer.valueOf(8), second.replaySessionId);
        assertNull(second.fromScreen);
        assertEquals(1, second.sequence);
    }

    @Test
    public void outOfScopeScreenBreaksChainWithoutOpeningSession() {
        NavigationEvent first = confirm(FEED_NEWEST);
        collector.confirmOutOfScopeScreen();
        NavigationEvent returned = confirm(FEED_NEWEST);
        NavigationEvent next = confirm(COMMENTS);

        assertNull(returned.fromScreen);
        assertEquals(first.sessionId, returned.sessionId);
        assertEquals(2, returned.sequence);
        assertEquals(FEED_NEWEST, next.fromScreen);
        assertEquals(3, next.sequence);
    }

    @Test
    public void sameTraceSessionIdInSeparateRunsHasSeparateIdentity() {
        collector.beginReplayRun();
        collector.beginReplaySession(7);
        NavigationEvent first = confirm(FEED_NEWEST);
        collector.endReplayRun();

        collector.beginReplayRun();
        collector.beginReplaySession(7);
        NavigationEvent second = confirm(FEED_NEWEST);

        assertEquals(Integer.valueOf(7), first.replaySessionId);
        assertEquals(first.replaySessionId, second.replaySessionId);
        assertNotEquals(first.runId, second.runId);
        assertNotEquals(first.sessionId, second.sessionId);
        assertEquals(1, second.sequence);
        assertNull(second.fromScreen);
    }

    @Test
    public void liveGapBelowThirtyMinutesDoesNotEmitDuplicateOnReturn() {
        NavigationEvent first = confirm(FEED_NEWEST);
        nowMs += THIRTY_MINUTES_MS - 1;
        assertFalse(collector.confirmScreen(FEED_NEWEST).isPresent());
        NavigationEvent next = confirm(POST_DETAIL);

        assertEquals(first.sessionId, next.sessionId);
        assertEquals(FEED_NEWEST, next.fromScreen);
        assertEquals(2, next.sequence);
    }

    @Test
    public void liveGapOfThirtyMinutesStartsNewSessionEvenOnSameScreen() {
        NavigationEvent first = confirm(FEED_NEWEST);
        nowMs += THIRTY_MINUTES_MS;
        NavigationEvent next = confirm(FEED_NEWEST);

        assertEquals(first.runId, next.runId);
        assertNotEquals(first.sessionId, next.sessionId);
        assertNull(next.fromScreen);
        assertEquals(1, next.sequence);
        assertEquals(nowMs, next.timestampMs);
    }

    @Test
    public void liveActivityExtendsSessionWithoutConsumingSequence() {
        NavigationEvent first = confirm(FEED_NEWEST);
        nowMs += THIRTY_MINUTES_MS - 1;
        collector.recordLiveActivity();
        nowMs += THIRTY_MINUTES_MS - 1;
        NavigationEvent next = confirm(POST_DETAIL);

        assertEquals(first.sessionId, next.sessionId);
        assertEquals(2, next.sequence);
    }

    @Test
    public void interactionsOnSameScreenKeepLiveSessionAcrossSeveralTimeoutWindows() {
        NavigationEvent first = confirm(FEED_NEWEST);
        for (int i = 0; i < 5; i++) {
            nowMs += THIRTY_MINUTES_MS / 2;
            collector.recordLiveActivity();
        }
        NavigationEvent next = confirm(POST_DETAIL);

        assertEquals(first.sessionId, next.sessionId);
        assertEquals(FEED_NEWEST, next.fromScreen);
        assertEquals(2, next.sequence);
    }

    @Test
    public void replaySessionWaitsForExplicitBoundaryInsteadOfLiveTimeout() {
        collector.beginReplayRun();
        assertFalse(collector.confirmScreen(FEED_NEWEST).isPresent());
        collector.beginReplaySession(7);
        NavigationEvent first = confirm(FEED_NEWEST);
        nowMs += THIRTY_MINUTES_MS * 2;
        collector.recordLiveActivity();
        NavigationEvent second = confirm(POST_DETAIL);

        assertEquals(first.sessionId, second.sessionId);
        assertEquals(FEED_NEWEST, second.fromScreen);
        assertEquals(2, second.sequence);
    }

    @Test
    public void endingReplayStartsFreshLiveContext() {
        NavigationEvent live = confirm(FEED_NEWEST);
        collector.beginReplayRun();
        collector.beginReplaySession(7);
        confirm(POST_DETAIL);
        collector.endReplayRun();
        NavigationEvent returned = confirm(COMMENTS);

        assertEquals(EventSource.LIVE, returned.source);
        assertNull(returned.replaySessionId);
        assertEquals(live.runId, returned.runId);
        assertNotEquals(live.sessionId, returned.sessionId);
        assertNull(returned.fromScreen);
        assertEquals(1, returned.sequence);
    }

    @Test
    public void freshCollectorHasIndependentLiveIdentity() {
        NavigationEvent first = confirm(FEED_NEWEST);
        NavigationEvent fresh = new TelemetryCollector(() -> nowMs).confirmScreen(FEED_NEWEST).get();

        assertNotEquals(first.runId, fresh.runId);
        assertNotEquals(first.sessionId, fresh.sessionId);
        assertNull(fresh.fromScreen);
    }

    @Test
    public void activeReplaySessionMustCloseBeforeOpeningAnother() {
        collector.beginReplayRun();
        collector.beginReplaySession(7);
        NavigationEvent first = confirm(FEED_NEWEST);

        assertThrows(IllegalStateException.class, () -> collector.beginReplaySession(8));
        assertEquals(first.sessionId, confirm(POST_DETAIL).sessionId);
        collector.endReplaySession();
        collector.beginReplaySession(8);
        assertNull(confirm(POST_DETAIL).fromScreen);
    }

    @Test
    public void replayLifecycleRejectsStartingSessionWithoutRunOrNestedRun() {
        assertThrows(IllegalStateException.class, () -> collector.beginReplaySession(7));
        collector.beginReplayRun();
        assertThrows(IllegalStateException.class, collector::beginReplayRun);
    }

    private NavigationEvent confirm(ScreenId screen) {
        return collector.confirmScreen(screen).get();
    }
}
