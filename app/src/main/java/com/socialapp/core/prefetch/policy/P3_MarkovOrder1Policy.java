package com.socialapp.core.prefetch.policy;

import androidx.annotation.NonNull;

import com.socialapp.core.prefetch.IPrefetchPolicy;
import com.socialapp.core.prefetch.PrefetchConfig;
import com.socialapp.core.prefetch.policy.markov.MarkovTransitionMatrix;

import java.util.List;

/**
 * P3: First-Order Markov Chain Policy with Probability Threshold theta.
 * Selects candidate screens where P(S_{t+1} | S_t) >= theta.
 * Directly integrates with Kiên's empirical transition matrix.
 */
public class P3_MarkovOrder1Policy implements IPrefetchPolicy {

    public static final String POLICY_ID = PolicyType.P3_MARKOV_ORDER_1.getCode();

    private final MarkovTransitionMatrix matrix;
    private double threshold;

    public P3_MarkovOrder1Policy() {
        this(new MarkovTransitionMatrix(), PrefetchConfig.DEFAULT_MARKOV_THRESHOLD);
    }

    public P3_MarkovOrder1Policy(@NonNull MarkovTransitionMatrix matrix, double threshold) {
        this.matrix = matrix;
        this.threshold = Math.max(0.0, Math.min(1.0, threshold));
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = Math.max(0.0, Math.min(1.0, threshold));
    }

    @NonNull
    public MarkovTransitionMatrix getMatrix() {
        return matrix;
    }

    @NonNull
    @Override
    public String getPolicyId() {
        return POLICY_ID;
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return String.format(java.util.Locale.US, "P3: Markov Bậc 1 (Ngưỡng θ = %.2f)", threshold);
    }

    @NonNull
    @Override
    public List<String> predictNextScreens(@NonNull String currentScreen, @NonNull List<String> history) {
        return matrix.getCandidatesAboveThreshold(currentScreen, threshold);
    }
}
