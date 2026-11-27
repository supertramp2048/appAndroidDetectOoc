package com.socialapp.core.prefetch;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.MainThread;
import androidx.core.os.HandlerCompat;

import com.bumptech.glide.Glide;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.socialapp.MyApplication;
import com.socialapp.core.di.AppContainer;
import com.socialapp.core.navigation.Navigator;
import com.socialapp.core.telemetry.AndroidNavigationObserver;
import com.socialapp.core.telemetry.NavigationEvent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Robot tự động điều hướng (Replay Engine) theo kịch bản hành vi người dùng.
 * Đọc file kịch bản, tuần tự kích hoạt Navigator chuyển qua các màn hình đúng thứ tự và thời gian dừng.
 */
@MainThread
public class ReplayEngine {

    private static final String TAG = "ReplayEngine";
    private static final String CONFIRMATION_TAG = "ReplayConfirmation";
    private static final long SESSION_GAP_MS = 300;
    public static final String DEFAULT_ASSET_FILE = "mock_replay_100_sessions.json";
    enum T4Preset {
        CALIBRATION("t4_calibration", 50, 12, 13, 17, 18, 23, 31, 41, 46, 52, 53),
        EVALUATION("t4_evaluation", 109, 8, 11, 21, 22, 24, 25, 29, 35, 39, 44, 49, 55, 56, 62, 63, 65, 68, 70, 75, 80, 83);

        final String metricsPrefix;
        final int expectedSteps;
        final int[] sessionIds;

        T4Preset(String metricsPrefix, int expectedSteps, int... sessionIds) {
            this.metricsPrefix = metricsPrefix;
            this.expectedSteps = expectedSteps;
            this.sessionIds = sessionIds;
        }
    }

    public enum State {
        IDLE,
        RUNNING,
        PAUSED,
        FINISHED,
        INVALID
    }

    public static class ReplayStep {
        public final int step;
        public final ScreenId screen;
        public final long dwellMs;

        public ReplayStep(int step, ScreenId screen, long dwellMs) {
            this.step = step;
            this.screen = screen;
            this.dwellMs = dwellMs;
        }
    }

    public static class ReplaySession {
        public final int sessionId;
        public final List<ReplayStep> steps = new ArrayList<>();

        public ReplaySession(int sessionId) {
            this.sessionId = sessionId;
        }
    }

    public interface OnReplayProgressListener {
        void onProgress(int currentSession, int totalSessions, int currentStep, int totalSteps, ScreenId screen);
        void onSessionCompleted(int sessionId, int totalSessions);
        void onReplayFinished();
        void onReplayStopped();
        default void onReplayRunStarted(UUID runId) {}
        default void onReplayFailed(UUID runId, String reason) {}
    }

    private final Context context;
    private final AndroidNavigationObserver navigationObserver;
    // Replay deadlines share the main thread and can pass a UI traversal barrier.
    private final Handler mainHandler = HandlerCompat.createAsync(Looper.getMainLooper());
    private volatile List<ReplaySession> sessions = Collections.emptyList();

    private volatile State state = State.IDLE;
    private int currentSessionIndex = 0;
    private int currentStepIndex = 0;
    private Runnable nextStepRunnable;
    private OnReplayProgressListener listener;
    private OnReplayProgressListener runListener;
    private UUID runId;
    private boolean sessionOpen;
    private int sessionCount;
    private int confirmedStepCount;
    private int navigationEventCount;
    private int transitionCount;
    private ReplayResult.Configuration replayConfiguration;
    private volatile ReplayResult latestResult;
    private final ReplayDemoResults demoResults = new ReplayDemoResults();
    private final SharedPreferences demoPreferences;
    private boolean demoCalibrationRun;
    private boolean exportPending;
    private long nextRequestId;
    private long timerGeneration;
    private long confirmationTimeoutMs = 5_000;
    private long sessionGapDeadlineMs;
    private long pausedSessionGapMs;
    private ReplayStepTiming activeStep;
    private boolean observedForRequest;
    private ScreenId lastObservedScreen;

    public ReplayEngine(@NonNull Context context, @NonNull AndroidNavigationObserver navigationObserver) {
        this.context = context.getApplicationContext();
        this.navigationObserver = navigationObserver;
        demoPreferences = context.getSharedPreferences("t4_calibration_demo_v1", Context.MODE_PRIVATE);
        demoResults.restore(demoPreferences.getString("results", null), T4Preset.CALIBRATION.expectedSteps);
        loadSessionsFromAsset(DEFAULT_ASSET_FILE);
    }

