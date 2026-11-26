package com.socialapp.core.prefetch;

import com.socialapp.core.telemetry.LoadContext;
import com.socialapp.core.telemetry.NavigationEvent;

import java.util.UUID;
import java.util.function.Consumer;

/** Run-scoped P5 feedback and tracing. Never calls back into the engine or cache. */
final class P5Experiment {
    static final double LEGACY_LAMBDA = 0.000005;
    static final String FILE_NAME = "p5_v1.csv";
    static final String HEADER = "schema_version,run_id,session_id,replay_session_id,navigation_id,"
            + "mode,event_type,prefetch_id,target,probability,estimated_load_time_ms,scaled_payload_bytes,"
            + "lambda,utility,selected,outcome_bytes,observation_index,old_lambda,new_lambda,waste_ratio,"
            + "miss_rate,resolved_count,successful_load_count,resolved_bytes,wasted_bytes,completed_bytes,"
            + "update_count,reason\n";

    enum Stage { CONSIDERED, DISPATCHED, COMPLETED, TERMINAL }

    static final class Attempt {
        final P5Experiment run;
        final long id;
        final NavigationEvent navigation;
        final ScreenId target;
        final long bytes;
        Stage stage = Stage.CONSIDERED;

        Attempt(P5Experiment run, long id, NavigationEvent navigation, ScreenId target, long bytes) {
            this.run = run;
            this.id = id;
            this.navigation = navigation;
            this.target = target;
            this.bytes = bytes;
        }

        void skip() { run.advance(this, Stage.CONSIDERED, Stage.TERMINAL, "SKIPPED", null); }
        void dispatch() { run.advance(this, Stage.CONSIDERED, Stage.DISPATCHED, "DISPATCHED", null); }
        void cancel() { run.advance(this, Stage.DISPATCHED, Stage.TERMINAL, "CANCELLED", null); }
        void fail() { run.advance(this, Stage.DISPATCHED, Stage.TERMINAL, "FAILED", null); }
        void complete() { run.advance(this, Stage.DISPATCHED, Stage.COMPLETED, "COMPLETED", bytes); }
        void use() { run.advance(this, Stage.COMPLETED, Stage.TERMINAL, "USED", bytes); }
        void waste() { run.advance(this, Stage.COMPLETED, Stage.TERMINAL, "WASTED", bytes); }
    }

    private final UUID runId;
    private final PrefetchEngine.P5Mode mode;
    private final AdaptiveLambdaController.Config config;
    private final AdaptiveLambdaController controller;
    private final Consumer<String> sink;
    private final double initial;
    private double lambda;
    private boolean sealed;
    private long nextAttempt;
    private long completedBytes;
    private long resolvedBytes;
    private long wastedBytes;
    private long successfulLoads;
    private long misses;
    private long windowResolvedBytes;
    private long windowWastedBytes;
    private int windowResolvedCount;
    private int windowLoads;
    private int windowMisses;
    private int observationIndex;
    private int updates;
    private AdaptiveLambdaController.Reason reason;

    P5Experiment(UUID runId, PrefetchEngine.P5Mode mode, AdaptiveLambdaController.Config config, Consumer<String> sink) {
        this.runId = runId;
        this.mode = mode;
        this.config = config;
        this.sink = sink;
        controller = new AdaptiveLambdaController(config);
        initial = mode == PrefetchEngine.P5Mode.LEGACY ? LEGACY_LAMBDA : config.initial;
        lambda = initial;
        reason = mode == PrefetchEngine.P5Mode.LEGACY ? AdaptiveLambdaController.Reason.LEGACY
                : mode == PrefetchEngine.P5Mode.FIXED_LAMBDA ? AdaptiveLambdaController.Reason.FIXED
                : AdaptiveLambdaController.Reason.WAITING_FOR_OBSERVATION;
    }

    boolean matches(UUID run) { return runId.equals(run); }
    PrefetchEngine.P5Mode mode() { return mode; }
    synchronized double lambda() { return lambda; }

    static double utility(double probability, long estimatedMs, double lambda, long bytes) {
        return probability * estimatedMs - lambda * bytes;
    }

