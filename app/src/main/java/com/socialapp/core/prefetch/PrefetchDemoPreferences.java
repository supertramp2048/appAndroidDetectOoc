package com.socialapp.core.prefetch;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Lưu trữ cấu hình của thanh thử nghiệm Prefetch Demo xuống SharedPreferences
 * để giữ nguyên các chế độ người dùng đã chọn qua các lần mở lại app.
 */
public class PrefetchDemoPreferences {

    public static final String PREFS_NAME = "prefetch_demo_prefs";

    public static final String KEY_POLICY = "policy";
    public static final String KEY_NETWORK = "network";
    public static final String KEY_TIME_SCALE = "time_scale";
    public static final String KEY_SIMULATION_MODE = "simulation_mode";
    public static final String KEY_OVERLAY_EXPANDED = "overlay_expanded";

    public static final PrefetchEngine.Policy DEFAULT_POLICY = PrefetchEngine.Policy.P3_MARKOV_FIXED;
    public static final PrefetchEngine.NetworkProfile DEFAULT_NETWORK = PrefetchEngine.NetworkProfile.LTE;
    public static final float DEFAULT_TIME_SCALE = 1.0f;
    public static final float MIN_TIME_SCALE = 0.1f;
    public static final boolean DEFAULT_SIMULATION_MODE = true;
    public static final boolean DEFAULT_OVERLAY_EXPANDED = true;

    /**
     * Abstraction tối giản cho phép unit test thuần JVM không phụ thuộc Android SharedPreferences.
     */
    public interface KeyValueStore {
        @Nullable
        String getString(@NonNull String key, @Nullable String defValue);

        void putString(@NonNull String key, @NonNull String value);

        float getFloat(@NonNull String key, float defValue);

        void putFloat(@NonNull String key, float value);

        boolean getBoolean(@NonNull String key, boolean defValue);

        void putBoolean(@NonNull String key, boolean value);
    }

    private static final class SharedPreferencesStore implements KeyValueStore {
        private final SharedPreferences sharedPreferences;

        SharedPreferencesStore(@NonNull SharedPreferences sharedPreferences) {
            this.sharedPreferences = sharedPreferences;
        }

        @Nullable
        @Override
        public String getString(@NonNull String key, @Nullable String defValue) {
            return sharedPreferences.getString(key, defValue);
        }

        @Override
        public void putString(@NonNull String key, @NonNull String value) {
            sharedPreferences.edit().putString(key, value).apply();
        }

        @Override
        public float getFloat(@NonNull String key, float defValue) {
            return sharedPreferences.getFloat(key, defValue);
        }

        @Override
        public void putFloat(@NonNull String key, float value) {
            sharedPreferences.edit().putFloat(key, value).apply();
        }

        @Override
        public boolean getBoolean(@NonNull String key, boolean defValue) {
            return sharedPreferences.getBoolean(key, defValue);
        }

        @Override
        public void putBoolean(@NonNull String key, boolean value) {
            sharedPreferences.edit().putBoolean(key, value).apply();
        }
    }

    private final KeyValueStore store;

    public PrefetchDemoPreferences(@NonNull Context context) {
        this(new SharedPreferencesStore(
                context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        ));
    }

    public PrefetchDemoPreferences(@NonNull KeyValueStore store) {
        this.store = store;
    }

    @NonNull
    public PrefetchEngine.Policy getPolicy() {
        String savedCode = store.getString(KEY_POLICY, DEFAULT_POLICY.code);
        if (savedCode != null) {
            for (PrefetchEngine.Policy policy : PrefetchEngine.Policy.values()) {
                if (policy.code.equals(savedCode)) {
                    return policy;
                }
            }
        }
        return DEFAULT_POLICY;
    }

    public void setPolicy(@NonNull PrefetchEngine.Policy policy) {
        store.putString(KEY_POLICY, policy.code);
    }

    @NonNull
    public PrefetchEngine.NetworkProfile getNetwork() {
        String savedCode = store.getString(KEY_NETWORK, DEFAULT_NETWORK.code);
        if (savedCode != null) {
            for (PrefetchEngine.NetworkProfile network : PrefetchEngine.NetworkProfile.values()) {
                if (network.code.equals(savedCode)) {
                    return network;
                }
            }
        }
        return DEFAULT_NETWORK;
    }

    public void setNetwork(@NonNull PrefetchEngine.NetworkProfile network) {
        store.putString(KEY_NETWORK, network.code);
    }

    public float getTimeScale() {
        float saved = store.getFloat(KEY_TIME_SCALE, DEFAULT_TIME_SCALE);
        return sanitizeTimeScale(saved);
    }

    public void setTimeScale(float timeScale) {
        float sanitized = Float.isNaN(timeScale) || Float.isInfinite(timeScale)
                ? DEFAULT_TIME_SCALE
                : Math.max(MIN_TIME_SCALE, timeScale);
        store.putFloat(KEY_TIME_SCALE, sanitized);
    }

    public boolean isSimulationMode() {
        return store.getBoolean(KEY_SIMULATION_MODE, DEFAULT_SIMULATION_MODE);
    }

    public void setSimulationMode(boolean simulationMode) {
        store.putBoolean(KEY_SIMULATION_MODE, simulationMode);
    }

    public boolean isOverlayExpanded() {
        return store.getBoolean(KEY_OVERLAY_EXPANDED, DEFAULT_OVERLAY_EXPANDED);
    }

    public void setOverlayExpanded(boolean expanded) {
        store.putBoolean(KEY_OVERLAY_EXPANDED, expanded);
    }

    private static float sanitizeTimeScale(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value) || value < MIN_TIME_SCALE) {
            return DEFAULT_TIME_SCALE;
        }
        return value;
    }
}
