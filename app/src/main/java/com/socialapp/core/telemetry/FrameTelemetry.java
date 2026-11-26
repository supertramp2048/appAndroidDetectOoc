package com.socialapp.core.telemetry;

import android.app.Activity;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.FrameMetrics;
import android.view.Window;

import com.socialapp.feature.comments.CommentsActivity;
import com.socialapp.feature.home.HomeActivity;
import com.socialapp.feature.post.PostDetailActivity;
import com.socialapp.feature.profile.ProfileActivity;
import com.socialapp.feature.settings.SettingsActivity;

/** Aggregates platform frame reports on a worker, per resumed window. */
public final class FrameTelemetry {
    private static final long INTERVAL_MS = 5_000;
    private final PerformanceTelemetry sink;
    private Handler handler;
    private Activity owner;
    private Window.OnFrameMetricsAvailableListener listener;
    private WindowSample sample;

    public FrameTelemetry(PerformanceTelemetry sink) { this.sink = sink; }

    public void onResume(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed() || activity == owner) return;
        if (owner != null) onPause(owner);
        if (handler == null) {
            HandlerThread worker = new HandlerThread("frame-telemetry");
            worker.start();
            handler = new Handler(worker.getLooper());
        }
        WindowSample attached = new WindowSample(screen(activity), SystemClock.elapsedRealtime());
        listener = (window, metrics, drops) -> attached.record(metrics, drops);
        owner = activity;
        sample = attached;
        activity.getWindow().addOnFrameMetricsAvailableListener(listener, handler);
    }

    public void onPause(Activity activity) {
        if (owner != activity) return;
        activity.getWindow().removeOnFrameMetricsAvailableListener(listener);
        WindowSample detached = sample;
        owner = null;
        listener = null;
        sample = null;
        handler.post(() -> detached.close(SystemClock.elapsedRealtime()));
    }

    public void checkpoint() {
        if (handler == null) sink.checkpoint();
        else handler.post(sink::checkpoint);
    }

    private static PerformanceTelemetry.Screen screen(Activity activity) {
        if (activity instanceof HomeActivity) return PerformanceTelemetry.Screen.HOME;
        if (activity instanceof PostDetailActivity) return PerformanceTelemetry.Screen.POST_DETAIL;
        if (activity instanceof CommentsActivity) return PerformanceTelemetry.Screen.COMMENTS;
        if (activity instanceof ProfileActivity) return PerformanceTelemetry.Screen.PROFILE;
        if (activity instanceof SettingsActivity) return PerformanceTelemetry.Screen.SETTINGS;
        return PerformanceTelemetry.Screen.OTHER;
    }

    private final class WindowSample {
        private final PerformanceTelemetry.Screen screen;
        private long startedAtMs;
        private FrameSummary frames = new FrameSummary();
        private boolean closed;

        WindowSample(PerformanceTelemetry.Screen screen, long startedAtMs) {
            this.screen = screen;
            this.startedAtMs = startedAtMs;
        }

        void record(FrameMetrics metrics, int drops) {
            if (closed) return;
            long deadline = Build.VERSION.SDK_INT >= 31 ? metrics.getMetric(FrameMetrics.DEADLINE) : -1;
            frames.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION), deadline, drops);
            long now = SystemClock.elapsedRealtime();
            if (now - startedAtMs >= INTERVAL_MS) flush(now);
        }

        void close(long now) {
            closed = true;
            flush(now);
        }

        private void flush(long now) {
            if (frames.observed > 0) sink.frames(screen, startedAtMs, now, frames);
            frames = new FrameSummary();
            startedAtMs = now;
        }
    }
}
