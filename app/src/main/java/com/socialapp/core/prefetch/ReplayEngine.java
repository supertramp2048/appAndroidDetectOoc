package com.socialapp.core.prefetch;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.socialapp.MyApplication;
import com.socialapp.core.di.AppContainer;
import com.socialapp.core.navigation.Navigator;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Robot tự động điều hướng (Replay Engine) theo kịch bản hành vi người dùng.
 * Đọc file kịch bản, tuần tự kích hoạt Navigator chuyển qua các màn hình đúng thứ tự và thời gian dừng.
 */
public class ReplayEngine {

    private static final String TAG = "ReplayEngine";
    public static final String DEFAULT_ASSET_FILE = "mock_replay_100_sessions.json";

    public enum State {
        IDLE,
        RUNNING,
        PAUSED,
        FINISHED
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
    }

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<ReplaySession> sessions = new ArrayList<>();

    private State state = State.IDLE;
    private int currentSessionIndex = 0;
    private int currentStepIndex = 0;
    private Runnable nextStepRunnable;
    private OnReplayProgressListener listener;

    public ReplayEngine(@NonNull Context context) {
        this.context = context.getApplicationContext();
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

    public int getCurrentSessionNumber() {
        return currentSessionIndex + 1;
    }

    public int getTotalSessions() {
        return sessions.size();
    }

    public int getCurrentStepNumber() {
        return currentStepIndex + 1;
    }

    public synchronized void loadSessionsFromAsset(@NonNull String assetPath) {
        sessions.clear();
        try (InputStream is = context.getAssets().open(assetPath);
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {

            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray sessionArray = root.getAsJsonArray("sessions");

            if (sessionArray != null) {
                for (JsonElement sElem : sessionArray) {
                    JsonObject sObj = sElem.getAsJsonObject();
                    int sessionId = sObj.has("session_id") ? sObj.get("session_id").getAsInt() : (sessions.size() + 1);
                    ReplaySession session = new ReplaySession(sessionId);

                    JsonArray stepsArray = sObj.getAsJsonArray("steps");
                    if (stepsArray != null) {
                        for (JsonElement stepElem : stepsArray) {
                            JsonObject stepObj = stepElem.getAsJsonObject();
                            int stepNum = stepObj.has("step") ? stepObj.get("step").getAsInt() : (session.steps.size() + 1);
                            String screenStr = stepObj.get("screen").getAsString();
                            long dwell = stepObj.has("dwell_ms") ? stepObj.get("dwell_ms").getAsLong() : 2000;

                            ScreenId screenId = ScreenId.fromRoute(screenStr);
                            session.steps.add(new ReplayStep(stepNum, screenId, dwell));
                        }
                    }
                    sessions.add(session);
                }
            }
            Log.d(TAG, "Loaded " + sessions.size() + " replay sessions from asset: " + assetPath);
        } catch (Exception e) {
            Log.e(TAG, "Error loading replay sessions: ", e);
        }
    }

    public synchronized void startReplay(int maxSessionsToRun) {
        if (state == State.RUNNING) return;
        if (sessions.isEmpty()) {
            loadSessionsFromAsset(DEFAULT_ASSET_FILE);
            if (sessions.isEmpty()) return;
        }

        state = State.RUNNING;
        currentSessionIndex = 0;
        currentStepIndex = 0;

        AppContainer container = MyApplication.getInstance() != null ? MyApplication.getInstance().getAppContainer() : null;
        if (container != null) {
            container.getMetricsLogger().resetSessionStats();
            container.getPrefetchEngine().resetSession();
        }

        executeCurrentStep(maxSessionsToRun);
    }

    public synchronized void pauseReplay() {
        if (state == State.RUNNING) {
            state = State.PAUSED;
            if (nextStepRunnable != null) {
                mainHandler.removeCallbacks(nextStepRunnable);
            }
        }
    }

    public synchronized void resumeReplay(int maxSessionsToRun) {
        if (state == State.PAUSED) {
            state = State.RUNNING;
            executeCurrentStep(maxSessionsToRun);
        }
    }

    public synchronized void stopReplay() {
        state = State.IDLE;
        if (nextStepRunnable != null) {
            mainHandler.removeCallbacks(nextStepRunnable);
            nextStepRunnable = null;
        }
        if (listener != null) {
            listener.onReplayStopped();
        }
        // Quay về trang chủ
        Navigator.navigateTo(context, Navigator.ROUTE_HOME);
    }

    private synchronized void executeCurrentStep(int maxSessionsToRun) {
        if (state != State.RUNNING) return;

        int limit = maxSessionsToRun > 0 ? Math.min(maxSessionsToRun, sessions.size()) : sessions.size();
        if (currentSessionIndex >= limit) {
            state = State.FINISHED;
            if (listener != null) {
                listener.onReplayFinished();
            }
            Navigator.navigateTo(context, Navigator.ROUTE_HOME);
            return;
        }

        ReplaySession session = sessions.get(currentSessionIndex);
        if (currentStepIndex >= session.steps.size()) {
            // Hoàn thành 1 phiên
            handleSessionCompletion(session, maxSessionsToRun);
            return;
        }

        ReplayStep currentStep = session.steps.get(currentStepIndex);
        Repository.setSessionId(session.sessionId);

        // Báo tiến trình cho UI
        if (listener != null) {
            listener.onProgress(currentSessionIndex + 1, limit, currentStepIndex + 1, session.steps.size(), currentStep.screen);
        }

        // Tự động điều hướng đến màn hình
        Navigator.navigateToScreen(context, currentStep.screen);

        AppContainer container = MyApplication.getInstance() != null ? MyApplication.getInstance().getAppContainer() : null;
        float timeScale = container != null ? container.getPrefetchEngine().getTimeScale() : 1.0f;
        long effectiveDwellMs = Math.max(80, (long) (currentStep.dwellMs / timeScale));

        nextStepRunnable = () -> {
            currentStepIndex++;
            executeCurrentStep(maxSessionsToRun);
        };

        mainHandler.postDelayed(nextStepRunnable, effectiveDwellMs);
    }

    private void handleSessionCompletion(ReplaySession session, int maxSessionsToRun) {
        AppContainer container = MyApplication.getInstance() != null ? MyApplication.getInstance().getAppContainer() : null;
        if (container != null) {
            // Chốt wasted bytes cuối phiên
            container.getPrefetchEngine().getCache().flushUnusedWastedBytes();
        }

        if (listener != null) {
            listener.onSessionCompleted(session.sessionId, maxSessionsToRun > 0 ? maxSessionsToRun : sessions.size());
        }

        // Định kỳ sau mỗi 20 phiên thì dọn dẹp RAM của Glide để chống tràn bộ nhớ
        if ((currentSessionIndex + 1) % 20 == 0) {
            try {
                Glide.get(context).clearMemory();
            } catch (Exception ignored) {
            }
        }

        currentSessionIndex++;
        currentStepIndex = 0;

        // Nghỉ một khoảng ngắn giữa các phiên rồi tiếp tục
        nextStepRunnable = () -> executeCurrentStep(maxSessionsToRun);
        mainHandler.postDelayed(nextStepRunnable, 300);
    }
}

