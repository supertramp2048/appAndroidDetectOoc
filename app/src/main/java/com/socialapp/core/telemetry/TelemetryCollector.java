package com.socialapp.core.telemetry;

import com.socialapp.core.prefetch.ScreenId;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Processes confirmed screens in order and snapshots attribution for asynchronous loads. */
public final class TelemetryCollector {
    public interface SessionListener {
        void onSessionChanged(UUID runId, UUID sessionId, Integer replaySessionId);
    }

    private final LongSupplier clock;
    private final SessionListener sessionListener;
    private final SessionTracker sessions = new SessionTracker();
    private UUID publishedSession;
    private UUID previousSessionId;
    private ScreenId previousScreen;
    private NavigationEvent lastNavigation;

    public TelemetryCollector(LongSupplier clock) {
        this(clock, (runId, sessionId, replaySessionId) -> {});
    }

    public TelemetryCollector(LongSupplier clock, SessionListener sessionListener) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sessionListener = Objects.requireNonNull(sessionListener, "sessionListener");
    }

    public synchronized UUID beginReplayRun() {
        UUID runId = sessions.beginReplayRun();
        publishSessionChange();
        return runId;
    }

    public synchronized void beginReplaySession(int traceSessionId) {
        sessions.beginReplaySession(traceSessionId);
        publishSessionChange();
    }

    public synchronized void endReplaySession() {
        sessions.endReplaySession();
        publishSessionChange();
    }

    public synchronized void endReplayRun() {
        sessions.endReplayRun();
        publishSessionChange();
    }

    /** Extends the LIVE session for user activity that does not change screens. */
    public synchronized void recordLiveActivity() {
        sessions.recordLiveActivity(clock.getAsLong());
        publishSessionChange();
    }

    /** Breaks the navigation chain for a confirmed screen outside the research scope. */
    public synchronized void confirmOutOfScopeScreen() {
        recordLiveActivity();
        previousScreen = null;
        lastNavigation = null;
    }

    synchronized NavigationEvent getConfirmedNavigation(ScreenId screen) {
        return lastNavigation != null && lastNavigation.toScreen == screen
                && lastNavigation.sessionId.equals(sessions.getSessionId()) ? lastNavigation : null;
    }

    public synchronized LoadContext beginLoad(ScreenId screen, LoadContext.Trigger trigger,
                                              NavigationEvent confirmedNavigation) {
        long now = clock.getAsLong();
        sessions.ensureLiveSession(now);
        publishSessionChange();
        NavigationEvent current = getConfirmedNavigation(screen);
        UUID navigationId = current != null && current == confirmedNavigation ? current.navigationId : null;
        return new LoadContext(sessions.getRunId(), sessions.getSessionId(), sessions.getReplaySessionId(),
                navigationId, screen, sessions.getSource(), trigger, now);
    }

    /** Serializes the active-state check and its effects with replay session boundaries. */
    public synchronized void withLoadSession(LoadContext load, Consumer<Boolean> action) {
        boolean current = load.sessionId != null && load.sessionId.equals(sessions.getSessionId())
                && load.runId.equals(sessions.getRunId());
        action.accept(current);
    }

    /** Returns empty for a duplicate screen or while replay is between sessions. */
    public synchronized Optional<NavigationEvent> confirmScreen(ScreenId screen) {
        Objects.requireNonNull(screen, "screen");
        long timestampMs = clock.getAsLong();
        sessions.recordLiveActivity(timestampMs);
        publishSessionChange();
        UUID sessionId = sessions.getSessionId();
        if (sessionId == null) {
            return Optional.empty();
        }
        if (!sessionId.equals(previousSessionId)) {
            previousScreen = null;
        }
        if (screen == previousScreen) {
            return Optional.empty();
        }

        NavigationEvent event = sessions.createEvent(previousScreen, screen, timestampMs);
        previousSessionId = sessionId;
        previousScreen = screen;
        lastNavigation = event;
        return Optional.of(event);
    }

    private void publishSessionChange() {
        UUID current = sessions.getSessionId();
        if (!Objects.equals(publishedSession, current)) {
            publishedSession = current;
            sessionListener.onSessionChanged(sessions.getRunId(), current, sessions.getReplaySessionId());
        }
    }
}
