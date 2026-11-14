package com.socialapp.core.prefetch.policy;

import androidx.annotation.NonNull;

import com.socialapp.core.prefetch.IPrefetchPolicy;

import java.util.Collections;
import java.util.List;

/**
 * Policy P0: No Prefetching (Baseline).
 * Never triggers background downloads. Used to measure pure application performance.
 */
public class NoPrefetchPolicy implements IPrefetchPolicy {

    public static final String POLICY_ID = "P0_NO_PREFETCH";

    @NonNull
    @Override
    public String getPolicyId() {
        return POLICY_ID;
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "P0: Không nạp trước (Baseline)";
    }

    @NonNull
    @Override
    public List<String> predictNextScreens(@NonNull String currentScreen, @NonNull List<String> history) {
        return Collections.emptyList();
    }
}
