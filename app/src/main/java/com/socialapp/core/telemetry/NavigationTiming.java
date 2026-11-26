package com.socialapp.core.telemetry;

import java.util.UUID;

/** One timing per explicit navigation request. Used on the main thread. */
public final class NavigationTiming {
    public final UUID id = UUID.randomUUID();
    public final long startedAtMs;
    private boolean reported;

    public NavigationTiming(long startedAtMs) {
        this.startedAtMs = startedAtMs;
    }

    public boolean markReady(long nowMs) {
        if (reported || nowMs < startedAtMs) return false;
        reported = true;
        return true;
    }
}