    public void setListener(@Nullable OnReplayProgressListener listener) {
        this.listener = listener;
    }

    public State getState() {
        return state;
    }

    public boolean isRunning() {
        return state == State.RUNNING;
    }

    public boolean isActive() {
        return state == State.RUNNING || state == State.PAUSED;
    }

    public void setConfirmationTimeoutMs(long timeoutMs) {
        if (timeoutMs <= 0 || isActive()) {
            throw new IllegalArgumentException("Set a positive replay timeout before starting a run");
        }
        confirmationTimeoutMs = timeoutMs;
    }

    public int getConfirmedStepCount() {
        return confirmedStepCount;
    }

    public int getNavigationEventCount() {
        return navigationEventCount;
    }

    @Nullable
    public ReplayResult getLatestResult() {
        return latestResult;
    }

    public List<ReplayResult> getDemoResults() {
        return demoResults.snapshot();
    }

    public File getCalibrationMetricsFile(UUID id) {
        return MyApplication.getAppContainer(context).getMetricsLogger().outputFile(
                MetricsLogger.metricsFileName(metricsConfigName(T4Preset.CALIBRATION.metricsPrefix, id))).get();
    }

    public int getCurrentSessionNumber() {
        return currentSessionIndex + 1;
    }

    public int getTotalSessions() {
        return sessions.size();
    }

    public int getCurrentStepNumber() {
        return currentStepIndex + 1;
    }

    public void loadSessionsFromAsset(@NonNull String assetPath) {
        loadSessionsFromAsset(assetPath, null);
    }

    public boolean startT4CalibrationReplay() {
        return startT4Preset(T4Preset.CALIBRATION);
    }

    public boolean startT4EvaluationReplay() {
        PrefetchEngine engine = MyApplication.getAppContainer(context).getPrefetchEngine();
        PrefetchEngine.Policy policy = engine.getCurrentPolicy();
        float speed = engine.getTimeScale();
        if (!engine.isSimulationMode() || engine.getCurrentNetwork() != PrefetchEngine.NetworkProfile.LTE
                || engine.getPayloadScale() != 1.0f || (speed != 2.0f && speed != 5.0f)
                || (policy != PrefetchEngine.Policy.P0_NONE && policy != PrefetchEngine.Policy.P5_ADAPTIVE_NETWORK)
                || (policy == PrefetchEngine.Policy.P5_ADAPTIVE_NETWORK && !engine.usesDefaultLambdaConfig())) {
            Toast.makeText(context, "Evaluation yêu cầu P0 hoặc P5 chuẩn, mô phỏng LTE, payload 1.0x và tốc độ 2.0x hoặc 5.0x.",
                    Toast.LENGTH_LONG).show();
            return false;
        }
        return startT4Preset(T4Preset.EVALUATION);
    }

    private boolean startT4Preset(T4Preset preset) {
        if (isActive()) return false;
        loadSessionsFromAsset(DEFAULT_ASSET_FILE, preset);
        return startReplay(0, preset.metricsPrefix, null, preset == T4Preset.CALIBRATION);
    }

    private synchronized void loadSessionsFromAsset(String assetPath, @Nullable T4Preset preset) {
        if (state == State.RUNNING || state == State.PAUSED) {
            Log.w(TAG, "Cannot replace the replay trace while replay is active: " + assetPath);
            return;
        }

        List<ReplaySession> loadedSessions;
        try (InputStream is = context.getAssets().open(assetPath);
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            loadedSessions = preset == null ? parseSessions(reader) : parseT4PresetSessions(reader, preset);
        } catch (IOException | RuntimeException e) {
            sessions = Collections.emptyList();
            Log.e(TAG, "Error loading replay trace: " + assetPath, e);
            return;
        }

        replaceSessions(loadedSessions);
        Log.d(TAG, "Loaded " + loadedSessions.size() + " replay sessions from asset: " + assetPath);
    }

