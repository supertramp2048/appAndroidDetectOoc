package com.socialapp.core.prefetch;

import androidx.annotation.NonNull;

import com.socialapp.core.network.sim.MockNetworkEngine;
import com.socialapp.core.network.sim.NetworkProfile;
import com.socialapp.core.network.sim.PayloadWeight;
import com.socialapp.core.prefetch.policy.PolicyType;
import com.socialapp.core.prefetch.policy.PrefetchPolicyFactory;

import java.util.Locale;

/**
 * Task 5 Central Runtime Management & Switchboard:
 * Encapsulates system-wide toggles and configuration for Tasks 1, 2, 3, 4, and 5.
 * Enables one-line policy switching and batch scenario execution for Replay trials.
 */
public class PrefetchRuntimeConfig {

    private static volatile PrefetchRuntimeConfig instance;

    @NonNull
    public static PrefetchRuntimeConfig getInstance() {
        if (instance == null) {
            synchronized (PrefetchRuntimeConfig.class) {
                if (instance == null) {
                    instance = new PrefetchRuntimeConfig();
                }
            }
        }
        return instance;
    }

    private final PrefetchPolicyFactory policyFactory;
    private final MockNetworkEngine networkEngine;

    // Feature Flags (Independent Toggles)
    private volatile boolean isPrefetchEnabled = true;
    private volatile boolean isLateHitEnabled = true;

    // Active Policy and Parameters
    private volatile PolicyType activePolicyType = PolicyType.P2_MOST_FREQUENT;
    private volatile double markovThreshold = PrefetchConfig.DEFAULT_MARKOV_THRESHOLD;
    private volatile double riskAversionLambda = 1.0;
    private volatile long cacheTtlMs = PrefetchConfig.DEFAULT_TTL_MS;

    public PrefetchRuntimeConfig() {
        this.policyFactory = new PrefetchPolicyFactory();
        this.networkEngine = MockNetworkEngine.getInstance();
        refreshManagerPolicy();
    }

    // --- One-Line Policy Switching (Task C5 Acceptance Criteria) ---

    /**
     * Switches the active prefetch policy with a single method call.
     */
    public void setPolicy(@NonNull PolicyType type) {
        this.activePolicyType = type;
        if (type == PolicyType.P0_NO_PREFETCH) {
            this.isPrefetchEnabled = false;
        } else {
            this.isPrefetchEnabled = true;
        }
        refreshManagerPolicy();
    }

    /**
     * Switches the active prefetch policy by code name (e.g. "P0", "P1", "P5").
     */
    public void setPolicyByCode(@NonNull String code) {
        for (PolicyType type : PolicyType.values()) {
            if (type.getCode().equalsIgnoreCase(code)) {
                setPolicy(type);
                return;
            }
        }
    }

    // --- Independent Toggles ---

    public boolean isPrefetchEnabled() {
        return isPrefetchEnabled;
    }

    public void setPrefetchEnabled(boolean enabled) {
        this.isPrefetchEnabled = enabled;
        if (!enabled) {
            this.activePolicyType = PolicyType.P0_NO_PREFETCH;
        } else if (this.activePolicyType == PolicyType.P0_NO_PREFETCH) {
            this.activePolicyType = PolicyType.P2_MOST_FREQUENT;
        }
        refreshManagerPolicy();
    }

    public boolean isLateHitEnabled() {
        return isLateHitEnabled;
    }

    public void setLateHitEnabled(boolean enabled) {
        this.isLateHitEnabled = enabled;
    }

    public boolean isNetworkSimulationEnabled() {
        return networkEngine.isSimulationEnabled();
    }

    public void setNetworkSimulationEnabled(boolean enabled) {
        networkEngine.setSimulationEnabled(enabled);
    }

    // --- Tunable Parameters ---

    public double getMarkovThreshold() {
        return markovThreshold;
    }

    public void setMarkovThreshold(double threshold) {
        this.markovThreshold = Math.max(0.0, Math.min(1.0, threshold));
        refreshManagerPolicy();
    }

