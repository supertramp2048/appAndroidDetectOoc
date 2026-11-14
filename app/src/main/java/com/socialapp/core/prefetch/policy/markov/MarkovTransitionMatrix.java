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
 * First-Order Markov Transition Matrix M^(1).
 * Models P(S_{t+1} = j | S_t = i).
 * Supports both online transition counting and batch matrix loading (from Kiên's dataset).
 */
public class MarkovTransitionMatrix {

    // Count matrix: FromScreen -> (ToScreen -> Count)
    private final Map<String, Map<String, AtomicInteger>> countMatrix = new ConcurrentHashMap<>();

    // Explicit override probabilities: FromScreen -> (ToScreen -> Probability)
    private final Map<String, Map<String, Double>> explicitProbabilities = new ConcurrentHashMap<>();

    public MarkovTransitionMatrix() {
        initDefaultEmpiricalBaseline();
    }

    /**
     * Seeds realistic empirical transition probabilities from social navigation patterns.
     */
    private void initDefaultEmpiricalBaseline() {
        // HOME: 82% PostDetail, 12% Search, 6% Profile
        setExplicitProbability(ScreenPayloadConfig.SCREEN_HOME, ScreenPayloadConfig.SCREEN_POST_DETAIL, 0.82);
        setExplicitProbability(ScreenPayloadConfig.SCREEN_HOME, ScreenPayloadConfig.SCREEN_SEARCH, 0.12);
        setExplicitProbability(ScreenPayloadConfig.SCREEN_HOME, ScreenPayloadConfig.SCREEN_PROFILE, 0.06);

        // POST_DETAIL: 72% Comments, 18% Profile, 7% FactCheck, 3% Home
        setExplicitProbability(ScreenPayloadConfig.SCREEN_POST_DETAIL, ScreenPayloadConfig.SCREEN_COMMENTS, 0.72);
        setExplicitProbability(ScreenPayloadConfig.SCREEN_POST_DETAIL, ScreenPayloadConfig.SCREEN_PROFILE, 0.18);
        setExplicitProbability(ScreenPayloadConfig.SCREEN_POST_DETAIL, ScreenPayloadConfig.SCREEN_FACTCHECK, 0.07);
        setExplicitProbability(ScreenPayloadConfig.SCREEN_POST_DETAIL, ScreenPayloadConfig.SCREEN_HOME, 0.03);

        // COMMENTS: 62% Profile, 35% PostDetail, 3% Home
        setExplicitProbability(ScreenPayloadConfig.SCREEN_COMMENTS, ScreenPayloadConfig.SCREEN_PROFILE, 0.62);
        setExplicitProbability(ScreenPayloadConfig.SCREEN_COMMENTS, ScreenPayloadConfig.SCREEN_POST_DETAIL, 0.35);
        setExplicitProbability(ScreenPayloadConfig.SCREEN_COMMENTS, ScreenPayloadConfig.SCREEN_HOME, 0.03);

        // PROFILE: 78% PostDetail, 14% Home, 8% UserList
        setExplicitProbability(ScreenPayloadConfig.SCREEN_PROFILE, ScreenPayloadConfig.SCREEN_POST_DETAIL, 0.78);
        setExplicitProbability(ScreenPayloadConfig.SCREEN_PROFILE, ScreenPayloadConfig.SCREEN_HOME, 0.14);
        setExplicitProbability(ScreenPayloadConfig.SCREEN_PROFILE, ScreenPayloadConfig.SCREEN_USER_LIST, 0.08);

        // FACTCHECK: 88% PostDetail, 12% Home
        setExplicitProbability(ScreenPayloadConfig.SCREEN_FACTCHECK, ScreenPayloadConfig.SCREEN_POST_DETAIL, 0.88);
        setExplicitProbability(ScreenPayloadConfig.SCREEN_FACTCHECK, ScreenPayloadConfig.SCREEN_HOME, 0.12);

        // SEARCH: 80% PostDetail, 15% Profile, 5% Home
        setExplicitProbability(ScreenPayloadConfig.SCREEN_SEARCH, ScreenPayloadConfig.SCREEN_POST_DETAIL, 0.80);
        setExplicitProbability(ScreenPayloadConfig.SCREEN_SEARCH, ScreenPayloadConfig.SCREEN_PROFILE, 0.15);
        setExplicitProbability(ScreenPayloadConfig.SCREEN_SEARCH, ScreenPayloadConfig.SCREEN_HOME, 0.05);
    }

    // Online Markov Predictor (Kiên - Tasks B1-B5)
    private final MarkovPredictor<String> predictor = new MarkovPredictor<>(1, 1);

    public MarkovPredictor<String> getPredictor() {
        return predictor;
    }

    /**
     * Records a live user transition S_from -> S_to (Online Learning).
     */
    public void recordTransition(@NonNull String fromScreen, @NonNull String toScreen) {
        countMatrix.computeIfAbsent(fromScreen, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(toScreen, k -> new AtomicInteger(0))
                .incrementAndGet();
        predictor.learn(Collections.singletonList(fromScreen), toScreen);
    }

    public void setExplicitProbability(@NonNull String fromScreen, @NonNull String toScreen, double probability) {
        explicitProbabilities.computeIfAbsent(fromScreen, k -> new ConcurrentHashMap<>())
                .put(toScreen, Math.max(0.0, Math.min(1.0, probability)));
    }

    /**
     * Loads a full transition matrix (e.g. trained from Kiên's offline Python script).
     */
    public void loadMatrix(@NonNull Map<String, Map<String, Double>> matrix) {
        explicitProbabilities.clear();
        for (Map.Entry<String, Map<String, Double>> row : matrix.entrySet()) {
            explicitProbabilities.put(row.getKey(), new ConcurrentHashMap<>(row.getValue()));
        }
    }

    /**
     * Returns the transition probability P(toScreen | fromScreen).
     */
    public double getProbability(@NonNull String fromScreen, @NonNull String toScreen) {
        // Priority 1: Check online count matrix if learning data exists
        Map<String, AtomicInteger> rowCounts = countMatrix.get(fromScreen);
        if (rowCounts != null && !rowCounts.isEmpty()) {
            long total = 0;
            for (AtomicInteger c : rowCounts.values()) {
                total += c.get();
            }
            if (total > 0) {
                AtomicInteger targetCount = rowCounts.get(toScreen);
                return targetCount != null ? (double) targetCount.get() / total : 0.0;
            }
        }

        // Priority 2: Use explicit matrix probabilities
        Map<String, Double> explicitRow = explicitProbabilities.get(fromScreen);
        if (explicitRow != null) {
            Double p = explicitRow.get(toScreen);
            return p != null ? p : 0.0;
        }

        return 0.0;
    }

    /**
     * Retrieves all candidate transitions from a screen, sorted descending by probability.
     */
    @NonNull
    public List<ScreenProbability> getSortedTransitions(@NonNull String fromScreen) {
        Map<String, AtomicInteger> rowCounts = countMatrix.get(fromScreen);
        if (rowCounts != null && !rowCounts.isEmpty()) {
            long total = 0;
            for (AtomicInteger c : rowCounts.values()) {
                total += c.get();
            }
            if (total > 0) {
                List<ScreenProbability> list = new ArrayList<>();
                for (Map.Entry<String, AtomicInteger> e : rowCounts.entrySet()) {
                    double p = (double) e.getValue().get() / total;
                    list.add(new ScreenProbability(e.getKey(), p));
                }
                Collections.sort(list);
                return list;
            }
        }

        Map<String, Double> explicitRow = explicitProbabilities.get(fromScreen);
        if (explicitRow != null && !explicitRow.isEmpty()) {
            List<ScreenProbability> list = new ArrayList<>();
            for (Map.Entry<String, Double> e : explicitRow.entrySet()) {
                list.add(new ScreenProbability(e.getKey(), e.getValue()));
            }
            Collections.sort(list);
            return list;
        }

        return Collections.emptyList();
    }

    /**
     * Filters candidate transitions with P(S_next | fromScreen) >= threshold.
     */
    @NonNull
    public List<String> getCandidatesAboveThreshold(@NonNull String fromScreen, double threshold) {
        List<ScreenProbability> transitions = getSortedTransitions(fromScreen);
        List<String> results = new ArrayList<>();
        for (ScreenProbability sp : transitions) {
            if (sp.getProbability() >= threshold) {
                results.add(sp.getScreenName());
            }
        }
        return results;
    }

    public void clearLearnedData() {
        countMatrix.clear();
    }
}
