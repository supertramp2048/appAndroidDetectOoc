package com.socialapp.core.prefetch.policy;

import androidx.annotation.NonNull;

import com.socialapp.core.prefetch.IPrefetchPolicy;
import com.socialapp.core.prefetch.PrefetchConfig;
import com.socialapp.core.prefetch.policy.markov.MarkovTransitionMatrix;
import com.socialapp.core.prefetch.policy.markov.MarkovTransitionTensor;

import java.util.List;

/**
 * P4: Second-Order Markov Chain Policy with Katz Backoff Smoothing.
 * Conditioned on the 2-step navigation trajectory P(S_{t+1} | S_t, S_{t-1}) >= theta.
 * Automatically backs off to Order 1 when history is insufficient or sequence is novel.
 */
public class P4_MarkovOrder2Policy implements IPrefetchPolicy {

    public static final String POLICY_ID = PolicyType.P4_MARKOV_ORDER_2.getCode();

    private final MarkovTransitionTensor tensor;
    private double threshold;

    public P4_MarkovOrder2Policy() {
        this(new MarkovTransitionTensor(new MarkovTransitionMatrix()), PrefetchConfig.DEFAULT_MARKOV_THRESHOLD);
    }

    public P4_MarkovOrder2Policy(@NonNull MarkovTransitionTensor tensor, double threshold) {
        this.tensor = tensor;
        this.threshold = Math.max(0.0, Math.min(1.0, threshold));
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = Math.max(0.0, Math.min(1.0, threshold));
    }

    @NonNull
    public MarkovTransitionTensor getTensor() {
        return tensor;
    }

    @NonNull
    @Override
    public String getPolicyId() {
        return POLICY_ID;
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return String.format(java.util.Locale.US, "P4: Markov Bậc 2 (Ngưỡng θ = %.2f)", threshold);
    }

    @NonNull
    @Override
    public List<String> predictNextScreens(@NonNull String currentScreen, @NonNull List<String> history) {
        // If history contains at least 2 screens, use 2nd-order trajectory (S_{t-1}, S_t)
        if (history != null && history.size() >= 2) {
            String prev = history.get(history.size() - 2);
            return tensor.getCandidatesAboveThreshold(prev, currentScreen, threshold);
        }

        // Katz Backoff: fall back to 1st-order Markov
        return tensor.getOrder1Fallback().getCandidatesAboveThreshold(currentScreen, threshold);
    }
}