    static List<ReplaySession> parseT4PresetSessions(Reader reader, T4Preset preset) {
        Map<Integer, ReplaySession> available = new HashMap<>();
        for (ReplaySession session : parseSessions(reader)) {
            if (available.putIfAbsent(session.sessionId, session) != null) {
                throw new IllegalArgumentException("Duplicate replay session " + session.sessionId);
            }
        }
        List<ReplaySession> selected = new ArrayList<>(preset.sessionIds.length);
        int steps = 0;
        for (int id : preset.sessionIds) {
            ReplaySession session = available.get(id);
            if (session == null || session.steps.isEmpty()) {
                throw new IllegalArgumentException("Missing or empty " + preset.metricsPrefix + " session " + id);
            }
            selected.add(session);
            steps += session.steps.size();
        }
        if (steps != preset.expectedSteps) throw new IllegalArgumentException("Unexpected step count in " + preset.metricsPrefix);
        return selected;
    }

    void replaceSessions(List<ReplaySession> loadedSessions) {
        if (isActive()) {
            throw new IllegalStateException("Cannot replace the trace during replay");
        }
        sessions = loadedSessions;
    }

    static List<ReplaySession> parseSessions(@NonNull Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        JsonArray sessionArray = root.getAsJsonArray("sessions");
        if (sessionArray == null) {
            throw new IllegalArgumentException("Missing replay sessions array");
        }

        List<ReplaySession> parsedSessions = new ArrayList<>();
        for (JsonElement sessionElement : sessionArray) {
            JsonObject sessionObject = sessionElement.getAsJsonObject();
            int sessionId = sessionObject.has("session_id")
                    ? sessionObject.get("session_id").getAsInt() : parsedSessions.size() + 1;
            ReplaySession session = new ReplaySession(sessionId);
            JsonArray steps = sessionObject.getAsJsonArray("steps");
            if (steps == null) {
                throw new IllegalArgumentException("Missing steps array for replay session " + sessionId);
            }

            for (JsonElement stepElement : steps) {
                JsonObject stepObject = stepElement.getAsJsonObject();
                int step = stepObject.has("step") ? stepObject.get("step").getAsInt() : session.steps.size() + 1;
                JsonElement screenValue = stepObject.get("screen");
                String location = " in replay session " + sessionId + ", step " + step;
                if (screenValue == null || !screenValue.isJsonPrimitive() || !screenValue.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("Expected a screen ID string" + location + ", got " + screenValue);
                }

                ScreenId screen;
                try {
                    screen = ScreenId.fromReplayId(screenValue.getAsString());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(e.getMessage() + location, e);
                }
                long dwellMs = stepObject.has("dwell_ms") ? stepObject.get("dwell_ms").getAsLong() : 2000;
                session.steps.add(new ReplayStep(step, screen, dwellMs));
            }
            parsedSessions.add(session);
        }
        return parsedSessions;
    }

    @Nullable
    synchronized String getStartBlockReason(int maxSessionsToRun) {
        if (exportPending) return "Đang lưu kết quả Replay.";
        if (state == State.RUNNING || state == State.PAUSED) {
            return "Replay đang hoạt động.";
        }
        if (sessions.isEmpty()) {
            return "Không có dữ liệu replay hợp lệ.";
        }
        if (requiresLogin(sessions, maxSessionsToRun)
                && !MyApplication.getAppContainer(context).getSessionManager().isLoggedIn()) {
            return "Cần đăng nhập trước khi chạy replay có tab Đang theo dõi.";
        }
        return null;
    }

