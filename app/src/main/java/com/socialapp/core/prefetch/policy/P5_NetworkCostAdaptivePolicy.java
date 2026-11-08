package com.socialapp.core.prefetch.policy;

import androidx.annotation.NonNull;

import com.socialapp.core.network.sim.MockNetworkEngine;
import com.socialapp.core.network.sim.NetworkProfile;
import com.socialapp.core.network.sim.PayloadWeight;
import com.socialapp.core.network.sim.ScreenPayloadConfig;
import com.socialapp.core.prefetch.IPrefetchPolicy;
import com.socialapp.core.prefetch.PrefetchConfig;
import com.socialapp.core.prefetch.policy.markov.MarkovTransitionTensor;
import com.socialapp.core.prefetch.policy.markov.ScreenProbability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * P5: Network-Cost Adaptive Prefetch Policy (Pareto Utility Optimization).
 * Formulated from Jiang & Kleinrock (1998) and Higgins et al. (MobiSys 2012 IMP).
 *
 * Computes the Expected Net Utility for each candidate transition:
 *   U(j) = P(j) * T_Saved(j) - lambda * CostFactor(Profile) * PayloadKb(j)
 *
 * Only prefetch candidates with positive net utility U(j) > 0.
 * Automatically curtails prefetching on slow/costly networks (2G/3G) to prevent
 * contention, while aggressively prefetching on fast networks (Wi-Fi).
 */
public class P5_NetworkCostAdaptivePolicy implements IPrefetchPolicy {

    public static final String POLICY_ID = PolicyType.P5_NETWORK_ADAPTIVE.getCode();

    private final MarkovTransitionTensor tensor;
    private final MockNetworkEngine networkEngine;
    private double riskAversionLambda; // lambda parameter

    public P5_NetworkCostAdaptivePolicy() {
        this(new MarkovTransitionTensor(new com.socialapp.core.prefetch.policy.markov.MarkovTransitionMatrix()),
                MockNetworkEngine.getInstance(), 1.0);
    }

    public P5_NetworkCostAdaptivePolicy(
            @NonNull MarkovTransitionTensor tensor,
            @NonNull MockNetworkEngine networkEngine,
            double riskAversionLambda) {
        this.tensor = tensor;
        this.networkEngine = networkEngine;
        this.riskAversionLambda = Math.max(0.1, riskAversionLambda);
    }

    public double getRiskAversionLambda() {
        return riskAversionLambda;
    }

    public void setRiskAversionLambda(double lambda) {
        this.riskAversionLambda = Math.max(0.1, lambda);
    }

    @NonNull
    @Override
    public String getPolicyId() {
        return POLICY_ID;
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return String.format(Locale.US, "P5: Thích ứng chi phí mạng (λ = %.2f)", riskAversionLambda);
    }

    @NonNull
    @Override
    public List<String> predictNextScreens(@NonNull String currentScreen, @NonNull List<String> history) {
        // 1. Get transitions from Markov model (Order 2 with Order 1 fallback)
        List<ScreenProbability> candidateProbabilities;
        if (history != null && history.size() >= 2) {
            String prev = history.get(history.size() - 2);
            candidateProbabilities = tensor.getTransitions(prev, currentScreen);
        } else {
            candidateProbabilities = tensor.getOrder1Fallback().getSortedTransitions(currentScreen);
        }

        if (candidateProbabilities.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. Query current live network state
        NetworkProfile profile = networkEngine.getCurrentProfile();
        PayloadWeight weight = networkEngine.getCurrentWeight();
        double costFactor = getNetworkCostFactor(profile);

        // 3. Evaluate Expected Net Utility for each candidate
        List<ScreenUtility> utilityList = new ArrayList<>();

        for (ScreenProbability sp : candidateProbabilities) {
            String candidateScreen = sp.getScreenName();
            double p = sp.getProbability();
            if (p <= 0.0) continue;

            double payloadKb = ScreenPayloadConfig.getBaseSizeKb(candidateScreen) * weight.getMultiplier();
            double transferTimeMs = (payloadKb * 8.0 * 1000.0) / profile.getBandwidthKbps();
            double totalSavedDelayMs = profile.getRttMs() + transferTimeMs;

            // Rigorous Decision Theory & Utility Formula:
            // Expected Gain = P * T_Saved - lambda * (1 - P) * WastedTransferTime * CostFactor
            double expectedBenefit = p * totalSavedDelayMs;
            double expectedRisk = riskAversionLambda * (1.0 - p) * transferTimeMs * costFactor;
            double netUtility = expectedBenefit - expectedRisk;

            if (netUtility > 0.0) {
                utilityList.add(new ScreenUtility(candidateScreen, netUtility));
            }
        }

        // Sort descending by highest net utility
        Collections.sort(utilityList);

        // 4. Return top-K candidates
        List<String> results = new ArrayList<>();
        int limit = Math.min(PrefetchConfig.MAX_PREFETCH_TARGETS, utilityList.size());
        for (int i = 0; i < limit; i++) {
            results.add(utilityList.get(i).screenName);
        }

        return results;
    }

    /**
     * Determines the relative penalty factor of spending bytes on a network profile.
     */
    private double getNetworkCostFactor(@NonNull NetworkProfile profile) {
        if (profile == NetworkProfile.WIFI) {
            return 0.15; // Cheap, abundant bandwidth, no data cap penalty
        } else if (profile == NetworkProfile.LTE_4G) {
            return 1.00; // Standard cellular baseline
        } else if (profile == NetworkProfile.NETWORK_3G) {
            return 3.50; // High congestion penalty, slow pipes
        } else if (profile == NetworkProfile.EDGE_2G) {
            return 12.0; // Severe congestion penalty; background prefetch chokes foreground
        }
        return 1.00;
    }

    private static class ScreenUtility implements Comparable<ScreenUtility> {
        final String screenName;
        final double utility;

        ScreenUtility(String screenName, double utility) {
            this.screenName = screenName;
            this.utility = utility;
        }

        @Override
        public int compareTo(@NonNull ScreenUtility o) {
            return Double.compare(o.utility, this.utility); // Descending
        }
    }
}
