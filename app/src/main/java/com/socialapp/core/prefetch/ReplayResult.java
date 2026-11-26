package com.socialapp.core.prefetch;

import java.util.UUID;

/** Last replay result, detached from live metrics state. */
public final class ReplayResult {
    public enum Outcome { COMPLETED, STOPPED, INVALID }

    public final UUID runId;
    public final Outcome outcome;
    public final String reason;
    public final Configuration configuration;
    public final int confirmedSteps;
    public final int transitions;
    public final Metrics metrics;
    public final PrefetchEngine.LambdaSnapshot lambda;
    private volatile ExportStatus exportStatus;

    ReplayResult(UUID runId, Outcome outcome, String reason, Configuration configuration, int confirmedSteps,
                 int transitions, Metrics metrics, PrefetchEngine.LambdaSnapshot lambda) {
        this.runId = runId;
        this.outcome = outcome;
        this.reason = reason;
        this.configuration = configuration;
        this.confirmedSteps = confirmedSteps;
        this.transitions = transitions;
        this.metrics = metrics;
        this.lambda = lambda;
    }

    public ExportStatus getExportStatus() { return exportStatus; }

    void setExportStatus(ExportStatus status) { exportStatus = status; }

    public static final class ExportStatus {
        public final boolean terminalWritten;
        public final long droppedRows;
        public final long failedRows;

        ExportStatus(boolean terminalWritten, long droppedRows, long failedRows) {
            this.terminalWritten = terminalWritten;
            this.droppedRows = droppedRows;
            this.failedRows = failedRows;
        }

        public boolean isComplete() { return terminalWritten && droppedRows == 0 && failedRows == 0; }
    }

    public static final class Configuration {
        public final PrefetchEngine.Policy policy;
        public final PrefetchEngine.NetworkProfile network;
        public final float payloadScale;
        public final float timeScale;
        public final boolean simulationMode;
        final boolean defaultLambda;

        Configuration(PrefetchEngine engine) {
            policy = engine.getCurrentPolicy();
            network = engine.getCurrentNetwork();
            payloadScale = engine.getPayloadScale();
            timeScale = engine.getTimeScale();
            simulationMode = engine.isSimulationMode();
            defaultLambda = engine.usesDefaultLambdaConfig();
        }
    }

    public static final class Metrics {
        public final int fullHits;
        public final int lateHits;
        public final int misses;
        public final Double hitRate;
        public final Double averageLoadLatencyMs;
        public final Long simulatedWastedBytes;

        Metrics(int fullHits, int lateHits, int misses, long latencySumMs, Long simulatedWastedBytes) {
            this.fullHits = fullHits;
            this.lateHits = lateHits;
            this.misses = misses;
            int total = fullHits + lateHits + misses;
            hitRate = total == 0 ? null : (fullHits + lateHits) / (double) total;
            averageLoadLatencyMs = total == 0 ? null : latencySumMs / (double) total;
            this.simulatedWastedBytes = simulatedWastedBytes;
        }
    }
}
