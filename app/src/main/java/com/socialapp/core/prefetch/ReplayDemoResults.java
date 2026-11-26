package com.socialapp.core.prefetch;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** The latest eligible Calibration result for each demo mode. */
final class ReplayDemoResults {
    private enum Mode { P0, FIXED_LAMBDA, ADAPTIVE_LAMBDA }

    private final EnumMap<Mode, ReplayResult> results = new EnumMap<>(Mode.class);

    synchronized boolean record(ReplayResult result, int expectedSteps) {
        ReplayResult.Configuration config = result.configuration;
        ReplayResult.ExportStatus export = result.getExportStatus();
        if (result.outcome != ReplayResult.Outcome.COMPLETED || export == null || !export.isComplete()
                || result.confirmedSteps != expectedSteps || config == null || result.metrics == null
                || result.metrics.hitRate == null
                || !config.simulationMode || config.network != PrefetchEngine.NetworkProfile.LTE
                || config.payloadScale != 1f || config.timeScale != 5f) return false;

        if (config.policy == PrefetchEngine.Policy.P0_NONE) {
            results.put(Mode.P0, result);
            return true;
        } else if (config.policy == PrefetchEngine.Policy.P5_ADAPTIVE_NETWORK && config.defaultLambda
                && result.lambda != null) {
            if (result.lambda.mode == PrefetchEngine.P5Mode.FIXED_LAMBDA) {
                results.put(Mode.FIXED_LAMBDA, result);
                return true;
            }
            if (result.lambda.mode == PrefetchEngine.P5Mode.ADAPTIVE_LAMBDA) {
                results.put(Mode.ADAPTIVE_LAMBDA, result);
                return true;
            }
        }
        return false;
    }

    synchronized List<ReplayResult> snapshot() {
        return new ArrayList<>(results.values());
    }

    synchronized void restore(String json, int expectedSteps) {
        if (json == null) return;
        try {
            ReplayResult[] saved = new Gson().fromJson(json, ReplayResult[].class);
            if (saved == null) return;
            for (ReplayResult result : saved) {
                if (result != null && result.runId != null) record(result, expectedSteps);
            }
        } catch (JsonParseException ignored) {
            // The raw run CSV remains available if the small UI snapshot is damaged.
        }
    }

    synchronized String serialize() {
        return new Gson().toJson(results.values());
    }
}
