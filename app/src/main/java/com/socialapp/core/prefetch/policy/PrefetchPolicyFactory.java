package com.socialapp.core.prefetch.policy;

import androidx.annotation.NonNull;

import com.socialapp.core.prefetch.IPrefetchPolicy;
import com.socialapp.core.prefetch.PrefetchConfig;
import com.socialapp.core.prefetch.policy.markov.MarkovTransitionMatrix;
import com.socialapp.core.prefetch.policy.markov.MarkovTransitionTensor;

/**
 * Factory and registry for instantiating and caching policy implementations.
 */
public class PrefetchPolicyFactory {

    private final MarkovTransitionMatrix sharedMatrix;
    private final MarkovTransitionTensor sharedTensor;

    public PrefetchPolicyFactory() {
        this.sharedMatrix = new MarkovTransitionMatrix();
        this.sharedTensor = new MarkovTransitionTensor(this.sharedMatrix);
    }

    public PrefetchPolicyFactory(
            @NonNull MarkovTransitionMatrix matrix,
            @NonNull MarkovTransitionTensor tensor) {
        this.sharedMatrix = matrix;
        this.sharedTensor = tensor;
    }

    @NonNull
    public MarkovTransitionMatrix getSharedMatrix() {
        return sharedMatrix;
    }

    @NonNull
    public MarkovTransitionTensor getSharedTensor() {
        return sharedTensor;
    }

    @NonNull
    public IPrefetchPolicy createPolicy(@NonNull PolicyType type) {
        return createPolicy(type, PrefetchConfig.DEFAULT_MARKOV_THRESHOLD, 1.0);
    }

    @NonNull
    public IPrefetchPolicy createPolicy(
            @NonNull PolicyType type,
            double markovThreshold,
            double riskAversionLambda) {

        switch (type) {
            case P0_NO_PREFETCH:
                return new P0_NoPrefetchPolicy();
            case P1_PREFETCH_ALL:
                return new P1_PrefetchAllPolicy(sharedMatrix);
            case P2_MOST_FREQUENT:
                return new P2_MostFrequentPolicy(sharedMatrix);
            case P3_MARKOV_ORDER_1:
                return new P3_MarkovOrder1Policy(sharedMatrix, markovThreshold);
            case P4_MARKOV_ORDER_2:
                return new P4_MarkovOrder2Policy(sharedTensor, markovThreshold);
            case P5_NETWORK_ADAPTIVE:
                return new P5_NetworkCostAdaptivePolicy(sharedTensor,
                        com.socialapp.core.network.sim.MockNetworkEngine.getInstance(), riskAversionLambda);
            default:
                return new P2_MostFrequentPolicy(sharedMatrix);
        }
    }

    @NonNull
    public IPrefetchPolicy createPolicyByCode(@NonNull String code) {
        for (PolicyType type : PolicyType.values()) {
            if (type.getCode().equalsIgnoreCase(code) || type.name().equalsIgnoreCase(code)) {
                return createPolicy(type);
            }
        }
        return new P2_MostFrequentPolicy(sharedMatrix);
    }
}
