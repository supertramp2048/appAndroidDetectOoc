package com.socialapp.core.network.sim;

import androidx.annotation.NonNull;

/**
 * Payload Weight multipliers as specified in Task C1:
 * - LIGHT_0_5: factor x0.5 (text-only, skeleton, metadata)
 * - NORMAL_1_0: factor x1.0 (standard screen content)
 * - HEAVY_2_0: factor x2.0 (rich media, multiple images, full list)
 */
public enum PayloadWeight {
    LIGHT_0_5(0.5, "Light (x0.5)"),
    NORMAL_1_0(1.0, "Normal (x1.0)"),
    HEAVY_2_0(2.0, "Heavy (x2.0)");

    private final double multiplier;
    private final String displayName;

    PayloadWeight(double multiplier, @NonNull String displayName) {
        this.multiplier = multiplier;
        this.displayName = displayName;
    }

    public double getMultiplier() {
        return multiplier;
    }

    @NonNull
    public String getDisplayName() {
        return displayName;
    }
}
