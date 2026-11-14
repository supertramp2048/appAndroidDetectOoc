package com.socialapp.core.network.sim;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Result and telemetry metadata returned by MockNetworkEngine.
 * Contains detailed breakdown of network simulation (RTT, transfer time, effective BW, concurrent requests).
 */
public class SimulatedNetworkResponse<T> {

    private final String requestId;
    private final String screenName;
    private final boolean isForeground;
    private final NetworkProfile profile;
    private final PayloadWeight weight;
    private final double payloadSizeKb;
    private final double effectiveBandwidthKbps;
    private final long rttMs;
    private final long transferTimeMs;
    private final long totalLatencyMs;
    private final int concurrentRequestsAtFinish;
    private final long startTimeMs;
    private final long finishTimeMs;
    private final T data;

    public SimulatedNetworkResponse(@NonNull String requestId,
                                    @NonNull String screenName,
                                    boolean isForeground,
                                    @NonNull NetworkProfile profile,
                                    @NonNull PayloadWeight weight,
                                    double payloadSizeKb,
                                    double effectiveBandwidthKbps,
                                    long rttMs,
                                    long transferTimeMs,
                                    long totalLatencyMs,
                                    int concurrentRequestsAtFinish,
                                    long startTimeMs,
                                    long finishTimeMs,
                                    @Nullable T data) {
        this.requestId = requestId;
        this.screenName = screenName;
        this.isForeground = isForeground;
        this.profile = profile;
        this.weight = weight;
        this.payloadSizeKb = payloadSizeKb;
        this.effectiveBandwidthKbps = effectiveBandwidthKbps;
        this.rttMs = rttMs;
        this.transferTimeMs = transferTimeMs;
        this.totalLatencyMs = totalLatencyMs;
        this.concurrentRequestsAtFinish = concurrentRequestsAtFinish;
        this.startTimeMs = startTimeMs;
        this.finishTimeMs = finishTimeMs;
        this.data = data;
    }

    @NonNull
    public String getRequestId() {
        return requestId;
    }

    @NonNull
    public String getScreenName() {
        return screenName;
    }

    public boolean isForeground() {
        return isForeground;
    }

    @NonNull
    public NetworkProfile getProfile() {
        return profile;
    }

    @NonNull
    public PayloadWeight getWeight() {
        return weight;
    }

    public double getPayloadSizeKb() {
        return payloadSizeKb;
    }

    public double getEffectiveBandwidthKbps() {
        return effectiveBandwidthKbps;
    }

    public long getRttMs() {
        return rttMs;
    }

    public long getTransferTimeMs() {
        return transferTimeMs;
    }

    public long getTotalLatencyMs() {
        return totalLatencyMs;
    }

    public int getConcurrentRequestsAtFinish() {
        return concurrentRequestsAtFinish;
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }

    public long getFinishTimeMs() {
        return finishTimeMs;
    }

    @Nullable
    public T getData() {
        return data;
    }

    @NonNull
    @Override
    public String toString() {
        return "SimulatedNetworkResponse{" +
                "screen='" + screenName + '\'' +
                ", profile=" + profile.name() +
                ", weight=" + weight.name() +
                ", size=" + payloadSizeKb + "KB" +
                ", latency=" + totalLatencyMs + "ms" +
                " (rtt=" + rttMs + "ms, transfer=" + transferTimeMs + "ms)" +
                ", concurrent=" + concurrentRequestsAtFinish +
                '}';
    }
}
