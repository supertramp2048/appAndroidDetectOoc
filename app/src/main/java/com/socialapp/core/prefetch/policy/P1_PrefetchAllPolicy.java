package com.socialapp.core.prefetch.policy;

import androidx.annotation.NonNull;

import com.socialapp.core.prefetch.IPrefetchPolicy;
import com.socialapp.core.prefetch.policy.markov.MarkovTransitionMatrix;
import com.socialapp.core.prefetch.policy.markov.ScreenProbability;

import java.util.ArrayList;
import java.util.List;

/**
 * P1: Greedy Prefetch All Outgoing Candidates.
 * Eagerly prefetches all adjacent screens with non-zero transition probability (P > 0).
 * Used to measure the theoretical upper bound of Hit Rate and maximum potential Wasted Bytes.
 */
public class P1_PrefetchAllPolicy implements IPrefetchPolicy {

    public static final String POLICY_ID = PolicyType.P1_PREFETCH_ALL.getCode();

    private final MarkovTransitionMatrix matrix;

    public P1_PrefetchAllPolicy() {
        this(new MarkovTransitionMatrix());
    }

    public P1_PrefetchAllPolicy(@NonNull MarkovTransitionMatrix matrix) {
        this.matrix = matrix;
    }

    @NonNull
    @Override
    public String getPolicyId() {
        return POLICY_ID;
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return PolicyType.P1_PREFETCH_ALL.getDisplayName();
    }

    @NonNull
    @Override
    public List<String> predictNextScreens(@NonNull String currentScreen, @NonNull List<String> history) {
        List<ScreenProbability> transitions = matrix.getSortedTransitions(currentScreen);
        List<String> results = new ArrayList<>();
        for (ScreenProbability sp : transitions) {
            if (sp.getProbability() > 0.0) {
                results.add(sp.getScreenName());
            }
        }
        return results;
    }
}
