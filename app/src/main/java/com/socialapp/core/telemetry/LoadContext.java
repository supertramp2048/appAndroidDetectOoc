package com.socialapp.core.telemetry;

import com.socialapp.core.prefetch.ScreenId;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Attribution captured once, before dispatching a foreground data load. */
public final class LoadContext {
    public enum Trigger { UNKNOWN, INITIAL, REFRESH, RETRY }

    public final UUID loadId = UUID.randomUUID();
    public final UUID runId;
    public final UUID sessionId;
    public final Integer replaySessionId;
    public final UUID navigationId;
    public final ScreenId screen;
    public final EventSource source;
    public final Trigger trigger;
    public final long startedAtMs;

    private final AtomicBoolean completed = new AtomicBoolean();

    LoadContext(UUID runId, UUID sessionId, Integer replaySessionId, UUID navigationId,
                ScreenId screen, EventSource source, Trigger trigger, long startedAtMs) {
        this.runId = runId;
        this.sessionId = sessionId;
        this.replaySessionId = replaySessionId;
        this.navigationId = navigationId;
        this.screen = screen;
        this.source = source;
        this.trigger = trigger;
        this.startedAtMs = startedAtMs;
    }

    public DataLoadEvent complete(long finishedAtMs, DataLoadEvent.Outcome outcome) {
        if (!completed.compareAndSet(false, true)) return null;
        return new DataLoadEvent(this, finishedAtMs, outcome);
    }
}
