package com.socialapp.core.prefetch.policy;

import androidx.annotation.NonNull;

import com.socialapp.core.prefetch.IPrefetchPolicy;

import java.util.Collections;
import java.util.List;

/**
 * P0: No Prefetching (Pure Baseline).
 * Completely disables proactive background downloads.
 * Used to measure unaltered application performance and baseline cold load latency.
 */
public class P0_NoPrefetchPolicy implements IPrefetchPolicy {

    public static final String POLICY_ID = PolicyType.P0_NO_PREFETCH.getCode();

    @NonNull
    @Override
    public String getPolicyId() {
        return POLICY_ID;
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return PolicyType.P0_NO_PREFETCH.getDisplayName();
    }

    @NonNull
    @Override
    public List<String> predictNextScreens(@NonNull String currentScreen, @NonNull List<String> history) {
        return Collections.emptyList();
    }
}