    static boolean requiresLogin(List<ReplaySession> sessions, int maxSessionsToRun) {
        int limit = sessionLimit(maxSessionsToRun, sessions.size());
        for (int i = 0; i < limit; i++) {
            for (ReplayStep step : sessions.get(i).steps) {
                if (step.screen == ScreenId.FEED_FOLLOWING) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int sessionLimit(int maxSessionsToRun, int sessionCount) {
        return maxSessionsToRun > 0 ? Math.min(maxSessionsToRun, sessionCount) : sessionCount;
    }

    public boolean startReplay(int maxSessionsToRun) {
        return startReplay(maxSessionsToRun, null);
    }

    boolean startReplay(int maxSessionsToRun, @Nullable OnReplayProgressListener runListener) {
        return startReplay(maxSessionsToRun, "replay", runListener);
    }

    boolean startReplay(int maxSessionsToRun, String metricsPrefix, @Nullable OnReplayProgressListener runListener) {
        return startReplay(maxSessionsToRun, metricsPrefix, runListener, false);
    }

    private boolean startReplay(int maxSessionsToRun, String metricsPrefix,
                                @Nullable OnReplayProgressListener runListener, boolean calibration) {
        String blockReason = getStartBlockReason(maxSessionsToRun);
        if (blockReason != null) {
            Log.w(TAG, blockReason);
            Toast.makeText(context, blockReason, Toast.LENGTH_LONG).show();
            return false;
        }

        this.runListener = runListener;
        demoCalibrationRun = calibration;
        runId = navigationObserver.beginReplayRun(this::onScreenObserved);
        state = State.RUNNING;
        currentSessionIndex = 0;
        currentStepIndex = 0;
        sessionCount = sessionLimit(maxSessionsToRun, sessions.size());
        confirmedStepCount = 0;
        navigationEventCount = 0;
        transitionCount = 0;
        replayConfiguration = null;
        UUID startedRun = runId;
        Log.i(CONFIRMATION_TAG, "runId=" + startedRun + " status=RUN_STARTED sessions=" + sessionCount);
        try {
            AppContainer container = MyApplication.getAppContainer(context);
            replayConfiguration = new ReplayResult.Configuration(container.getPrefetchEngine());
            if (!container.getMetricsLogger().setConfigName(metricsConfigName(metricsPrefix, startedRun),
                    startedRun, replayConfiguration.timeScale)) {
                navigationObserver.endReplayRun();
                runId = null;
                this.runListener = null;
                state = State.IDLE;
                Toast.makeText(context, "Telemetry writer busy. Please retry Replay.", Toast.LENGTH_LONG).show();
                return false;
            }
            container.getProfileManager().beginReplay();
            container.getPrefetchEngine().beginP5Replay(startedRun, container.getMetricsLogger().captureP5Sink(startedRun));
            notifyListeners(this.runListener, callback -> callback.onReplayRunStarted(startedRun));
            executeCurrentStep();
        } catch (RuntimeException error) {
            failReplay("ERROR", navigationObserver.getVisibleScreen(), error);
        }
        return true;
    }

    static String metricsConfigName(String prefix, UUID runId) {
        return prefix + "_attempt_" + runId;
    }

    public void pauseReplay() {
        if (state != State.RUNNING) {
            return;
        }
        state = State.PAUSED;
        cancelTimer();
        if (activeStep == null) {
            pausedSessionGapMs = Math.max(0, sessionGapDeadlineMs - SystemClock.elapsedRealtime());
        } else {
            activeStep.pause();
        }
    }

    public void resumeReplay() {
        if (state != State.PAUSED) {
            return;
        }
        state = State.RUNNING;
        if (activeStep == null) {
            scheduleNextSession(pausedSessionGapMs);
        } else {
            activeStep.resume();
            navigationObserver.recheckResumedScreen();
            scheduleStepTimer(activeStep);
        }
    }

    public void resumeReplay(int maxSessionsToRun) {
        resumeReplay();
    }

    public void stopReplay() {
        if (isActive()) {
            finishRun(State.IDLE, "STOPPED");
        }
    }

    private void executeCurrentStep() {
        if (state != State.RUNNING) return;

        if (currentSessionIndex >= sessionCount) {
            finishRun(State.FINISHED, "RUN_FINISHED");
            return;
        }

        ReplaySession session = sessions.get(currentSessionIndex);
        if (!sessionOpen) {
            navigationObserver.beginReplaySession(session.sessionId);
            sessionOpen = true;
            Repository.setReplaySessionId(session.sessionId);
        }
        if (currentStepIndex >= session.steps.size()) {
            handleSessionCompletion(session);
            return;
        }

        ReplayStep step = session.steps.get(currentStepIndex);
        float timeScale = MyApplication.getAppContainer(context).getPrefetchEngine().getTimeScale();
        long dwellMs = Math.max(80, (long) (step.dwellMs / timeScale));
        activeStep = new ReplayStepTiming(SystemClock::elapsedRealtime, ++nextRequestId,
                step.screen, confirmationTimeoutMs, dwellMs);
        observedForRequest = false;
        lastObservedScreen = null;
        if (navigationObserver.getVisibleScreen() == step.screen) {
            onScreenObserved(activeStep.requestId, step.screen);
            return;
        }

        Navigator.navigateToReplayScreen(context, step.screen, activeStep.requestId);
        scheduleStepTimer(activeStep);
    }

    private boolean onScreenObserved(long requestId, @Nullable ScreenId screen) {
        ReplayStepTiming step = activeStep;
        if (step == null || state != State.RUNNING) {
            return false;
        }
        if (!step.isWaiting()) {
            if ((requestId == 0 || requestId == step.requestId) && screen != step.expected) {
                failReplay("UNEXPECTED_DURING_DWELL", screen, null);
            }
            return false;
        }
        if (requestId != step.requestId) {
            return false;
        }
        observedForRequest = true;
        lastObservedScreen = screen;
        if (!step.confirm(requestId, screen)) {
            return false;
        }

        try {
            NavigationEvent event = navigationObserver.recordReplayConfirmation(step.expected).orElse(null);
            confirmedStepCount++;
            if (event != null) {
                navigationEventCount++;
                if (event.fromScreen != null) transitionCount++;
            }
            logStep("CONFIRMED", screen, event);
            scheduleStepTimer(step);
            int sessionNumber = currentSessionIndex + 1;
            int stepNumber = currentStepIndex + 1;
            int stepCount = sessions.get(currentSessionIndex).steps.size();
            notifyListeners(runListener, callback -> callback.onProgress(
                    sessionNumber, sessionCount, stepNumber, stepCount, step.expected));
        } catch (RuntimeException error) {
            failReplay("ERROR", screen, error);
        }
        return true;
    }

    private void scheduleStepTimer(ReplayStepTiming step) {
        scheduleTimer(step.remainingMs(), () -> {
            if (activeStep != step) return;
            if (step.remainingMs() > 0) {
                scheduleStepTimer(step);
                return;
            }
            if (step.isWaiting()) {
                ScreenId visible = navigationObserver.getVisibleScreen();
                String reason = observedForRequest && visible != step.expected ? "WRONG_DESTINATION" : "TIMEOUT";
                failReplay(reason, visible != null ? visible : lastObservedScreen, null);
            } else {
                ScreenId visible = navigationObserver.getVisibleScreen();
                if (!step.canCompleteDwell(visible)) {
                    failReplay("UNEXPECTED_DURING_DWELL", visible, null);
                    return;
                }
                activeStep = null;
                currentStepIndex++;
                executeCurrentStep();
            }
        });
    }

    private void handleSessionCompletion(ReplaySession session) {
        navigationObserver.endReplaySession();
        sessionOpen = false;
        notifyListeners(runListener, callback -> callback.onSessionCompleted(session.sessionId, sessionCount));
        if (!isActive()) return;

        if ((currentSessionIndex + 1) % 20 == 0) {
            Glide.get(context).clearMemory();
        }
        currentSessionIndex++;
        currentStepIndex = 0;
        scheduleNextSession(SESSION_GAP_MS);
    }

    private void scheduleNextSession(long delayMs) {
        sessionGapDeadlineMs = SystemClock.elapsedRealtime() + delayMs;
        pausedSessionGapMs = delayMs;
        if (state == State.RUNNING) {
            scheduleTimer(delayMs, this::executeCurrentStep);
        }
    }

    private void scheduleTimer(long delayMs, Runnable action) {
        cancelTimer();
        long generation = timerGeneration;
        nextStepRunnable = () -> {
            if (state != State.RUNNING || timerGeneration != generation) return;
            nextStepRunnable = null;
            try {
                action.run();
            } catch (RuntimeException error) {
                failReplay("ERROR", navigationObserver.getVisibleScreen(), error);
            }
        };
        mainHandler.postDelayed(nextStepRunnable, delayMs);
    }

    private void cancelTimer() {
        timerGeneration++;
        if (nextStepRunnable != null) {
            mainHandler.removeCallbacks(nextStepRunnable);
            nextStepRunnable = null;
        }
    }

    private void failReplay(String reason, @Nullable ScreenId observed, @Nullable RuntimeException error) {
        if (runId == null) return;
        if (error != null) Log.e(TAG, "Replay failed", error);
        logStep(reason, observed, null);
        finishRun(State.INVALID, reason);
        Toast.makeText(context, "Replay không hợp lệ (" + reason + ")", Toast.LENGTH_LONG).show();
    }

    private void finishRun(State terminal, String status) {
        UUID finishedRun = runId;
        boolean calibration = demoCalibrationRun;
        OnReplayProgressListener finishedListener = runListener;
        cancelTimer();
        activeStep = null;
        if (sessionOpen) {
            navigationObserver.endReplaySession();
            sessionOpen = false;
        }
        navigationObserver.endReplayRun();
        AppContainer container = MyApplication.getAppContainer(context);
        PrefetchEngine.LambdaSnapshot lambda = container.getPrefetchEngine().finishP5Replay(finishedRun);
        MetricsLogger metricsLogger = container.getMetricsLogger();
        exportPending = true;
        ReplayResult.Metrics replayMetrics = metricsLogger.finishReplayRun(finishedRun, status,
                terminal == State.FINISHED, confirmedStepCount, navigationEventCount, export -> {
                    exportPending = false;
                    ReplayResult result = latestResult;
                    if (result != null && result.runId.equals(finishedRun)) {
                        result.setExportStatus(export);
                        if (calibration && demoResults.record(result, T4Preset.CALIBRATION.expectedSteps)) {
                            demoPreferences.edit().putString("results", demoResults.serialize()).apply();
                        }
                    }
                    Log.i(CONFIRMATION_TAG, "runId=" + finishedRun + " csvComplete=" + export.isComplete()
                            + " droppedRows=" + export.droppedRows + " failedRows=" + export.failedRows);
                    if (!export.isComplete() && terminal == State.FINISHED) {
                        notifyTerminal(finishedListener, State.INVALID, finishedRun, "TELEMETRY_INCOMPLETE");
                    } else {
                        notifyTerminal(finishedListener, terminal, finishedRun, status);
                    }
                });
        ReplayResult.Outcome outcome = terminal == State.FINISHED ? ReplayResult.Outcome.COMPLETED
                : terminal == State.INVALID ? ReplayResult.Outcome.INVALID : ReplayResult.Outcome.STOPPED;
        latestResult = new ReplayResult(finishedRun, outcome, status, replayConfiguration, confirmedStepCount,
                transitionCount, replayMetrics, lambda);
        container.getProfileManager().endReplay();
        runListener = null;
        runId = null;
        state = terminal;
        metricsLogger.setConfigName("default");
        container.getPerformanceTelemetry().checkpoint();

        Log.i(CONFIRMATION_TAG, "runId=" + finishedRun + " status=" + status + " navigationValid="
                + (terminal == State.FINISHED) + " confirmedSteps=" + confirmedStepCount
                + " navigationEvents=" + navigationEventCount);
        if (replayMetrics == null) {
            exportPending = false;
            notifyTerminal(finishedListener, terminal == State.FINISHED ? State.INVALID : terminal,
                    finishedRun, terminal == State.FINISHED ? "TELEMETRY_UNAVAILABLE" : status);
        }
    }

    private void notifyTerminal(OnReplayProgressListener listener, State terminal, UUID finishedRun, String status) {
        if (terminal == State.FINISHED) {
            notifyListeners(listener, OnReplayProgressListener::onReplayFinished);
        } else if (terminal == State.INVALID) {
            notifyListeners(listener, callback -> callback.onReplayFailed(finishedRun, status));
        } else {
            notifyListeners(listener, OnReplayProgressListener::onReplayStopped);
        }
    }

    private void logStep(String status, @Nullable ScreenId observed, @Nullable NavigationEvent event) {
        if (activeStep == null) return;
        ReplaySession session = sessions.get(currentSessionIndex);
        ReplayStep step = session.steps.get(currentStepIndex);
        Log.i(CONFIRMATION_TAG, "runId=" + runId + " replaySessionId=" + session.sessionId
                + " sessionOrdinal=" + (currentSessionIndex + 1) + " traceStep=" + step.step
                + " expected=" + step.screen + " observed=" + observed
                + " confirmationLatencyMs=" + activeStep.getConfirmationLatencyMs()
                + " dwellMs=" + activeStep.dwellMs + " status=" + status
                + " navigationId=" + (event == null ? null : event.navigationId));
    }

    private void notifyListeners(@Nullable OnReplayProgressListener runCallback, Consumer<OnReplayProgressListener> action) {
        OnReplayProgressListener displayCallback = listener;
        notifyListener(displayCallback, action);
        if (runCallback != displayCallback) notifyListener(runCallback, action);
    }

    private void notifyListener(@Nullable OnReplayProgressListener callback, Consumer<OnReplayProgressListener> action) {
        if (callback != null) {
            try {
                action.accept(callback);
            } catch (RuntimeException error) {
                Log.e(TAG, "Replay listener failed", error);
            }
        }
    }
}

