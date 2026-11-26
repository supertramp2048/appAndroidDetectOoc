package com.socialapp.core.telemetry;

import java.util.concurrent.CancellationException;

/** The terminal result of a Repository load, timed until data or an error is ready. */
public final class DataLoadEvent {
    public enum Outcome {
        SUCCESS, FAILURE, CANCELLED;

        public static Outcome fromError(Throwable error) {
            return error instanceof CancellationException || error instanceof InterruptedException
                    ? CANCELLED : FAILURE;
        }
    }

    public final LoadContext context;
    public final long finishedAtMs;
    public final long loadLatencyMs;
    public final Outcome outcome;

    DataLoadEvent(LoadContext context, long finishedAtMs, Outcome outcome) {
        this.context = context;
        this.finishedAtMs = finishedAtMs;
        this.loadLatencyMs = finishedAtMs - context.startedAtMs;
        this.outcome = outcome;
    }
}