    synchronized Attempt consider(NavigationEvent event, ScreenId target, double probability, long estimateMs,
                                  long scaledBytes, double decisionLambda, double utility, boolean selected) {
        Attempt attempt = new Attempt(this, ++nextAttempt, event, target, scaledBytes);
        emit("DECISION", attempt, probability, estimateMs, scaledBytes, decisionLambda, utility, selected, null);
        if (!selected) attempt.stage = Stage.TERMINAL;
        return attempt;
    }

    private synchronized void advance(Attempt attempt, Stage expected, Stage next, String event, Long bytes) {
        if (sealed || attempt.stage != expected) return;
        attempt.stage = next;
        if (next == Stage.COMPLETED) completedBytes += attempt.bytes;
        if (expected == Stage.COMPLETED) {
            resolvedBytes += attempt.bytes;
            windowResolvedBytes += attempt.bytes;
            windowResolvedCount++;
            if ("WASTED".equals(event)) {
                wastedBytes += attempt.bytes;
                windowWastedBytes += attempt.bytes;
            }
        }
        emit(event, attempt, null, null, null, null, null, null, bytes);
    }

    synchronized void foreground(LoadContext load, HitType hit) {
        if (sealed || !matches(load.runId)) return;
        successfulLoads++;
        windowLoads++;
        if (hit == HitType.MISS) {
            misses++;
            windowMisses++;
        }
        if (mode != PrefetchEngine.P5Mode.ADAPTIVE_LAMBDA || windowLoads < config.loadsPerObservation) return;

        double old = lambda;
        AdaptiveLambdaController.Decision decision = controller.evaluate(lambda, windowResolvedBytes,
                windowWastedBytes, windowResolvedCount, windowLoads, windowMisses);
        lambda = decision.lambda;
        reason = decision.reason;
        if (lambda != old) updates++;
        observationIndex++;
        emitSummary("OBSERVATION", load, observationIndex, old, lambda, windowResolvedBytes, windowWastedBytes,
                windowResolvedCount, windowLoads, windowMisses);
        windowResolvedBytes = 0;
        windowWastedBytes = 0;
        windowResolvedCount = 0;
        windowLoads = 0;
        windowMisses = 0;
    }

    synchronized PrefetchEngine.LambdaSnapshot snapshot() {
        return new PrefetchEngine.LambdaSnapshot(mode, initial, lambda, updates, completedBytes, wastedBytes,
                ratio(wastedBytes, resolvedBytes), ratio(misses, successfulLoads), reason);
    }

    synchronized PrefetchEngine.LambdaSnapshot finish() {
        if (!sealed) {
            sealed = true;
            emitSummary("RUN_SUMMARY", null, null, initial, lambda, resolvedBytes, wastedBytes, null, successfulLoads, misses);
        }
        return snapshot();
    }

    private void emit(String type, Attempt attempt, Object probability, Object estimate,
                      Object scaledBytes, Object decisionLambda, Object utility, Object selected, Object bytes) {
        NavigationEvent event = attempt.navigation;
        write(event.sessionId, event.replaySessionId, event.navigationId, type, attempt.id, attempt.target.getId(),
                probability, estimate, scaledBytes, decisionLambda, utility, selected, bytes, null, null, null,
                null, null, null, null, null, null, null, null, null);
    }

    private void emitSummary(String type, LoadContext load, Integer index, double old, double next, long resolved,
                             long wasted, Integer resolvedCount, long loads, long missCount) {
        write(load == null ? null : load.sessionId, load == null ? null : load.replaySessionId,
                load == null ? null : load.navigationId, type, null, null, null, null, null, null, null, null, null,
                index, old, next, ratio(wasted, resolved), ratio(missCount, loads), resolvedCount, loads, resolved, wasted,
                completedBytes, updates, reason);
    }

    private void write(Object session, Object traceSession, Object navigation, String type, Object... values) {
        StringBuilder row = new StringBuilder(208).append("1,").append(runId).append(',');
        row.append(session == null ? "" : session).append(',').append(traceSession == null ? "" : traceSession)
                .append(',').append(navigation == null ? "" : navigation).append(',').append(mode).append(',').append(type);
        for (Object value : values) {
            row.append(',');
            if (value != null) row.append(value);
        }
        sink.accept(row.append('\n').toString());
    }

    private static Double ratio(long numerator, long denominator) {
        return denominator == 0 ? null : (double) numerator / denominator;
    }
}
