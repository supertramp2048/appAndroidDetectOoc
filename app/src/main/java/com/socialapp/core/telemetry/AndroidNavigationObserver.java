package com.socialapp.core.telemetry;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.Nullable;

import com.socialapp.core.prefetch.ScreenId;
import com.socialapp.core.navigation.Navigator;
import com.socialapp.feature.comments.CommentsActivity;
import com.socialapp.feature.feed.FeedFragment;
import com.socialapp.feature.factcheck.MediaProjectionActivity;
import com.socialapp.feature.home.HomeActivity;
import com.socialapp.feature.post.PostDetailActivity;
import com.socialapp.feature.profile.ProfileActivity;
import com.socialapp.feature.settings.SettingsActivity;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@MainThread
public final class AndroidNavigationObserver {
    private static final String TAG = "NavigationTelemetry";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final TelemetryCollector collector;
    private final Consumer<NavigationEvent> navigationConsumer;
    private final Runnable confirmPendingScreen = this::confirmResumedScreen;
    private Activity resumedActivity;
    private boolean confirmationPending;
    private ReplayScreenListener replayListener;
    private Intent abandonedReplayIntent;
    private ScreenId abandonedReplayScreen;

    public interface ReplayScreenListener {
        boolean onScreenObserved(long requestId, @Nullable ScreenId screen);
    }

    public AndroidNavigationObserver(TelemetryCollector collector, Consumer<NavigationEvent> navigationConsumer) {
        this.collector = collector;
        this.navigationConsumer = navigationConsumer;
    }

    public UUID beginReplayRun(ReplayScreenListener listener) {
        UUID runId = collector.beginReplayRun();
        mainHandler.removeCallbacks(confirmPendingScreen);
        abandonedReplayIntent = null;
        replayListener = listener;
        return runId;
    }

    public void beginReplaySession(int traceSessionId) {
        collector.beginReplaySession(traceSessionId);
    }

    public void endReplaySession() {
        collector.endReplaySession();
    }

    public void endReplayRun() {
        mainHandler.removeCallbacks(confirmPendingScreen);
        replayListener = null;
        collector.endReplayRun();
    }

    public Optional<NavigationEvent> recordReplayConfirmation(ScreenId screen) {
        Optional<NavigationEvent> event = collector.confirmScreen(screen);
        event.ifPresent(this::publishNavigation);
        return event;
    }

    @AnyThread
    public LoadContext beginLoad(ScreenId screen, LoadContext.Trigger trigger, @Nullable NavigationEvent proof) {
        return collector.beginLoad(screen, trigger, proof);
    }

    @AnyThread
    public void withLoadSession(LoadContext load, Consumer<Boolean> action) {
        collector.withLoadSession(load, action);
    }

    @Nullable
    public NavigationEvent getConfirmedFeedNavigation(FeedFragment owner, ScreenId screen) {
        if (confirmationPending || !(getResumedCandidate() instanceof HomeActivity) || owner.getActivity() != resumedActivity
                || !owner.isResumed() || getVisibleScreen() != screen) return null;
        HomeActivity home = (HomeActivity) resumedActivity;
        return home.isCurrentFeedForTelemetry(owner) ? collector.getConfirmedNavigation(screen) : null;
    }

    @Nullable
    public ScreenId getVisibleScreen() {
        Activity activity = getResumedCandidate();
        return activity == null ? null : resolveScreen(activity);
    }

    public void recheckResumedScreen() {
        scheduleConfirmation();
    }

    @Nullable
    public NavigationEvent getContentNavigation(Activity owner, ScreenId screen, long requestedAtMs) {
        if (confirmationPending || getResumedCandidate() != owner || resolveScreen(owner) != screen) return null;
        NavigationEvent confirmed = collector.getConfirmedNavigation(screen);
        return confirmed != null && confirmed.timestampMs > requestedAtMs ? confirmed : null;
    }

    public void onActivityResumed(Activity activity) {
        resumedActivity = activity;
        scheduleConfirmation();
    }

    public void onActivityPaused(Activity activity) {
        if (resumedActivity == activity) {
            resumedActivity = null;
            mainHandler.removeCallbacks(confirmPendingScreen);
        }
    }

    public void onHomeScreenChanged(HomeActivity home) {
        if (resumedActivity == home) {
            scheduleConfirmation();
        }
    }

    public void onUserInteraction(Activity activity) {
        if (resumedActivity == activity && !activity.isFinishing()) {
            collector.recordLiveActivity();
        }
    }

    private void scheduleConfirmation() {
        confirmationPending = true;
        mainHandler.removeCallbacks(confirmPendingScreen);
        // Wait for the current resume or Fragment dispatch to finish before resolving the screen.
        mainHandler.post(confirmPendingScreen);
    }

    private void confirmResumedScreen() {
        confirmationPending = false;
        Activity activity = getResumedCandidate();
        if (activity == null) {
            return;
        }

        ScreenId screen = resolveScreen(activity);
        if (screen == null && activity instanceof HomeActivity) {
            return;
        }

        Intent intent = activity.getIntent();
        long requestId = intent == null ? 0 : intent.getLongExtra(Navigator.EXTRA_REPLAY_REQUEST_ID, 0);
        if (replayListener != null) {
            if (replayListener.onScreenObserved(requestId, screen) && requestId != 0) {
                intent.removeExtra(Navigator.EXTRA_REPLAY_REQUEST_ID);
            }
        } else if (requestId != 0) {
            // A pending replay Intent can arrive after the run has closed.
            intent.removeExtra(Navigator.EXTRA_REPLAY_REQUEST_ID);
            abandonedReplayIntent = intent;
            abandonedReplayScreen = screen;
        } else if (intent != null && intent == abandonedReplayIntent && screen == abandonedReplayScreen) {
            return;
        } else {
            abandonedReplayIntent = null;
            logLiveScreen(screen);
        }
    }

    private void logLiveScreen(@Nullable ScreenId screen) {
        if (screen == null) {
            collector.confirmOutOfScopeScreen();
        } else {
            collector.confirmScreen(screen).ifPresent(this::publishNavigation);
        }
    }

    @Nullable
    private Activity getResumedCandidate() {
        Activity activity = resumedActivity;
        return activity == null || activity.isFinishing() || activity.isDestroyed()
                || activity instanceof MediaProjectionActivity ? null : activity;
    }

    @Nullable
    private static ScreenId resolveScreen(Activity activity) {
        if (activity instanceof HomeActivity) return ((HomeActivity) activity).getVisibleScreenForTelemetry();
        if (activity instanceof PostDetailActivity) return ScreenId.POST_DETAIL;
        if (activity instanceof CommentsActivity) return ScreenId.COMMENTS;
        if (activity instanceof ProfileActivity) return ScreenId.PROFILE;
        if (activity instanceof SettingsActivity) return ScreenId.SETTINGS;
        return null;
    }

    private void publishNavigation(NavigationEvent event) {
        Log.i(TAG, "runId=" + event.runId
                + " sessionId=" + event.sessionId
                + " sequence=" + event.sequence
                + " navigationId=" + event.navigationId
                + " fromScreen=" + event.fromScreen
                + " toScreen=" + event.toScreen
                + " timestampMs=" + event.timestampMs
                + " source=" + event.source);
        navigationConsumer.accept(event);
    }
}
