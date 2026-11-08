package com.socialapp.core.prefetch;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * Strategy interface defining how the prefetch engine selects candidate target screens
 * based on current navigation context and user transition history.
 *
 * Kiên's Markov model ($P_3, P_4$) implements this interface to plug directly into the engine.
 */
public interface IPrefetchPolicy {

    /**
     * Unique identifier for the policy (e.g. "P0_NONE", "P2_MOST_FREQUENT", "P3_MARKOV_O1").
     */
    @NonNull
    String getPolicyId();

    /**
     * Human-readable label for UI telemetry / selector.
     */
    @NonNull
    String getDisplayName();

    /**
     * Predicts candidate screens to prefetch given current screen and navigation history.
     *
     * @param currentScreen The screen currently being rendered / visited (e.g., "home", "post_detail")
     * @param history Recent navigation history in chronological order [S_t-2, S_t-1, S_t]
     * @return Ordered list of candidate screen identifiers to prefetch (highest probability first).
     */
    @NonNull
    List<String> predictNextScreens(@NonNull String currentScreen, @NonNull List<String> history);
}
