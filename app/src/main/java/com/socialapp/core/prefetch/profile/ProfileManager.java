package com.socialapp.core.prefetch.profile;

import androidx.annotation.MainThread;

import com.socialapp.core.prefetch.PrefetchEngine;
import com.socialapp.core.telemetry.EventSource;
import com.socialapp.core.telemetry.NavigationEvent;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Coordinates the single mutable model and its asynchronous LIVE checkpoints. */
@MainThread
public final class ProfileManager {
    private static final int CHECKPOINT_INTERVAL = 10;

    private final PrefetchEngine engine;
    private final ProfileStore store;
    private final Executor disk;
    private final Consumer<Runnable> postToMain;
    private final Consumer<String> logWarning;
    private final Queue<NavigationEvent> startupEvents = new ArrayDeque<>();

    private boolean ready;
    private boolean checkpointAfterStartup;
    private boolean resetDuringStartup;
    private int unsavedChanges;
    private long liveVersion;
    /** Present only while the engine is running an isolated replay. */
    private NavigationProfile liveBeforeReplay;

    public ProfileManager(PrefetchEngine engine, ProfileStore store, Executor disk,
                          Consumer<Runnable> postToMain, Consumer<String> logWarning) {
        this.engine = engine;
        this.store = store;
        this.disk = disk;
        this.postToMain = postToMain;
        this.logWarning = logWarning;
        disk.execute(this::readLiveProfile);
    }

    public void onNavigationEvent(NavigationEvent event) {
        if (event.source == EventSource.REPLAY) {
            if (liveBeforeReplay != null) engine.onNavigationEvent(event);
        } else if (liveBeforeReplay == null) {
            if (!ready) startupEvents.add(event);
            else learnLive(event);
        }
    }

    public void beginReplay() {
        if (liveBeforeReplay != null) throw new IllegalStateException("A profile replay is already active");
        NavigationProfile checkpoint = checkpointLive();
        liveBeforeReplay = checkpoint != null ? checkpoint : engine.snapshotProfile(liveVersion);
        engine.resetSession();
    }

    public void endReplay() {
        if (liveBeforeReplay == null) return;
        engine.restoreProfile(liveBeforeReplay);
        liveBeforeReplay = null;
        if (ready) resumeLive();
    }

    public void resetModel() {
        engine.resetSession();
        if (liveBeforeReplay != null) return;
        startupEvents.clear();
        if (!ready) resetDuringStartup = true;
        unsavedChanges++;
        saveLiveProfile();
    }

    public void saveLiveProfile() {
        if (liveBeforeReplay != null) return;
        if (!ready) checkpointAfterStartup = true;
        else checkpointLive();
    }

    private void learnLive(NavigationEvent event) {
        if (engine.onNavigationEvent(event) && ++unsavedChanges >= CHECKPOINT_INTERVAL) checkpointLive();
    }

    private void readLiveProfile() {
        NavigationProfile saved = null;
        try {
            saved = store.read().orElse(null);
        } catch (IOException | RuntimeException error) {
            logWarning.accept("LIVE profile read failed: " + error.getClass().getSimpleName());
        }
        NavigationProfile result = saved;
        postToMain.accept(() -> finishStartup(result));
    }

    private void finishStartup(NavigationProfile saved) {
        if (saved != null && engine.supportsProfile(saved)) {
            liveVersion = saved.profileVersion;
            if (!resetDuringStartup) {
                if (liveBeforeReplay == null) engine.restoreProfile(saved);
                else liveBeforeReplay = saved;
            }
        } else if (saved != null) {
            logWarning.accept("LIVE profile uses incompatible model metadata");
        }
        ready = true;
        if (liveBeforeReplay == null) resumeLive();
    }

    private void resumeLive() {
        while (!startupEvents.isEmpty()) learnLive(startupEvents.remove());
        if (checkpointAfterStartup) {
            checkpointAfterStartup = false;
            checkpointLive();
        }
    }

    private NavigationProfile checkpointLive() {
        if (!ready || liveBeforeReplay != null || unsavedChanges == 0) return null;
        if (liveVersion == Long.MAX_VALUE) {
            logWarning.accept("LIVE profile version exhausted");
            return null;
        }
        NavigationProfile snapshot = engine.snapshotProfile(++liveVersion);
        unsavedChanges = 0;
        disk.execute(() -> {
            try {
                store.write(snapshot);
            } catch (IOException | RuntimeException error) {
                logWarning.accept("LIVE profile write failed: " + error.getClass().getSimpleName());
                postToMain.accept(() -> {
                    if (liveVersion == snapshot.profileVersion) unsavedChanges = Math.max(1, unsavedChanges);
                });
            }
        });
        return snapshot;
    }
}
