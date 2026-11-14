package com.socialapp.core.prefetch.policy;

import androidx.annotation.NonNull;

import com.socialapp.core.network.sim.ScreenPayloadConfig;
import com.socialapp.core.prefetch.IPrefetchPolicy;
import com.socialapp.core.prefetch.PrefetchConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Policy P3 / P4: Markov Chain Prediction Policy.
 * Prepared for Kiên's transition matrix integration.
 * Evaluates transition probabilities P(S_next | S_current) >= threshold.
 */
public class MarkovStubPolicy implements IPrefetchPolicy {

    public static final String POLICY_ID = "P3_MARKOV_ORDER_1";

    private double threshold;
    // Map: CurrentScreen -> Map<CandidateScreen, TransitionProbability>
    private final Map<String, Map<String, Double>> transitionMatrix = new ConcurrentHashMap<>();

    public MarkovStubPolicy() {
        this(PrefetchConfig.DEFAULT_MARKOV_THRESHOLD);
    }

    public MarkovStubPolicy(double threshold) {
        this.threshold = Math.max(0.0, Math.min(1.0, threshold));
        initDefaultSampleProbabilities();
    }

    /**
     * Initializes baseline transition probabilities until Kiên's empirical matrix is plugged in.
     */
    private void initDefaultSampleProbabilities() {
        // From HOME: 85% PostDetail, 15% Search
        setProbability(ScreenPayloadConfig.SCREEN_HOME, ScreenPayloadConfig.SCREEN_POST_DETAIL, 0.85);
        setProbability(ScreenPayloadConfig.SCREEN_HOME, ScreenPayloadConfig.SCREEN_SEARCH, 0.15);

        // From POST_DETAIL: 75% Comments, 20% Profile, 5% FactCheck
        setProbability(ScreenPayloadConfig.SCREEN_POST_DETAIL, ScreenPayloadConfig.SCREEN_COMMENTS, 0.75);
        setProbability(ScreenPayloadConfig.SCREEN_POST_DETAIL, ScreenPayloadConfig.SCREEN_PROFILE, 0.20);
        setProbability(ScreenPayloadConfig.SCREEN_POST_DETAIL, ScreenPayloadConfig.SCREEN_FACTCHECK, 0.05);

        // From COMMENTS: 65% Profile, 35% PostDetail
        setProbability(ScreenPayloadConfig.SCREEN_COMMENTS, ScreenPayloadConfig.SCREEN_PROFILE, 0.65);
        setProbability(ScreenPayloadConfig.SCREEN_COMMENTS, ScreenPayloadConfig.SCREEN_POST_DETAIL, 0.35);

        // From PROFILE: 80% PostDetail, 20% Home
        setProbability(ScreenPayloadConfig.SCREEN_PROFILE, ScreenPayloadConfig.SCREEN_POST_DETAIL, 0.80);
        setProbability(ScreenPayloadConfig.SCREEN_PROFILE, ScreenPayloadConfig.SCREEN_HOME, 0.20);

        // From FACTCHECK: 90% PostDetail, 10% Home
        setProbability(ScreenPayloadConfig.SCREEN_FACTCHECK, ScreenPayloadConfig.SCREEN_POST_DETAIL, 0.90);
    }

    public void setProbability(@NonNull String fromScreen, @NonNull String toScreen, double probability) {
        transitionMatrix.computeIfAbsent(fromScreen, k -> new ConcurrentHashMap<>())
                .put(toScreen, Math.max(0.0, Math.min(1.0, probability)));
    }

    public void setThreshold(double threshold) {
        this.threshold = Math.max(0.0, Math.min(1.0, threshold));
    }

    public double getThreshold() {
        return threshold;
    }

    @NonNull
    @Override
    public String getPolicyId() {
        return POLICY_ID;
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return String.format("P3: Markov Bậc 1 (Ngưỡng θ = %.2f)", threshold);
    }

    @NonNull
    @Override
    public List<String> predictNextScreens(@NonNull String currentScreen, @NonNull List<String> history) {
        Map<String, Double> outgoing = transitionMatrix.get(currentScreen);
        if (outgoing == null || outgoing.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map.Entry<String, Double>> candidates = new ArrayList<>();
        for (Map.Entry<String, Double> entry : outgoing.entrySet()) {
            if (entry.getValue() >= threshold) {
                candidates.add(entry);
            }
        }

        // Sort descending by transition probability
        candidates.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Double> e : candidates) {
            result.add(e.getKey());
        }
        return result;
    }

    /**
     * Replaces the entire transition matrix with Kiên's empirical dataset.
     */
    public void loadEmpiricalMatrix(@NonNull Map<String, Map<String, Double>> newMatrix) {
        transitionMatrix.clear();
        for (Map.Entry<String, Map<String, Double>> e : newMatrix.entrySet()) {
            transitionMatrix.put(e.getKey(), new ConcurrentHashMap<>(e.getValue()));
        }
    }
}
