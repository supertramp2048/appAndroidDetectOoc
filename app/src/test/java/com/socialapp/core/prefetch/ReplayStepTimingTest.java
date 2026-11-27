package com.socialapp.core.prefetch;

import org.junit.Test;

import static com.socialapp.core.prefetch.ScreenId.FEED_HOT;
import static com.socialapp.core.prefetch.ScreenId.FEED_NEWEST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReplayStepTimingTest {
    private long nowMs;

    @Test
    public void dwellStartsAtConfirmation() {
        ReplayStepTiming step = newStep(7);
        nowMs = 350;
        assertTrue(step.isWaiting());
        assertEquals(650, step.remainingMs());
        assertTrue(step.confirm(7, FEED_HOT));
        assertEquals(350, step.getConfirmationLatencyMs());
        assertEquals(200, step.remainingMs());
        nowMs = 550;
        assertEquals(0, step.remainingMs());
    }

    @Test
    public void wrongAndLateCallbacksCannotConfirmANewStepOfTheSameScreen() {
        ReplayStepTiming previous = newStep(7);
        assertTrue(previous.confirm(7, FEED_HOT));
        ReplayStepTiming current = newStep(8);
        assertFalse(current.confirm(7, FEED_HOT));
        assertFalse(current.confirm(8, FEED_NEWEST));
        assertTrue(current.isWaiting());
        assertTrue(current.confirm(8, FEED_HOT));
        nowMs = 50;
        assertFalse(current.confirm(8, FEED_HOT));
        assertEquals(150, current.remainingMs());
    }

    @Test
    public void expiredConfirmationDeadlineRejectsEvenTheExpectedDestination() {
        ReplayStepTiming step = newStep(7);
        nowMs = 1_000;
        assertEquals(0, step.remainingMs());
        assertFalse(step.confirm(7, FEED_HOT));
        assertTrue(step.isWaiting());
    }

    @Test
    public void pauseFreezesTimeoutAndExcludesPausedTimeFromConfirmationLatency() {
        ReplayStepTiming step = newStep(7);
        nowMs = 250;
        step.pause();
        nowMs = 10_250;
        assertFalse(step.confirm(7, FEED_HOT));
        assertEquals(750, step.remainingMs());
        step.resume();
        nowMs += 50;
        assertTrue(step.confirm(7, FEED_HOT));
        assertEquals(300, step.getConfirmationLatencyMs());
    }

    @Test
    public void pauseFreezesRemainingDwellWithoutConfirmingAgain() {
        ReplayStepTiming step = newStep(7);
        assertTrue(step.confirm(7, FEED_HOT));
        nowMs = 80;
        step.pause();
        nowMs = 10_080;
        assertEquals(120, step.remainingMs());
        step.resume();
        assertFalse(step.confirm(7, FEED_HOT));
        nowMs = 10_200;
        assertEquals(0, step.remainingMs());
    }

    @Test
    public void finishingDwellRequiresTheConfirmedScreenToStillBeVisible() {
        ReplayStepTiming step = newStep(7);
        assertFalse(step.canCompleteDwell(FEED_HOT));
        assertTrue(step.confirm(7, FEED_HOT));
        assertFalse(step.canCompleteDwell(FEED_HOT));
        nowMs = 200;
        assertFalse(step.canCompleteDwell(FEED_NEWEST));
        assertFalse(step.canCompleteDwell(null));
        assertTrue(step.canCompleteDwell(FEED_HOT));
    }

    private ReplayStepTiming newStep(long requestId) {
        return new ReplayStepTiming(() -> nowMs, requestId, FEED_HOT, 1_000, 200);
    }
}
