package com.socialapp.core.network.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class MockNetworkEngineTest {

    private MockNetworkEngine engine;

    @Before
    public void setUp() {
        engine = new MockNetworkEngine();
    }

    @After
    public void tearDown() {
        engine.setSimulationEnabled(false);
    }

    @Test
    public void testNetworkProfileTheoreticalLatency() {
        // Wi-Fi: RTT 15ms, BW 6250 KB/s
        // Size: 1250 KB, Weight: 1.0, N = 1
        // Transfer: 1250 / 6250 = 0.2s = 200ms -> Latency = 15 + 200 = 215ms
        long wifiLatency = NetworkProfile.WIFI.calculateLatencyMs(1250.0, 1.0, 1);
        assertEquals(215, wifiLatency);

        // LTE: RTT 50ms, BW 2500 KB/s
        // Size: 500 KB, Weight: 1.0, N = 1 -> 500 / 2500 = 0.2s = 200ms -> Latency = 50 + 200 = 250ms
        long lteLatency = NetworkProfile.LTE_4G.calculateLatencyMs(500.0, 1.0, 1);
        assertEquals(250, lteLatency);

        // 3G: RTT 180ms, BW 250 KB/s
        // Size: 250 KB, Weight: 2.0 (Heavy -> 500 KB), N = 1 -> 500 / 250 = 2s = 2000ms -> Latency = 180 + 2000 = 2180ms
        long g3Latency = NetworkProfile.NETWORK_3G.calculateLatencyMs(250.0, 2.0, 1);
        assertEquals(2180, g3Latency);

        // EDGE: RTT 400ms, BW 20 KB/s
        // Size: 20 KB, Weight: 0.5 (Light -> 10 KB), N = 1 -> 10 / 20 = 0.5s = 500ms -> Latency = 400 + 500 = 900ms
        long edgeLatency = NetworkProfile.EDGE_2G.calculateLatencyMs(20.0, 0.5, 1);
        assertEquals(900, edgeLatency);
    }

    @Test
    public void testBandwidthSharingCalculation() {
        // LTE: BW 2500 KB/s. With N = 2 concurrent requests, effective BW = 1250 KB/s.
        // Size: 250 KB -> Transfer: 250 / 1250 = 0.2s = 200ms -> Latency = 50 + 200 = 250ms
        long lteLatencyN2 = NetworkProfile.LTE_4G.calculateLatencyMs(250.0, 1.0, 2);
        assertEquals(250, lteLatencyN2);
    }

    @Test
    public void testSimulationDisabledCompletesInstantly() throws ExecutionException, InterruptedException, TimeoutException {
        engine.setSimulationEnabled(false);
        CompletableFuture<SimulatedNetworkResponse<String>> future = engine.executeWithDataSupplier(
                "req-1",
                ScreenPayloadConfig.SCREEN_HOME,
                true,
                () -> "Hello World"
        );

        SimulatedNetworkResponse<String> response = future.get(50, TimeUnit.MILLISECONDS);
        assertNotNull(response);
        assertEquals("Hello World", response.getData());
        assertEquals("req-1", response.getRequestId());
        assertEquals(0, engine.getActiveRequestCount());
    }

    @Test
    public void testSingleRequestExecutionWithDelay() throws ExecutionException, InterruptedException, TimeoutException {
        // Use fast Wi-Fi and light weight for quick test
        engine.setProfile(NetworkProfile.WIFI);
        engine.setWeight(PayloadWeight.LIGHT_0_5);
        ScreenPayloadConfig.registerSize("test_small_screen", 6.25); // 6.25 KB * 0.5 = 3.125 KB
        // Transfer time = 3.125 / 6250 = 0.5ms. Total latency ~ 15ms RTT + 1ms transfer = ~16ms

        long start = System.currentTimeMillis();
        CompletableFuture<SimulatedNetworkResponse<Integer>> future = engine.executeWithDataSupplier(
                "req-single",
                "test_small_screen",
                true,
                () -> 42
        );

        SimulatedNetworkResponse<Integer> response = future.get(1000, TimeUnit.MILLISECONDS);
        long elapsed = System.currentTimeMillis() - start;

        assertNotNull(response);
        assertEquals(Integer.valueOf(42), response.getData());
        assertTrue("Elapsed should be at least RTT (~15ms), was " + elapsed, elapsed >= 12);
        assertEquals(0, engine.getActiveRequestCount());
    }

    @Test
    public void testCancellationFreesRequest() {
        engine.setProfile(NetworkProfile.EDGE_2G); // Slow profile
        engine.setWeight(PayloadWeight.HEAVY_2_0);

        CompletableFuture<SimulatedNetworkResponse<Void>> future = engine.executeSimulatedRequest(
                "req-cancel",
                ScreenPayloadConfig.SCREEN_POST_DETAIL,
                false
        );

        assertEquals(1, engine.getActiveRequestCount());
        boolean cancelled = engine.cancelRequest("req-cancel");
        assertTrue(cancelled);
        assertEquals(0, engine.getActiveRequestCount());
        assertTrue(future.isCancelled());
    }

    @Test
    public void testListenerNotification() throws ExecutionException, InterruptedException, TimeoutException {
        engine.setSimulationEnabled(false);
        AtomicBoolean completed = new AtomicBoolean(false);

        engine.addListener(new MockNetworkEngine.MockNetworkListener() {
            @Override
            public void onRequestCompleted(SimulatedNetworkResponse<?> response) {
                if ("listener-test".equals(response.getRequestId())) {
                    completed.set(true);
                }
            }

            @Override
            public void onRequestFailed(String requestId, Throwable error) {}
        });

        engine.executeSimulatedRequest("listener-test", ScreenPayloadConfig.SCREEN_PROFILE, true)
                .get(100, TimeUnit.MILLISECONDS);

        assertTrue(completed.get());
    }
}
