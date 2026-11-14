package com.socialapp.core.network.sim;

import androidx.annotation.NonNull;

/**
 * 4 Network Profiles as specified in Task C1 & C2:
 * - Wi-Fi: RTT 15ms, Bandwidth 6,250 KB/s (50 Mbps)
 * - LTE (4G): RTT 50ms, Bandwidth 2,500 KB/s (20 Mbps)
 * - 3G: RTT 180ms, Bandwidth 250 KB/s (2 Mbps)
 * - EDGE (2G): RTT 400ms, Bandwidth 20 KB/s (160 Kbps)
 */
public enum NetworkProfile {
    WIFI(15, 6250.0, "Wi-Fi (50 Mbps)"),
    LTE_4G(50, 2500.0, "LTE / 4G (20 Mbps)"),
    NETWORK_3G(180, 250.0, "3G (2 Mbps)"),
    EDGE_2G(400, 20.0, "EDGE / 2G (160 Kbps)");

    private final long rttMs;
    private final double bandwidthKbps; // KB/s (Kilobytes per second)
    private final String displayName;

    NetworkProfile(long rttMs, double bandwidthKbps, @NonNull String displayName) {
        this.rttMs = rttMs;
        this.bandwidthKbps = bandwidthKbps;
        this.displayName = displayName;
    }

    public long getRttMs() {
        return rttMs;
    }

    /**
     * Total link bandwidth in Kilobytes per second (KB/s).
     */
    public double getBandwidthKbps() {
        return bandwidthKbps;
    }

    @NonNull
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Calculates the deterministic nominal latency (ms) for a given payload size (KB)
     * and weight multiplier, considering concurrent active connections.
     * Formula: Latency = RTT + (PayloadSize * Weight) / (TotalBandwidth / N)
     *
     * @param payloadSizeKb Base payload size in Kilobytes
     * @param weightMultiplier Weight factor (0.5, 1.0, 2.0)
     * @param concurrentRequests Number of active requests sharing the bandwidth (>= 1)
     * @return Theoretical latency in milliseconds
     */
    public long calculateLatencyMs(double payloadSizeKb, double weightMultiplier, int concurrentRequests) {
        int n = Math.max(1, concurrentRequests);
        double effectiveBandwidth = bandwidthKbps / n; // KB/s
        double effectiveSize = payloadSizeKb * weightMultiplier; // KB
        double transferTimeMs = (effectiveSize / effectiveBandwidth) * 1000.0;
        return rttMs + (long) Math.ceil(transferTimeMs);
    }
}