    public double getRiskAversionLambda() {
        return riskAversionLambda;
    }

    public void setRiskAversionLambda(double lambda) {
        this.riskAversionLambda = Math.max(0.1, lambda);
        refreshManagerPolicy();
    }

    public long getCacheTtlMs() {
        return cacheTtlMs;
    }

    public void setCacheTtlMs(long ttlMs) {
        this.cacheTtlMs = Math.max(1_000L, ttlMs);
    }

    @NonNull
    public PolicyType getActivePolicyType() {
        return activePolicyType;
    }

    @NonNull
    public IPrefetchPolicy getActivePolicy() {
        return policyFactory.createPolicy(activePolicyType, markovThreshold, riskAversionLambda);
    }

    @NonNull
    public PrefetchPolicyFactory getPolicyFactory() {
        return policyFactory;
    }

    private void refreshManagerPolicy() {
        IPrefetchPolicy policy = getActivePolicy();
        PrefetchManager.getInstance().setPolicy(policy);
    }

    // --- Batch Scenario Loader (For Huy's Replay Runner) ---

    /**
     * Applies a batch experiment scenario string in format:
     * "POLICY=P5;NETWORK=3G;WEIGHT=NORMAL;PREFETCH=TRUE;LATE_HIT=TRUE;THETA=0.5"
     */
    public void applyScenario(@NonNull String scenarioString) {
        if (scenarioString == null || scenarioString.trim().isEmpty()) return;

        String[] tokens = scenarioString.split(";");
        for (String rawToken : tokens) {
            String token = rawToken.trim();
            if (token.isEmpty() || !token.contains("=")) continue;

            String[] kv = token.split("=", 2);
            String key = kv[0].trim().toUpperCase(Locale.US);
            String value = kv[1].trim();

            switch (key) {
                case "POLICY":
                    setPolicyByCode(value);
                    break;
                case "NETWORK":
                    setNetworkProfileByCode(value);
                    break;
                case "WEIGHT":
                    setPayloadWeightByCode(value);
                    break;
                case "PREFETCH":
                    setPrefetchEnabled(Boolean.parseBoolean(value));
                    break;
                case "LATE_HIT":
                    setLateHitEnabled(Boolean.parseBoolean(value));
                    break;
                case "SIMULATION":
                    setNetworkSimulationEnabled(Boolean.parseBoolean(value));
                    break;
                case "THETA":
                    try { setMarkovThreshold(Double.parseDouble(value)); } catch (Exception ignored) {}
                    break;
                case "LAMBDA":
                    try { setRiskAversionLambda(Double.parseDouble(value)); } catch (Exception ignored) {}
                    break;
                case "TTL":
                    try { setCacheTtlMs(Long.parseLong(value)); } catch (Exception ignored) {}
                    break;
            }
        }
    }

    private void setNetworkProfileByCode(@NonNull String code) {
        String upper = code.toUpperCase(Locale.US);
        if (upper.contains("WIFI")) {
            networkEngine.setProfile(NetworkProfile.WIFI);
        } else if (upper.contains("4G") || upper.contains("LTE")) {
            networkEngine.setProfile(NetworkProfile.LTE_4G);
        } else if (upper.contains("3G")) {
            networkEngine.setProfile(NetworkProfile.NETWORK_3G);
        } else if (upper.contains("2G") || upper.contains("EDGE")) {
            networkEngine.setProfile(NetworkProfile.EDGE_2G);
        }
    }

    private void setPayloadWeightByCode(@NonNull String code) {
        String upper = code.toUpperCase(Locale.US);
        if (upper.contains("LIGHT") || upper.contains("0.5") || upper.contains("0_5")) {
            networkEngine.setWeight(PayloadWeight.LIGHT_0_5);
        } else if (upper.contains("HEAVY") || upper.contains("2.0") || upper.contains("2_0")) {
            networkEngine.setWeight(PayloadWeight.HEAVY_2_0);
        } else {
            networkEngine.setWeight(PayloadWeight.NORMAL_1_0);
        }
    }
}
