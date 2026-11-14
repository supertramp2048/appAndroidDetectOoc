package com.socialapp.core.prefetch.policy.markov;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * Value object representing a candidate screen transition and its conditional probability.
 */
public final class ScreenProbability implements Comparable<ScreenProbability> {

    @NonNull
    private final String screenName;
    private final double probability;

    public ScreenProbability(@NonNull String screenName, double probability) {
        this.screenName = screenName;
        this.probability = Math.max(0.0, Math.min(1.0, probability));
    }

    @NonNull
    public String getScreenName() {
        return screenName;
    }

    public double getProbability() {
        return probability;
    }

    @Override
    public int compareTo(@NonNull ScreenProbability other) {
        // Sort descending: highest probability first
        return Double.compare(other.probability, this.probability);
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.US, "%s (%.1f%%)", screenName, probability * 100.0);
    }
}
