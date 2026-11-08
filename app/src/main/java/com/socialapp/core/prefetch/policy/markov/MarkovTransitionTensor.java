package com.socialapp.core.prefetch.policy.markov;

import androidx.annotation.NonNull;

import com.socialapp.core.network.sim.ScreenPayloadConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Second-Order Markov Transition Tensor M^(2).
 * Models P(S_{t+1} | S_t, S_{t-1}) based on the 2-step navigation trajectory.
 * Incorporates Katz Backoff to Order 1 when second-order observations are absent or sparse.
 */
public class MarkovTransitionTensor {

    private final MarkovTransitionMatrix order1Fallback;

    // Tensor Count Map: "screenPrev->screenCurr" -> (CandidateScreen -> Count)
    private final Map<String, Map<String, AtomicInteger>> tensorCounts = new ConcurrentHashMap<>();

    // Explicit Tensor Probabilities: "screenPrev->screenCurr" -> (CandidateScreen -> Probability)
    private final Map<String, Map<String, Double>> explicitTensorProbabilities = new ConcurrentHashMap<>();

    public MarkovTransitionTensor(@NonNull MarkovTransitionMatrix order1Fallback) {
        this.order1Fallback = order1Fallback;
        initDefaultOrder2Baseline();
    }

    private String makeKey(@NonNull String prev, @NonNull String curr) {
        return prev + "->" + curr;
    }

    private void initDefaultOrder2Baseline() {
        // Trajectory 1: HOME -> POST_DETAIL: 80% read Comments, 15% Profile
        setExplicitProbability(ScreenPayloadConfig.SCREEN_HOME, ScreenPayloadConfig.SCREEN_POST_DETAIL,
                ScreenPayloadConfig.SCREEN_COMMENTS, 0.80);
        setExplicitProbability(ScreenPayloadConfig.SCREEN_HOME, ScreenPayloadConfig.SCREEN_POST_DETAIL,
                ScreenPayloadConfig.SCREEN_PROFILE, 0.15);

        // Trajectory 2: SEARCH -> POST_DETAIL: 60% check Profile (Author credentials), 35% Comments
        setExplicitProbability(ScreenPayloadConfig.SCREEN_SEARCH, ScreenPayloadConfig.SCREEN_POST_DETAIL,
                ScreenPayloadConfig.SCREEN_PROFILE, 0.60);
        setExplicitProbability(ScreenPayloadConfig.SCREEN_SEARCH, ScreenPayloadConfig.SCREEN_POST_DETAIL,
                ScreenPayloadConfig.SCREEN_COMMENTS, 0.35);

        // Trajectory 3: FACTCHECK -> POST_DETAIL: 85% check Comments (Debate / Discussions)
        setExplicitProbability(ScreenPayloadConfig.SCREEN_FACTCHECK, ScreenPayloadConfig.SCREEN_POST_DETAIL,
                ScreenPayloadConfig.SCREEN_COMMENTS, 0.85);

        // Trajectory 4: POST_DETAIL -> COMMENTS: 70% check author Profile, 25% back to PostDetail
        setExplicitProbability(ScreenPayloadConfig.SCREEN_POST_DETAIL, ScreenPayloadConfig.SCREEN_COMMENTS,
                ScreenPayloadConfig.SCREEN_PROFILE, 0.70);
        setExplicitProbability(ScreenPayloadConfig.SCREEN_POST_DETAIL, ScreenPayloadConfig.SCREEN_COMMENTS,
                ScreenPayloadConfig.SCREEN_POST_DETAIL, 0.25);

        // Trajectory 5: COMMENTS -> PROFILE: 85% view author's PostDetail feed
        setExplicitProbability(ScreenPayloadConfig.SCREEN_COMMENTS, ScreenPayloadConfig.SCREEN_PROFILE,
                ScreenPayloadConfig.SCREEN_POST_DETAIL, 0.85);
    }

    public void recordSequence(@NonNull String prev, @NonNull String curr, @NonNull String next) {
        String key = makeKey(prev, curr);
        tensorCounts.computeIfAbsent(key, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(next, k -> new AtomicInteger(0))
                .incrementAndGet();

        // Also train Order 1 simultaneously
        order1Fallback.recordTransition(curr, next);
    }

    public void setExplicitProbability(@NonNull String prev, @NonNull String curr, @NonNull String next, double prob) {
        String key = makeKey(prev, curr);
        explicitTensorProbabilities.computeIfAbsent(key, k -> new ConcurrentHashMap<>())
                .put(next, Math.max(0.0, Math.min(1.0, prob)));
    }

    public void loadTensor(@NonNull Map<String, Map<String, Double>> tensorData) {
        explicitTensorProbabilities.clear();
        for (Map.Entry<String, Map<String, Double>> e : tensorData.entrySet()) {
            explicitTensorProbabilities.put(e.getKey(), new ConcurrentHashMap<>(e.getValue()));
        }
    }

    /**
     * Retrieves candidate transitions conditioned on the 2-step history [prev, curr].
     * If the 2-step sequence has no observations, automatically applies Katz Backoff to Order 1.
     */
    @NonNull
    public List<ScreenProbability> getTransitions(@NonNull String prev, @NonNull String curr) {
        String key = makeKey(prev, curr);

        // 1. Check online counts
        Map<String, AtomicInteger> counts = tensorCounts.get(key);
        if (counts != null && !counts.isEmpty()) {
            long total = 0;
            for (AtomicInteger c : counts.values()) {
                total += c.get();
            }
            if (total > 0) {
                List<ScreenProbability> list = new ArrayList<>();
                for (Map.Entry<String, AtomicInteger> e : counts.entrySet()) {
                    list.add(new ScreenProbability(e.getKey(), (double) e.getValue().get() / total));
                }
                Collections.sort(list);
                return list;
            }
        }

        // 2. Check explicit probabilities
        Map<String, Double> explicit = explicitTensorProbabilities.get(key);
        if (explicit != null && !explicit.isEmpty()) {
            List<ScreenProbability> list = new ArrayList<>();
            for (Map.Entry<String, Double> e : explicit.entrySet()) {
                list.add(new ScreenProbability(e.getKey(), e.getValue()));
            }
            Collections.sort(list);
            return list;
        }

        // 3. Katz Backoff: Fallback to Order 1 P(S_{t+1} | S_t = curr)
        return order1Fallback.getSortedTransitions(curr);
    }

    @NonNull
    public List<String> getCandidatesAboveThreshold(@NonNull String prev, @NonNull String curr, double threshold) {
        List<ScreenProbability> list = getTransitions(prev, curr);
        List<String> results = new ArrayList<>();
        for (ScreenProbability sp : list) {
            if (sp.getProbability() >= threshold) {
                results.add(sp.getScreenName());
            }
        }
        return results;
    }

    @NonNull
    public MarkovTransitionMatrix getOrder1Fallback() {
        return order1Fallback;
    }
}
