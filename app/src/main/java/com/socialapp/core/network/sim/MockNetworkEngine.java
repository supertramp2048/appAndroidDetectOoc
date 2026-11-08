package com.socialapp.core.network.sim;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Task C1 & C2: Mock Network Engine.
 * 
 * Simulates deterministic network latency across 4 profiles (Wi-Fi, 4G, 3G, EDGE)
 * with 3 payload weight factors (0.5x, 1.0x, 2.0x).
 * 
 * Supports dynamic continuous bandwidth sharing (fluid flow model):
 * When N requests run concurrently, the bottleneck link is shared (EffectiveBW = TotalBW / N).
 * Whenever a new request joins or leaves, remaining times are dynamically recalculated.
 */
public class MockNetworkEngine {

    public interface MockNetworkListener {
        void onRequestCompleted(@NonNull SimulatedNetworkResponse<?> response);
        void onRequestFailed(@NonNull String requestId, @NonNull Throwable error);
    }

    private static volatile MockNetworkEngine instance;

    public static MockNetworkEngine getInstance() {
        if (instance == null) {
            synchronized (MockNetworkEngine.class) {
                if (instance == null) {
                    instance = new MockNetworkEngine();
                }
            }
        }
        return instance;
    }

    private final Object lock = new Object();
    private final ScheduledExecutorService scheduler;
    private final Map<String, ActiveTransfer<?>> activeTransfers = new LinkedHashMap<>();
    private final List<MockNetworkListener> listeners = new CopyOnWriteArrayList<>();

    private volatile NetworkProfile currentProfile = NetworkProfile.LTE_4G;
    private volatile PayloadWeight currentWeight = PayloadWeight.NORMAL_1_0;
    private volatile boolean isSimulationEnabled = true;

    private long lastUpdateTimeMs = System.currentTimeMillis();

