package com.socialapp.core.telemetry;

final class FrameSummary {
    long observed;
    long withDeadline;
    long jank;
    long platformDrops;
    private long timed;
    private long totalNs;
    private long maxNs;

    void add(long durationNs, long deadlineNs, int drops) {
        observed++;
        platformDrops += drops;
        if (durationNs < 0) return;
        timed++;
        totalNs += durationNs;
        maxNs = Math.max(maxNs, durationNs);
        if (deadlineNs > 0) {
            withDeadline++;
            if (durationNs >= deadlineNs) jank++;
        }
    }

    Double jankRate() { return withDeadline == 0 ? null : (double) jank / withDeadline; }
    Double meanMs() { return timed == 0 ? null : totalNs / (timed * 1_000_000.0); }
    Double maxMs() { return timed == 0 ? null : maxNs / 1_000_000.0; }
}
