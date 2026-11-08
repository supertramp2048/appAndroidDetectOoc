package com.socialapp.core.prefetch.policy;

import androidx.annotation.NonNull;

import com.socialapp.core.network.sim.ScreenPayloadConfig;
import com.socialapp.core.prefetch.IPrefetchPolicy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Policy P1: Greedy Prefetch All.
 * Eagerly prefetches all possible outgoing screens from current location.
 * Used to measure theoretical maximum latency reduction vs maximum bandwidth waste.
 */
public class PrefetchAllPolicy implements IPrefetchPolicy {

    public static final String POLICY_ID = "P1_PREFETCH_ALL";

    @NonNull
    @Override
    public String getPolicyId() {
        return POLICY_ID;
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "P1: Nạp tất cả (Greedy Prefetch All)";
    }

    @NonNull
    @Override
    public List<String> predictNextScreens(@NonNull String currentScreen, @NonNull List<String> history) {
        if (ScreenPayloadConfig.SCREEN_HOME.equals(currentScreen)) {
            return Arrays.asList(
                    ScreenPayloadConfig.SCREEN_POST_DETAIL,
                    ScreenPayloadConfig.SCREEN_SEARCH,
                    ScreenPayloadConfig.SCREEN_PROFILE
            );
        } else if (ScreenPayloadConfig.SCREEN_POST_DETAIL.equals(currentScreen)) {
            return Arrays.asList(
                    ScreenPayloadConfig.SCREEN_COMMENTS,
                    ScreenPayloadConfig.SCREEN_PROFILE,
                    ScreenPayloadConfig.SCREEN_FACTCHECK
            );
        } else if (ScreenPayloadConfig.SCREEN_COMMENTS.equals(currentScreen)) {
            return Arrays.asList(
                    ScreenPayloadConfig.SCREEN_PROFILE,
                    ScreenPayloadConfig.SCREEN_POST_DETAIL
            );
        } else if (ScreenPayloadConfig.SCREEN_FACTCHECK.equals(currentScreen)) {
            return Collections.singletonList(ScreenPayloadConfig.SCREEN_POST_DETAIL);
        }
        return Collections.emptyList();
    }
}
