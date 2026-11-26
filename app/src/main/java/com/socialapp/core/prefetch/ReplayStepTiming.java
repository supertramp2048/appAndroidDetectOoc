package com.socialapp.core.prefetch;

import java.util.function.LongSupplier;

/** Active-time deadlines for one replay request. */
final class ReplayStepTiming {
    private final LongSupplier clock;
    final long requestId;
    final ScreenId expected;
    final long dwellMs;
    private final long timeoutMs;
    private long deadlineMs;
    private long pausedRemainingMs = -1;
    private long confirmationLatencyMs = -1;

    ReplayStepTiming(LongSupplier clock, long requestId, ScreenId expected, long timeoutMs, long dwellMs) {
        this.clock = clock;
        this.requestId = requestId;
        this.expected = expected;
        this.timeoutMs = timeoutMs;
        this.dwellMs = dwellMs;
        deadlineMs = clock.getAsLong() + timeoutMs;
    }

    boolean confirm(long observedRequestId, ScreenId screen) {
        if (!isWaiting() || pausedRemainingMs >= 0 || observedRequestId != requestId || screen != expected) {
            return false;
        }
        long now = clock.getAsLong();
        if (now >= deadlineMs) {
            return false;
        }
        confirmationLatencyMs = timeoutMs - (deadlineMs - now);
        deadlineMs = now + dwellMs;
        return true;
    }

    boolean isWaiting() {
        return confirmationLatencyMs < 0;
    }

    boolean canCompleteDwell(ScreenId visibleScreen) {
        return !isWaiting() && pausedRemainingMs < 0 && remainingMs() == 0 && visibleScreen == expected;
    }

    long getConfirmationLatencyMs() {
        return confirmationLatencyMs;
    }

    long remainingMs() {
        return pausedRemainingMs >= 0 ? pausedRemainingMs : Math.max(0, deadlineMs - clock.getAsLong());
    }

    void pause() {
        pausedRemainingMs = remainingMs();
    }

    void resume() {
        if (pausedRemainingMs >= 0) {
            deadlineMs = clock.getAsLong() + pausedRemainingMs;
            pausedRemainingMs = -1;
        }
    }
}
