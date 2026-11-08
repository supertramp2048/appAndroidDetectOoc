package com.socialapp.core.prefetch.policy;

import androidx.annotation.NonNull;

import com.socialapp.core.network.sim.ScreenPayloadConfig;
import com.socialapp.core.prefetch.IPrefetchPolicy;

import java.util.Collections;
import java.util.List;

/**
 * Policy P2: Most Frequent Transition Heuristic (Default).
 * Selects the single most probable outgoing screen based on standard social networking flow:
 * HOME -> POST_DETAIL (80% users read feed posts)
 * POST_DETAIL -> COMMENTS (70% users check comments)
 * COMMENTS -> PROFILE (50% users view author)
 */
public class MostFrequentPolicy implements IPrefetchPolicy {

    public static final String POLICY_ID = "P2_MOST_FREQUENT";

    @NonNull
    @Override
    public String getPolicyId() {
        return POLICY_ID;
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "P2: Chuyển tiếp phổ biến nhất (Default Heuristic)";
    }

    @NonNull
    @Override
    public List<String> predictNextScreens(@NonNull String currentScreen, @NonNull List<String> history) {
        if (ScreenPayloadConfig.SCREEN_HOME.equals(currentScreen)) {
            return Collections.singletonList(ScreenPayloadConfig.SCREEN_POST_DETAIL);
        } else if (ScreenPayloadConfig.SCREEN_POST_DETAIL.equals(currentScreen)) {
            return Collections.singletonList(ScreenPayloadConfig.SCREEN_COMMENTS);
        } else if (ScreenPayloadConfig.SCREEN_COMMENTS.equals(currentScreen)) {
            return Collections.singletonList(ScreenPayloadConfig.SCREEN_PROFILE);
        } else if (ScreenPayloadConfig.SCREEN_FACTCHECK.equals(currentScreen)) {
            return Collections.singletonList(ScreenPayloadConfig.SCREEN_POST_DETAIL);
        }
        return Collections.emptyList();
    }
}
