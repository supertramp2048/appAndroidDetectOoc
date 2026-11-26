package com.socialapp.core.telemetry;

import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.CancellationException;

import static com.socialapp.core.prefetch.ScreenId.FEED_HOT;
import static com.socialapp.core.prefetch.ScreenId.POST_DETAIL;
import static com.socialapp.core.telemetry.DataLoadEvent.Outcome.CANCELLED;
import static com.socialapp.core.telemetry.DataLoadEvent.Outcome.FAILURE;
import static com.socialapp.core.telemetry.DataLoadEvent.Outcome.SUCCESS;
import static com.socialapp.core.telemetry.LoadContext.Trigger.REFRESH;
import static com.socialapp.core.telemetry.LoadContext.Trigger.UNKNOWN;
import static org.junit.Assert.*;

public class DataLoadCorrelationTest {
    private long now = 100;
    private final TelemetryCollector collector = new TelemetryCollector(() -> now);

    @Test
    public void sameSessionLoadUsesStartIdentityAndMonotonicDuration() {
        NavigationEvent nav = collector.confirmScreen(FEED_HOT).get();
        LoadContext load = collector.beginLoad(FEED_HOT, REFRESH, nav);
        now += 35;
        DataLoadEvent result = load.complete(now, SUCCESS);
        assertEquals(35, result.loadLatencyMs);
        assertEquals(100, result.context.startedAtMs);
        assertEquals(nav.runId, result.context.runId);
        assertEquals(nav.sessionId, result.context.sessionId);
        assertEquals(nav.navigationId, result.context.navigationId);
        collector.withLoadSession(load, current -> assertTrue(current));
    }

    @Test
    public void completionAfterSessionChangeKeepsOriginalIdentity() {
        collector.beginReplayRun();
        collector.beginReplaySession(11);
        LoadContext old = collector.beginLoad(FEED_HOT, UNKNOWN, null);
        collector.endReplaySession();
        collector.beginReplaySession(12);
        LoadContext next = collector.beginLoad(FEED_HOT, UNKNOWN, null);

        DataLoadEvent result = old.complete(now + 40, SUCCESS);
        assertSame(old, result.context);
        assertEquals(Integer.valueOf(11), result.context.replaySessionId);
        assertNotEquals(next.sessionId, result.context.sessionId);
        assertEquals(next.runId, result.context.runId);
        collector.withLoadSession(old, current -> assertFalse(current));
        collector.withLoadSession(next, current -> assertTrue(current));
    }

    @Test
    public void completionAfterRunChangeKeepsItsRunEvenWithSameTraceSessionNumber() {
        UUID firstRun = collector.beginReplayRun();
        collector.beginReplaySession(11);
        LoadContext load = collector.beginLoad(FEED_HOT, UNKNOWN, null);
        collector.endReplayRun();
        UUID secondRun = collector.beginReplayRun();
        collector.beginReplaySession(11);
        LoadContext other = collector.beginLoad(FEED_HOT, UNKNOWN, null);

        assertEquals(firstRun, load.complete(now + 60, SUCCESS).context.runId);
        assertEquals(secondRun, other.runId);
        assertEquals(load.replaySessionId, other.replaySessionId);
        assertNotEquals(load.sessionId, other.sessionId);
        collector.withLoadSession(load, current -> assertFalse(current));
    }

    @Test
    public void outOfOrderCompletionsKeepTheirOwnIdScreenAndDuration() {
        LoadContext first = collector.beginLoad(FEED_HOT, UNKNOWN, null);
        now += 10;
        LoadContext second = collector.beginLoad(POST_DETAIL, UNKNOWN, null);
        DataLoadEvent secondResult = second.complete(now + 20, SUCCESS);
        DataLoadEvent firstResult = first.complete(now + 80, SUCCESS);

        assertNotEquals(firstResult.context.loadId, secondResult.context.loadId);
        assertEquals(FEED_HOT, firstResult.context.screen);
        assertEquals(POST_DETAIL, secondResult.context.screen);
        assertEquals(90, firstResult.loadLatencyMs);
        assertEquals(20, secondResult.loadLatencyMs);
    }

    @Test
    public void refreshUsesTheExistingEventWithoutAdvancingNavigationSequence() {
        NavigationEvent first = collector.confirmScreen(FEED_HOT).get();
        LoadContext refresh = collector.beginLoad(FEED_HOT, REFRESH, first);
        refresh.complete(now, SUCCESS);
        assertEquals(first.navigationId, refresh.navigationId);
        assertFalse(collector.confirmScreen(FEED_HOT).isPresent());
        assertEquals(2, collector.confirmScreen(POST_DETAIL).get().sequence);
    }

    @Test
    public void earlyLoadAndUnprovenSameScreenLoadStayUnlinked() {
        NavigationEvent previous = collector.confirmScreen(FEED_HOT).get();
        LoadContext early = collector.beginLoad(POST_DETAIL, UNKNOWN, previous);
        assertNull(collector.beginLoad(FEED_HOT, UNKNOWN, null).navigationId);
        NavigationEvent destination = collector.confirmScreen(POST_DETAIL).get();
        assertNull(early.complete(now, SUCCESS).context.navigationId);
        assertEquals(destination.navigationId, collector.beginLoad(POST_DETAIL, REFRESH, destination).navigationId);
        collector.confirmOutOfScopeScreen();
        assertNull(collector.beginLoad(POST_DETAIL, REFRESH, destination).navigationId);
    }

    @Test
    public void errorAndCancellationCannotBeOverwrittenByLaterSuccess() {
        LoadContext failed = collector.beginLoad(POST_DETAIL, UNKNOWN, null);
        LoadContext cancelled = collector.beginLoad(FEED_HOT, UNKNOWN, null);
        assertEquals(FAILURE, failed.complete(now + 10,
                DataLoadEvent.Outcome.fromError(new IllegalStateException())).outcome);
        assertEquals(CANCELLED, cancelled.complete(now + 10,
                DataLoadEvent.Outcome.fromError(new CancellationException())).outcome);
        assertEquals(CANCELLED, DataLoadEvent.Outcome.fromError(new InterruptedException()));
        assertNull(failed.complete(now + 15, SUCCESS));
        assertNull(cancelled.complete(now + 15, SUCCESS));
    }

    @Test
    public void liveLoadBeforeFirstConfirmationSharesItsSessionAndDoesNotExtendInactivity() {
        LoadContext early = collector.beginLoad(FEED_HOT, UNKNOWN, null);
        now++;
        NavigationEvent first = collector.confirmScreen(FEED_HOT).get();
        assertEquals(early.sessionId, first.sessionId);
        assertNull(early.navigationId);
        now += 29 * 60_000;
        assertEquals(first.sessionId, collector.beginLoad(FEED_HOT, UNKNOWN, null).sessionId);
        now += 60_000;
        LoadContext next = collector.beginLoad(FEED_HOT, REFRESH, first);
        assertNotEquals(first.sessionId, next.sessionId);
        assertNull(next.navigationId);
    }

    @Test
    public void loadInReplayGapHasRunButNoSessionOrNavigation() {
        UUID run = collector.beginReplayRun();
        LoadContext gap = collector.beginLoad(FEED_HOT, UNKNOWN, null);
        assertEquals(run, gap.runId);
        assertEquals(EventSource.REPLAY, gap.source);
        assertNull(gap.sessionId);
        assertNull(gap.navigationId);
        collector.withLoadSession(gap, current -> assertFalse(current));
    }
}
