package com.socialapp.core.telemetry;

import com.socialapp.core.prefetch.ScreenId;

import java.util.UUID;

/** One confirmed navigation in the nine-screen research scope. */
public final class NavigationEvent {
    public final UUID navigationId;
    public final UUID runId;
    public final UUID sessionId;
    /** The session ID from the trace, or null for LIVE. */
    public final Integer replaySessionId;
    /** One-based order of emitted navigation events within the session. */
    public final long sequence;
    /** Null at the start of a session or after an out-of-scope screen. */
    public final ScreenId fromScreen;
    public final ScreenId toScreen;
    /** Confirmation time in monotonic milliseconds, not wall-clock time. */
    public final long timestampMs;
    public final EventSource source;

    NavigationEvent(UUID navigationId, UUID runId, UUID sessionId, Integer replaySessionId,
                    long sequence, ScreenId fromScreen, ScreenId toScreen,
                    long timestampMs, EventSource source) {
        this.navigationId = navigationId;
        this.runId = runId;
        this.sessionId = sessionId;
        this.replaySessionId = replaySessionId;
        this.sequence = sequence;
        this.fromScreen = fromScreen;
        this.toScreen = toScreen;
        this.timestampMs = timestampMs;
        this.source = source;
    }
}
