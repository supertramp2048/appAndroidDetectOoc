package com.socialapp.core.prefetch.policy;

import androidx.annotation.NonNull;

import com.socialapp.core.prefetch.IPrefetchPolicy;
import com.socialapp.core.prefetch.policy.markov.MarkovTransitionMatrix;
import com.socialapp.core.prefetch.policy.markov.ScreenProbability;

import java.util.Collections;
import java.util.List;

/**
 * P2: Most Frequent Transition Heuristic (Top-1).
 * Strictly prefetches the single most probable outgoing screen from the current location.
 */
public class P2_MostFrequentPolicy implements IPrefetchPolicy {

    public static final String POLICY_ID = PolicyType.P2_MOST_FREQUENT.getCode();

    private final MarkovTransitionMatrix matrix;

    public P2_MostFrequentPolicy() {
        this(new MarkovTransitionMatrix());
    }

    public P2_MostFrequentPolicy(@NonNull MarkovTransitionMatrix matrix) {
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
        return PolicyType.P2_MOST_FREQUENT.getDisplayName();
    }

    @NonNull
    @Override
    public List<String> predictNextScreens(@NonNull String currentScreen, @NonNull List<String> history) {
        List<ScreenProbability> transitions = matrix.getSortedTransitions(currentScreen);
        if (!transitions.isEmpty()) {
            return Collections.singletonList(transitions.get(0).getScreenName());
        }
        return Collections.emptyList();
    }
}