    public MockNetworkEngine() {
        this.scheduler = Executors.newScheduledThreadPool(4, new ThreadFactory() {
            private final AtomicInteger count = new AtomicInteger(1);
            @Override
            public Thread newThread(@NonNull Runnable r) {
                Thread t = new Thread(r, "MockNetEngine-" + count.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });
    }

    // --- Configuration Getters & Setters ---

    @NonNull
    public NetworkProfile getCurrentProfile() {
        return currentProfile;
    }

    public void setProfile(@NonNull NetworkProfile profile) {
        synchronized (lock) {
            this.currentProfile = profile;
            updateStateAndRescheduleLocked();
        }
    }

    @NonNull
    public PayloadWeight getCurrentWeight() {
        return currentWeight;
    }

    public void setWeight(@NonNull PayloadWeight weight) {
        synchronized (lock) {
            this.currentWeight = weight;
            updateStateAndRescheduleLocked();
        }
    }

    public boolean isSimulationEnabled() {
        return isSimulationEnabled;
    }

    public void setSimulationEnabled(boolean enabled) {
        this.isSimulationEnabled = enabled;
    }

    public void addListener(@NonNull MockNetworkListener listener) {
        listeners.add(listener);
    }

    public void removeListener(@NonNull MockNetworkListener listener) {
        listeners.remove(listener);
    }

    public int getActiveRequestCount() {
        synchronized (lock) {
            return activeTransfers.size();
        }
    }

    public double getCurrentEffectiveBandwidthKbps() {
        synchronized (lock) {
            int count = Math.max(1, activeTransfers.size());
            return currentProfile.getBandwidthKbps() / count;
        }
    }

    // --- Execution APIs ---

    /**
     * Executes a simulated network request for a screen, returning simulated telemetry data.
     */
    @NonNull
    public CompletableFuture<SimulatedNetworkResponse<Void>> executeSimulatedRequest(
            @NonNull String requestId,
            @NonNull String screenName,
            boolean isForeground) {
        return executeWithDataSupplier(requestId, screenName, isForeground, () -> null);
    }

    /**
     * Executes a network request wrapped with simulated delay and dynamic bandwidth sharing.
     * When the simulated delay completes, realDataSupplier is invoked to provide the payload.
     */
    @NonNull
    public <T> CompletableFuture<SimulatedNetworkResponse<T>> executeWithDataSupplier(
            @NonNull String requestId,
            @NonNull String screenName,
            boolean isForeground,
            @Nullable Supplier<T> realDataSupplier) {

        long now = System.currentTimeMillis();
        CompletableFuture<SimulatedNetworkResponse<T>> future = new CompletableFuture<>();

        // If simulation is disabled, return immediately
        if (!isSimulationEnabled) {
            try {
                T data = realDataSupplier != null ? realDataSupplier.get() : null;
                double sizeKb = ScreenPayloadConfig.getBaseSizeKb(screenName) * currentWeight.getMultiplier();
                SimulatedNetworkResponse<T> resp = new SimulatedNetworkResponse<>(
                        requestId, screenName, isForeground, currentProfile, currentWeight,
                        sizeKb, currentProfile.getBandwidthKbps(), 0, 0, 0, 0, now, now, data
                );
                future.complete(resp);
                notifySuccess(resp);
            } catch (Throwable ex) {
                future.completeExceptionally(ex);
                notifyFailure(requestId, ex);
            }
            return future;
        }

        synchronized (lock) {
            // Compute total transfer size
            double baseSize = ScreenPayloadConfig.getBaseSizeKb(screenName);
            double totalBytesKb = baseSize * currentWeight.getMultiplier();
            long rttMs = currentProfile.getRttMs();

            ActiveTransfer<T> transfer = new ActiveTransfer<>(
                    requestId,
                    screenName,
                    isForeground,
                    totalBytesKb,
                    rttMs,
                    now,
                    realDataSupplier,
                    future
            );

            activeTransfers.put(requestId, transfer);
            updateStateAndRescheduleLocked();
        }

        return future;
    }

    /**
     * Cancels an in-flight request, releasing its share of bandwidth to other requests.
     */
    public boolean cancelRequest(@NonNull String requestId) {
        synchronized (lock) {
            ActiveTransfer<?> transfer = activeTransfers.remove(requestId);
            if (transfer != null) {
                if (transfer.scheduledTimer != null) {
                    transfer.scheduledTimer.cancel(false);
                    transfer.scheduledTimer = null;
                }
                transfer.future.cancel(true);
                updateStateAndRescheduleLocked();
                return true;
            }
            return false;
        }
    }

    public void cancelAll() {
        synchronized (lock) {
            for (ActiveTransfer<?> t : activeTransfers.values()) {
                if (t.scheduledTimer != null) {
                    t.scheduledTimer.cancel(true);
                }
                t.future.cancel(true);
            }
            activeTransfers.clear();
        }
    }

    public void shutdown() {
        cancelAll();
        scheduler.shutdownNow();
    }

    // --- State Machine & Dynamic Bandwidth Rescheduling ---

    private void updateStateAndRescheduleLocked() {
        long now = System.currentTimeMillis();
        long elapsed = Math.max(0, now - lastUpdateTimeMs);
        lastUpdateTimeMs = now;

        int activeCount = activeTransfers.size();
        if (activeCount == 0) {
            return;
        }

        // 1. Progress active transfers using the bandwidth from the elapsed time slice
        if (elapsed > 0) {
            double sliceEffectiveBw = currentProfile.getBandwidthKbps() / activeCount;
            for (ActiveTransfer<?> t : activeTransfers.values()) {
                if (now > t.rttEndTimeMs) {
                    // Calculate time elapsed strictly after RTT ended
                    long timeAfterRtt = Math.min(elapsed, now - t.rttEndTimeMs);
                    double transferInSliceKb = sliceEffectiveBw * (timeAfterRtt / 1000.0);
                    t.remainingBytesKb = Math.max(0.0, t.remainingBytesKb - transferInSliceKb);
                }
            }
        }

        // 2. Reschedule completion timers based on new effective bandwidth
        double newEffectiveBw = currentProfile.getBandwidthKbps() / activeCount;
        List<ActiveTransfer<?>> readyToComplete = new ArrayList<>();

        for (ActiveTransfer<?> t : activeTransfers.values()) {
            if (t.scheduledTimer != null && !t.scheduledTimer.isDone()) {
                t.scheduledTimer.cancel(false);
                t.scheduledTimer = null;
            }

            long timeUntilRttDone = Math.max(0, t.rttEndTimeMs - now);
            long transferTimeRemainingMs = (long) Math.ceil((t.remainingBytesKb / newEffectiveBw) * 1000.0);
            long totalRemainingMs = timeUntilRttDone + transferTimeRemainingMs;

            if (totalRemainingMs <= 0) {
                readyToComplete.add(t);
            } else {
                final String reqId = t.requestId;
                t.scheduledTimer = scheduler.schedule(() -> onTransferTimerFired(reqId),
                        totalRemainingMs, TimeUnit.MILLISECONDS);
            }
        }

        // Complete any transfers that are mathematically finished
        for (ActiveTransfer<?> t : readyToComplete) {
            completeTransferLocked(t);
        }
    }

    private void onTransferTimerFired(@NonNull String requestId) {
        synchronized (lock) {
            ActiveTransfer<?> transfer = activeTransfers.get(requestId);
            if (transfer == null) {
                return;
            }
            completeTransferLocked(transfer);
            updateStateAndRescheduleLocked();
        }
    }

    private void completeTransferLocked(@NonNull ActiveTransfer<?> t) {
        activeTransfers.remove(t.requestId);
        if (t.scheduledTimer != null) {
            t.scheduledTimer.cancel(false);
            t.scheduledTimer = null;
        }

        long now = System.currentTimeMillis();
        long totalLatencyMs = Math.max(1, now - t.startTimeMs);
        long transferTimeMs = Math.max(0, totalLatencyMs - t.rttMs);
        double effectiveBw = t.totalBytesKb / Math.max(0.001, (transferTimeMs / 1000.0));

        Object data = null;
        try {
            if (t.dataSupplier != null) {
                data = t.dataSupplier.get();
            }

            @SuppressWarnings({"unchecked", "rawtypes"})
            SimulatedNetworkResponse response = new SimulatedNetworkResponse<>(
                    t.requestId,
                    t.screenName,
                    t.isForeground,
                    currentProfile,
                    currentWeight,
                    t.totalBytesKb,
                    effectiveBw,
                    t.rttMs,
                    transferTimeMs,
                    totalLatencyMs,
                    activeTransfers.size(),
                    t.startTimeMs,
                    now,
                    data
            );

            //noinspection unchecked
            ((ActiveTransfer) t).future.complete(response);
            notifySuccess(response);
        } catch (Throwable ex) {
            t.future.completeExceptionally(ex);
            notifyFailure(t.requestId, ex);
        }
    }

    private void notifySuccess(@NonNull SimulatedNetworkResponse<?> response) {
        for (MockNetworkListener listener : listeners) {
            try {
                listener.onRequestCompleted(response);
            } catch (Throwable ignored) {}
        }
    }

    private void notifyFailure(@NonNull String requestId, @NonNull Throwable error) {
        for (MockNetworkListener listener : listeners) {
            try {
                listener.onRequestFailed(requestId, error);
            } catch (Throwable ignored) {}
        }
    }

    // --- Inner Transfer State Representation ---

    private static class ActiveTransfer<T> {
        final String requestId;
        final String screenName;
        final boolean isForeground;
        final double totalBytesKb;
        double remainingBytesKb;
        final long rttMs;
        final long rttEndTimeMs;
        final long startTimeMs;
        final Supplier<T> dataSupplier;
        final CompletableFuture<SimulatedNetworkResponse<T>> future;
        ScheduledFuture<?> scheduledTimer;

        ActiveTransfer(@NonNull String requestId,
                       @NonNull String screenName,
                       boolean isForeground,
                       double totalBytesKb,
                       long rttMs,
                       long startTimeMs,
                       @Nullable Supplier<T> dataSupplier,
                       @NonNull CompletableFuture<SimulatedNetworkResponse<T>> future) {
            this.requestId = requestId;
            this.screenName = screenName;
            this.isForeground = isForeground;
            this.totalBytesKb = totalBytesKb;
            this.remainingBytesKb = totalBytesKb;
            this.rttMs = rttMs;
            this.startTimeMs = startTimeMs;
            this.rttEndTimeMs = startTimeMs + rttMs;
            this.dataSupplier = dataSupplier;
            this.future = future;
        }
    }
}
