package com.socialapp.core.prefetch;

import org.junit.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ReplayDemoResultsTest {
    private final ReplayDemoResults results = new ReplayDemoResults();
    private final PrefetchEngine engine = new PrefetchEngine(
            new PrefetchCache(10, 0), Runnable::run, ignored -> {}, task -> {});

    @Test
    public void preservesEachModeWhenConfigurationChanges() {
        ReplayResult adaptive = run(PrefetchEngine.P5Mode.ADAPTIVE_LAMBDA);
        ReplayResult p0 = run(null);
        ReplayResult fixed = run(PrefetchEngine.P5Mode.FIXED_LAMBDA);
        for (ReplayResult result : Arrays.asList(adaptive, p0, fixed)) results.record(result, 50);
        assertEquals(Arrays.asList(p0, fixed, adaptive), results.snapshot());
        ReplayResult replacement = run(PrefetchEngine.P5Mode.FIXED_LAMBDA);
        results.record(replacement, 50);
        assertEquals(Arrays.asList(p0, replacement, adaptive), results.snapshot());
    }

    @Test
    public void rejectsWrongSetupOrIncompleteRuns() {
        ReplayResult valid = run(null);
        engine.setTimeScale(2f);
        results.record(result(null, ReplayResult.Outcome.COMPLETED, 50, true), 50);
        engine.setTimeScale(5f);
        engine.setSimulationMode(false);
        results.record(result(null, ReplayResult.Outcome.COMPLETED, 50, true), 50);
        engine.setSimulationMode(true);
        results.record(result(null, ReplayResult.Outcome.INVALID, 50, true), 50);
        results.record(result(null, ReplayResult.Outcome.STOPPED, 50, true), 50);
        results.record(result(null, ReplayResult.Outcome.COMPLETED, 49, true), 50);
        results.record(result(null, ReplayResult.Outcome.COMPLETED, 50, false), 50);
        results.record(run(PrefetchEngine.P5Mode.LEGACY), 50);
        assertTrue(results.snapshot().isEmpty());
        results.record(valid, 50);
        assertEquals(Arrays.asList(valid), results.snapshot());
    }

    @Test
    public void restoresEligibleRunsAcrossProcessRestart() {
        ReplayResult p0 = run(null);
        ReplayResult adaptive = run(PrefetchEngine.P5Mode.ADAPTIVE_LAMBDA);
        results.record(p0, 50);
        results.record(adaptive, 50);
        ReplayDemoResults restarted = new ReplayDemoResults();
        restarted.restore(results.serialize(), 50);
        assertEquals(2, restarted.snapshot().size());
        assertEquals(p0.runId, restarted.snapshot().get(0).runId);
        assertEquals(adaptive.runId, restarted.snapshot().get(1).runId);
        assertTrue(restarted.snapshot().get(1).getExportStatus().isComplete());
        restarted.restore("{invalid", 50);
        assertEquals(2, restarted.snapshot().size());
    }

    private ReplayResult run(PrefetchEngine.P5Mode mode) {
        engine.setCurrentNetwork(PrefetchEngine.NetworkProfile.LTE);
        engine.setSimulationMode(true);
        engine.setTimeScale(5f);
        engine.setPayloadScale(1f);
        engine.setCurrentPolicy(mode == null ? PrefetchEngine.Policy.P0_NONE
                : PrefetchEngine.Policy.P5_ADAPTIVE_NETWORK);
        return result(mode, ReplayResult.Outcome.COMPLETED, 50, true);
    }

    private ReplayResult result(PrefetchEngine.P5Mode mode, ReplayResult.Outcome outcome,
                                int steps, boolean complete) {
        PrefetchEngine.LambdaSnapshot lambda = mode == null ? null : new PrefetchEngine.LambdaSnapshot(
                mode, 0.000075, 0.00009, 1, 150, 60, 0.4, 0.5, AdaptiveLambdaController.Reason.HIGH_WASTE);
        ReplayResult result = new ReplayResult(UUID.randomUUID(), outcome, "test",
                new ReplayResult.Configuration(engine), steps, 40,
                new ReplayResult.Metrics(25, 0, 10, 100, 60L), lambda);
        result.setExportStatus(new ReplayResult.ExportStatus(complete, 0, 0));
        return result;
    }
}
