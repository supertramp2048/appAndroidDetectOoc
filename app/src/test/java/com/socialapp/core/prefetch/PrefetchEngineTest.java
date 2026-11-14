package com.socialapp.core.prefetch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.socialapp.core.network.sim.MockNetworkEngine;
import com.socialapp.core.network.sim.NetworkProfile;
import com.socialapp.core.network.sim.PayloadWeight;
import com.socialapp.core.network.sim.ScreenPayloadConfig;
import com.socialapp.core.prefetch.policy.MarkovStubPolicy;
import com.socialapp.core.prefetch.policy.MostFrequentPolicy;
import com.socialapp.core.prefetch.policy.NoPrefetchPolicy;
import com.socialapp.core.prefetch.policy.PrefetchAllPolicy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class PrefetchEngineTest {

    private MockNetworkEngine networkEngine;
    private PrefetchCache cache;
    private PrefetchManager prefetchManager;

    @Before
    public void setUp() {
        networkEngine = new MockNetworkEngine();
        networkEngine.setSimulationEnabled(true);
        networkEngine.setProfile(NetworkProfile.LTE_4G);
        networkEngine.setWeight(PayloadWeight.NORMAL_1_0);

        cache = new PrefetchCache(10, 0); // No auto-cleanup in test
        prefetchManager = new PrefetchManager(cache, networkEngine, new MostFrequentPolicy());
    }

    @After
    public void tearDown() {
        prefetchManager.cancelAllInFlight();
        cache.clear();
        cache.shutdown();
        networkEngine.shutdown();
    }

    @Test
    public void testFullHit_InstantReturnWithZeroWait() throws Exception {
        String key = "test_home";
        String screen = ScreenPayloadConfig.SCREEN_HOME;
        String payload = "Sample Home Feed Data";
        long sizeBytes = 120 * 1024; // 120 KB

        // Seed RAM cache directly (as if prefetch finished earlier)
        cache.put(key, screen, payload, sizeBytes, 60_000L);

        // Consumer acquires data
        CompletableFuture<PrefetchResult<String>> future =
                prefetchManager.acquire(screen, key, () -> "Fallback");

        PrefetchResult<String> result = future.get(1, TimeUnit.SECONDS);

        assertNotNull(result);
        assertEquals(PrefetchHitType.FULL_HIT, result.getHitType());
        assertEquals("Sample Home Feed Data", result.getData());
        assertEquals(0L, result.getWaitMs()); // Instantaneous!
        assertTrue(result.getSavedMs() > 0);
        assertEquals(1, cache.getFullHitCount());
        assertEquals(0, cache.getMissCount());
    }

    @Test
    public void testLateHit_AttachesToInFlightTask() throws Exception {
        String screen = ScreenPayloadConfig.SCREEN_POST_DETAIL;
        String key = "default_" + screen;

        // Trigger proactive background prefetch
        prefetchManager.triggerPrefetch(screen, key, () -> "PostDetail Body 123", 250 * 1024);

        // Immediate verify in-flight status
        assertTrue(prefetchManager.getInFlightCount() >= 1);

        // User opens screen while prefetch is in-flight -> Acquire!
        CompletableFuture<PrefetchResult<String>> acquireFuture =
                prefetchManager.acquire(screen, key, () -> "Fallback");

        // Wait for download to finish
        PrefetchResult<String> result = acquireFuture.get(3, TimeUnit.SECONDS);

        assertNotNull(result);
        assertEquals(PrefetchHitType.LATE_HIT, result.getHitType());
        assertEquals("PostDetail Body 123", result.getData());
        assertEquals(1, cache.getLateHitCount());
        assertEquals(0, cache.getMissCount());
        assertEquals(0, prefetchManager.getInFlightCount());
    }

    @Test
    public void testMiss_TriggersStandardNetworkLoad() throws Exception {
        String screen = ScreenPayloadConfig.SCREEN_COMMENTS;
        String key = "uncached_comments";

        CompletableFuture<PrefetchResult<String>> future =
                prefetchManager.acquire(screen, key, () -> "Fresh Comments Data");

        PrefetchResult<String> result = future.get(3, TimeUnit.SECONDS);

        assertNotNull(result);
        assertEquals(PrefetchHitType.MISS, result.getHitType());
        assertEquals("Fresh Comments Data", result.getData());
        assertTrue(result.getWaitMs() > 0);
        assertEquals(0L, result.getSavedMs());
        assertEquals(1, cache.getMissCount());
        assertEquals(0, cache.getFullHitCount());
    }

    @Test
    public void testWastedBytes_RecordedWhenUnusedEntryExpires() throws Exception {
        String key = "wasted_key";
        long sizeBytes = 80 * 1024; // 80 KB

        // Put with short TTL of 50ms
        cache.put(key, ScreenPayloadConfig.SCREEN_COMMENTS, "Unused Comments", sizeBytes, 50L);

        assertEquals(0L, cache.getWastedBytes());

        // Wait for TTL to expire (Windows timer granularity safe)
        Thread.sleep(120);

        // Run cleanup
        int evicted = cache.cleanupExpired();
        assertEquals(1, evicted);
        assertEquals(sizeBytes, cache.getWastedBytes());
        assertEquals(1, cache.getExpiredCount());
    }

    @Test
    public void testUsefulBytes_NoWasteWhenEntryWasUsed() throws Exception {
        String key = "useful_key";
        long sizeBytes = 60 * 1024; // 60 KB

        cache.put(key, ScreenPayloadConfig.SCREEN_PROFILE, "Author Profile", sizeBytes, 50L);

        // Consumer reads and marks used
        PrefetchEntry<String> entry = cache.get(key);
        assertNotNull(entry);
        entry.markUsed();

        // Wait for TTL to expire (Windows timer granularity safe)
        Thread.sleep(120);

        // Run cleanup
        cache.cleanupExpired();

        // Must NOT count as wasted bytes!
        assertEquals(0L, cache.getWastedBytes());
        assertEquals(sizeBytes, cache.getUsefulBytes());
    }

    @Test
    public void testLruEviction_EnforcesCapacityLimit() {
        PrefetchCache smallCache = new PrefetchCache(3, 0);

        smallCache.put("k1", "s1", "d1", 100, 60_000L);
        smallCache.put("k2", "s2", "d2", 200, 60_000L);
        smallCache.put("k3", "s3", "d3", 300, 60_000L);

        assertEquals(3, smallCache.size());

        // Access k1 to make k2 the least recently used
        smallCache.get("k1");

        // Put 4th entry -> k2 should be evicted!
        smallCache.put("k4", "s4", "d4", 400, 60_000L);

        assertEquals(3, smallCache.size());
        assertNotNull(smallCache.get("k1"));
        assertNull(smallCache.get("k2")); // Evicted!
        assertNotNull(smallCache.get("k3"));
        assertNotNull(smallCache.get("k4"));

        assertEquals(200L, smallCache.getWastedBytes()); // k2 was not used -> 200 bytes wasted
        smallCache.shutdown();
    }

    @Test
    public void testPolicyPredictions() {
        NoPrefetchPolicy p0 = new NoPrefetchPolicy();
        assertTrue(p0.predictNextScreens(ScreenPayloadConfig.SCREEN_HOME, Collections.emptyList()).isEmpty());

        MostFrequentPolicy p2 = new MostFrequentPolicy();
        List<String> p2Result = p2.predictNextScreens(ScreenPayloadConfig.SCREEN_HOME, Collections.emptyList());
        assertEquals(1, p2Result.size());
        assertEquals(ScreenPayloadConfig.SCREEN_POST_DETAIL, p2Result.get(0));

        MarkovStubPolicy p3 = new MarkovStubPolicy(0.70);
        List<String> p3Result = p3.predictNextScreens(ScreenPayloadConfig.SCREEN_HOME, Collections.emptyList());
        // 85% PostDetail >= 70% threshold, but 15% Search < 70%
        assertEquals(1, p3Result.size());
        assertEquals(ScreenPayloadConfig.SCREEN_POST_DETAIL, p3Result.get(0));
    }
}
