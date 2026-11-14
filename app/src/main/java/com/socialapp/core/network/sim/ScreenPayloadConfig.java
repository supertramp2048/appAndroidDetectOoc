package com.socialapp.core.network.sim;

import androidx.annotation.NonNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of base payload sizes (in Kilobytes) for each screen in the application.
 * Used by MockNetworkEngine to compute deterministic transfer durations.
 */
public final class ScreenPayloadConfig {

    public static final String SCREEN_HOME = "home";
    public static final String SCREEN_POST_DETAIL = "post_detail";
    public static final String SCREEN_COMMENTS = "comments";
    public static final String SCREEN_PROFILE = "profile";
    public static final String SCREEN_FACTCHECK = "factcheck";
    public static final String SCREEN_SEARCH = "search";
    public static final String SCREEN_SETTINGS = "settings";
    public static final String SCREEN_COMPOSE = "compose";
    public static final String SCREEN_USER_LIST = "user_list";

    public static final double DEFAULT_BASE_SIZE_KB = 50.0;

    private static final Map<String, Double> PAYLOAD_REGISTRY = new ConcurrentHashMap<>();

    static {
        // Base sizes in Kilobytes (KB)
        PAYLOAD_REGISTRY.put(SCREEN_HOME, 120.0);        // Feed: articles, category tabs, user previews
        PAYLOAD_REGISTRY.put(SCREEN_POST_DETAIL, 250.0); // Full post content, media headers, author card
        PAYLOAD_REGISTRY.put(SCREEN_COMMENTS, 80.0);     // Comment tree, replies, avatars
        PAYLOAD_REGISTRY.put(SCREEN_PROFILE, 60.0);      // User profile bio, follow counts, post tabs
        PAYLOAD_REGISTRY.put(SCREEN_FACTCHECK, 180.0);   // Verification analysis, claim cards, OCR/OOC logs
        PAYLOAD_REGISTRY.put(SCREEN_SEARCH, 90.0);       // Search index queries, trending chips
        PAYLOAD_REGISTRY.put(SCREEN_SETTINGS, 15.0);     // Preferences, toggle configs
        PAYLOAD_REGISTRY.put(SCREEN_COMPOSE, 10.0);      // Category selection, drafting meta
        PAYLOAD_REGISTRY.put(SCREEN_USER_LIST, 45.0);    // Followers / Following list
    }

    private ScreenPayloadConfig() {}

    /**
     * Gets the base payload size in KB for a screen identifier.
     * Returns DEFAULT_BASE_SIZE_KB if not registered.
     */
    public static double getBaseSizeKb(@NonNull String screenName) {
        if (screenName == null) {
            return DEFAULT_BASE_SIZE_KB;
        }
        Double size = PAYLOAD_REGISTRY.get(screenName.toLowerCase().trim());
        return size != null ? size : DEFAULT_BASE_SIZE_KB;
    }

    /**
     * Customizes or registers a new base payload size for a screen.
     */
    public static void registerSize(@NonNull String screenName, double sizeKb) {
        if (screenName != null) {
            PAYLOAD_REGISTRY.put(screenName.toLowerCase().trim(), Math.max(1.0, sizeKb));
        }
    }
}
