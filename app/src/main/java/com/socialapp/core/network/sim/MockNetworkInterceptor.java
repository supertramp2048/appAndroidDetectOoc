package com.socialapp.core.network.sim;

import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Task C1 & C2: OkHttp Interceptor that injects deterministic simulated latency
 * and dynamic bandwidth sharing into real app network requests.
 * 
 * Runs in-process with the app, transparently applying the currently selected
 * NetworkProfile (Wi-Fi, 4G, 3G, EDGE) and PayloadWeight (0.5x, 1.0x, 2.0x).
 */
public class MockNetworkInterceptor implements Interceptor {

    private static final String TAG = "MockNetworkEngine";

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        MockNetworkEngine engine = MockNetworkEngine.getInstance();

        if (!engine.isSimulationEnabled()) {
            return chain.proceed(request);
        }

        String url = request.url().toString();
        String screenName = mapUrlToScreen(url);
        String requestId = "http-" + UUID.randomUUID().toString().substring(0, 8);

        long startNs = SystemClock.elapsedRealtimeNanos();

        // Register request with MockNetworkEngine to apply mathematical delay and share bandwidth
        CompletableFuture<SimulatedNetworkResponse<Void>> delayFuture =
                engine.executeSimulatedRequest(requestId, screenName, true);

        try {
            // Await the deterministic simulated delay
            SimulatedNetworkResponse<Void> simResponse = delayFuture.get();

            // Proceed with the actual network call (or mock pipeline)
            Response response = chain.proceed(request);

            long totalElapsedMs = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000;

            Log.i(TAG, String.format(
                    "[MockNetwork] [%s] %s -> Delay: %dms (RTT: %dms, Transfer: %dms) | BW: %.1f KB/s | N=%d | Total: %dms",
                    simResponse.getProfile().name(),
                    screenName,
                    simResponse.getTotalLatencyMs(),
                    simResponse.getRttMs(),
                    simResponse.getTransferTimeMs(),
                    simResponse.getEffectiveBandwidthKbps(),
                    simResponse.getConcurrentRequestsAtFinish(),
                    totalElapsedMs
            ));

            return response;

        } catch (InterruptedException e) {
            engine.cancelRequest(requestId);
            Thread.currentThread().interrupt();
            throw new IOException("Simulated network request interrupted", e);
        } catch (Exception e) {
            engine.cancelRequest(requestId);
            throw new IOException("Simulated network error: " + e.getMessage(), e);
        }
    }

    /**
     * Maps an HTTP URL path to standard ScreenPayloadConfig identifiers.
     */
    @NonNull
    private String mapUrlToScreen(@NonNull String url) {
        String lower = url.toLowerCase();
        if (lower.contains("/api/categories") || lower.contains("/api/posts?page=1")) {
            return ScreenPayloadConfig.SCREEN_HOME;
        } else if (lower.contains("/api/posts/")) {
            if (lower.contains("/comments") || lower.contains("/replies")) {
                return ScreenPayloadConfig.SCREEN_COMMENTS;
            }
            return ScreenPayloadConfig.SCREEN_POST_DETAIL;
        } else if (lower.contains("/api/user") || lower.contains("/api/profiles")) {
            return ScreenPayloadConfig.SCREEN_PROFILE;
        } else if (lower.contains("/api/factcheck") || lower.contains("hf.space")) {
            return ScreenPayloadConfig.SCREEN_FACTCHECK;
        } else if (lower.contains("/api/search")) {
            return ScreenPayloadConfig.SCREEN_SEARCH;
        }
        return ScreenPayloadConfig.SCREEN_HOME;
    }
}
