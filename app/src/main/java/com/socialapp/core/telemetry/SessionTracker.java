package com.socialapp.core.telemetry;

import com.socialapp.core.prefetch.ScreenId;

import java.util.UUID;

/** Owns run identity, session identity, and the sequence within the active session. */
final class SessionTracker {
    private static final long LIVE_TIMEOUT_MS = 30 * 60 * 1000L;

    private final UUID liveRunId = UUID.randomUUID();
    private UUID replayRunId;
    private UUID sessionId;
    private Integer replaySessionId;
    private long sequence;
    private long lastLiveActivityMs;

    UUID getSessionId() {
        return sessionId;
    }

    UUID getRunId() {
        return replayRunId == null ? liveRunId : replayRunId;
    }

    Integer getReplaySessionId() {
        return replaySessionId;
    }

    EventSource getSource() {
        return replayRunId == null ? EventSource.LIVE : EventSource.REPLAY;
    }

    void recordLiveActivity(long timestampMs) {
        if (replayRunId != null) {
            return;
        }
        ensureLiveSession(timestampMs);
        lastLiveActivityMs = timestampMs;
    }

    void ensureLiveSession(long timestampMs) {
        if (replayRunId != null) return;
        if (sessionId == null || timestampMs - lastLiveActivityMs >= LIVE_TIMEOUT_MS) {
            sessionId = UUID.randomUUID();
            sequence = 0;
            lastLiveActivityMs = timestampMs;
        }
    }

    UUID beginReplayRun() {
        if (replayRunId != null) {
            throw new IllegalStateException("A replay run is already active");
        }
        replayRunId = UUID.randomUUID();
        clearSession();
        return replayRunId;
    }

    void beginReplaySession(int traceSessionId) {
        requireReplayRun();
        if (sessionId != null) {
            throw new IllegalStateException("Close the active replay session before opening another");
        }
        sessionId = UUID.randomUUID();
        replaySessionId = traceSessionId;
        sequence = 0;
    }

    void endReplaySession() {
        requireReplayRun();
        clearSession();
    }

    void endReplayRun() {
        endReplaySession();
        replayRunId = null;
    }

    NavigationEvent createEvent(ScreenId fromScreen, ScreenId toScreen, long timestampMs) {
        boolean isReplay = replayRunId != null;
        return new NavigationEvent(UUID.randomUUID(), isReplay ? replayRunId : liveRunId,
                sessionId, replaySessionId, ++sequence, fromScreen, toScreen, timestampMs,
                isReplay ? EventSource.REPLAY : EventSource.LIVE);
    }

    private void requireReplayRun() {
        if (replayRunId == null) {
            throw new IllegalStateException("No active replay run");
        }
    }

    private void clearSession() {
        sessionId = null;
        replaySessionId = null;
        sequence = 0;
    }
}
