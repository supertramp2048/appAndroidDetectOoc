package com.socialapp.core.prefetch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class PrefetchDemoPreferencesTest {

    private static final class InMemoryKeyValueStore implements PrefetchDemoPreferences.KeyValueStore {
        private final Map<String, Object> entries = new HashMap<>();

        @Nullable
        @Override
        public String getString(@NonNull String key, @Nullable String defValue) {
            Object value = entries.get(key);
            return value instanceof String ? (String) value : defValue;
        }

        @Override
        public void putString(@NonNull String key, @NonNull String value) {
            entries.put(key, value);
        }

        @Override
        public float getFloat(@NonNull String key, float defValue) {
            Object value = entries.get(key);
            return value instanceof Float ? (Float) value : defValue;
        }

        @Override
        public void putFloat(@NonNull String key, float value) {
            entries.put(key, value);
        }

        @Override
        public boolean getBoolean(@NonNull String key, boolean defValue) {
            Object value = entries.get(key);
            return value instanceof Boolean ? (Boolean) value : defValue;
        }

        @Override
        public void putBoolean(@NonNull String key, boolean value) {
            entries.put(key, value);
        }
    }

    @Test
    public void defaults_onEmptyStore() {
        InMemoryKeyValueStore store = new InMemoryKeyValueStore();
        PrefetchDemoPreferences prefs = new PrefetchDemoPreferences(store);
        PrefetchEngine engine = new PrefetchEngine(prefs);

        assertEquals(PrefetchEngine.Policy.P3_MARKOV_FIXED, prefs.getPolicy());
        assertEquals(PrefetchEngine.NetworkProfile.LTE, prefs.getNetwork());
        assertEquals(1.0f, prefs.getTimeScale(), 0.0001f);
        assertTrue(prefs.isSimulationMode());
        assertTrue(prefs.isOverlayExpanded());

        assertEquals(PrefetchEngine.Policy.P3_MARKOV_FIXED, engine.getCurrentPolicy());
        assertEquals(PrefetchEngine.NetworkProfile.LTE, engine.getCurrentNetwork());
        assertEquals(1.0f, engine.getTimeScale(), 0.0001f);
        assertTrue(engine.isSimulationMode());
    }

    @Test
    public void roundTripSaveAndRestoreAcrossEngineInstances() {
        InMemoryKeyValueStore store = new InMemoryKeyValueStore();
        PrefetchDemoPreferences firstPrefs = new PrefetchDemoPreferences(store);
        PrefetchEngine firstEngine = new PrefetchEngine(firstPrefs);

        firstEngine.setCurrentPolicy(PrefetchEngine.Policy.P5_ADAPTIVE_NETWORK);
        firstEngine.setCurrentNetwork(PrefetchEngine.NetworkProfile.EDGE);
        firstEngine.setTimeScale(5.0f);
        firstEngine.setSimulationMode(false);
        firstPrefs.setOverlayExpanded(false);

        // Simulated restart with fresh instances reading the same backing store
        PrefetchDemoPreferences restoredPrefs = new PrefetchDemoPreferences(store);
        PrefetchEngine restoredEngine = new PrefetchEngine(restoredPrefs);

        assertEquals(PrefetchEngine.Policy.P5_ADAPTIVE_NETWORK, restoredEngine.getCurrentPolicy());
        assertEquals(PrefetchEngine.NetworkProfile.EDGE, restoredEngine.getCurrentNetwork());
        assertEquals(5.0f, restoredEngine.getTimeScale(), 0.0001f);
        assertFalse(restoredEngine.isSimulationMode());
        assertFalse(restoredPrefs.isOverlayExpanded());

        // Resetting Markov session stats must preserve user settings
        restoredEngine.resetSession();
        assertEquals(PrefetchEngine.Policy.P5_ADAPTIVE_NETWORK, restoredPrefs.getPolicy());
        assertEquals(PrefetchEngine.NetworkProfile.EDGE, restoredPrefs.getNetwork());
        assertEquals(5.0f, restoredPrefs.getTimeScale(), 0.0001f);
        assertFalse(restoredPrefs.isSimulationMode());
    }

    @Test
    public void corruptedValues_fallBackToDefaults() {
        InMemoryKeyValueStore store = new InMemoryKeyValueStore();
        store.putString(PrefetchDemoPreferences.KEY_POLICY, "INVALID_POLICY");
        store.putString(PrefetchDemoPreferences.KEY_NETWORK, "INVALID_NET");
        store.putFloat(PrefetchDemoPreferences.KEY_TIME_SCALE, 0.0f);

        PrefetchDemoPreferences prefs = new PrefetchDemoPreferences(store);
        assertEquals(PrefetchEngine.Policy.P3_MARKOV_FIXED, prefs.getPolicy());
        assertEquals(PrefetchEngine.NetworkProfile.LTE, prefs.getNetwork());
        assertEquals(1.0f, prefs.getTimeScale(), 0.0001f);

        store.putFloat(PrefetchDemoPreferences.KEY_TIME_SCALE, -5.0f);
        assertEquals(1.0f, prefs.getTimeScale(), 0.0001f);

        store.putFloat(PrefetchDemoPreferences.KEY_TIME_SCALE, Float.NaN);
        assertEquals(1.0f, prefs.getTimeScale(), 0.0001f);

        store.putFloat(PrefetchDemoPreferences.KEY_TIME_SCALE, Float.POSITIVE_INFINITY);
        assertEquals(1.0f, prefs.getTimeScale(), 0.0001f);
    }

    @Test
    public void nonPersistingBatchOverride_doesNotOverwriteSavedPrefs() {
        InMemoryKeyValueStore store = new InMemoryKeyValueStore();
        PrefetchDemoPreferences prefs = new PrefetchDemoPreferences(store);
        PrefetchEngine engine = new PrefetchEngine(prefs);

        engine.setCurrentPolicy(PrefetchEngine.Policy.P4_MARKOV_ORDER2);
        engine.setCurrentNetwork(PrefetchEngine.NetworkProfile.G3);

        engine.setCurrentPolicy(PrefetchEngine.Policy.P0_NONE, false);
        engine.setCurrentNetwork(PrefetchEngine.NetworkProfile.WIFI, false);

        assertEquals(PrefetchEngine.Policy.P0_NONE, engine.getCurrentPolicy());
        assertEquals(PrefetchEngine.NetworkProfile.WIFI, engine.getCurrentNetwork());

        // Stored preferences stay untouched
        assertEquals(PrefetchEngine.Policy.P4_MARKOV_ORDER2, prefs.getPolicy());
        assertEquals(PrefetchEngine.NetworkProfile.G3, prefs.getNetwork());

        PrefetchEngine nextLaunch = new PrefetchEngine(new PrefetchDemoPreferences(store));
        assertEquals(PrefetchEngine.Policy.P4_MARKOV_ORDER2, nextLaunch.getCurrentPolicy());
        assertEquals(PrefetchEngine.NetworkProfile.G3, nextLaunch.getCurrentNetwork());
    }

    @Test
    public void zeroArgConstructor_remainsBackwardCompatible() {
        PrefetchEngine engine = new PrefetchEngine();

        assertEquals(PrefetchEngine.Policy.P3_MARKOV_FIXED, engine.getCurrentPolicy());
        assertEquals(PrefetchEngine.NetworkProfile.LTE, engine.getCurrentNetwork());
        assertEquals(1.0f, engine.getTimeScale(), 0.0001f);
        assertTrue(engine.isSimulationMode());

        engine.setCurrentPolicy(PrefetchEngine.Policy.P1_ALL);
        engine.setCurrentNetwork(PrefetchEngine.NetworkProfile.WIFI);
        engine.setTimeScale(2.0f);
        engine.setSimulationMode(false);

        assertEquals(PrefetchEngine.Policy.P1_ALL, engine.getCurrentPolicy());
        assertEquals(PrefetchEngine.NetworkProfile.WIFI, engine.getCurrentNetwork());
        assertEquals(2.0f, engine.getTimeScale(), 0.0001f);
        assertFalse(engine.isSimulationMode());
    }
}
