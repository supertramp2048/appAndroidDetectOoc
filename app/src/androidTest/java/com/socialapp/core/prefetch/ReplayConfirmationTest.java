package com.socialapp.core.prefetch;

import android.app.Instrumentation;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.socialapp.MyApplication;
import com.socialapp.core.di.AppContainer;
import com.socialapp.core.navigation.Navigator;
import com.socialapp.core.network.dto.UserResource;
import com.socialapp.core.prefetch.ui.GlobalDemoOverlayManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class ReplayConfirmationTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();
    private final AppContainer container = MyApplication.getAppContainer(context);
    private final ReplayEngine replay = container.getReplayEngine();
    private boolean overlayEnabled;
    private float timeScale;
    private PrefetchEngine.Policy previousPolicy;
    private String previousToken;
    private UserResource previousUser;

    @Before
    public void setUp() {
        onMain(() -> {
            overlayEnabled = GlobalDemoOverlayManager.isEnabled();
            GlobalDemoOverlayManager.setEnabled(false);
            timeScale = container.getPrefetchEngine().getTimeScale();
            previousPolicy = container.getPrefetchEngine().getCurrentPolicy();
            previousToken = container.getSessionManager().getAuthToken();
            previousUser = container.getSessionManager().getCurrentUser();
            container.getSessionManager().logout();
        });
    }

    @After
    public void tearDown() {
        onMain(() -> {
            container.getBatchRunner().stopBatch();
            container.getBatchRunner().setListener(null);
            replay.stopReplay();
            replay.loadSessionsFromAsset(ReplayEngine.DEFAULT_ASSET_FILE);
            replay.setConfirmationTimeoutMs(5_000);
            container.getPrefetchEngine().setTimeScale(timeScale);
            container.getPrefetchEngine().setCurrentPolicy(previousPolicy);
            if (previousToken != null && previousUser != null) {
                container.getSessionManager().saveSession(previousToken, previousUser);
            } else {
                container.getSessionManager().logout();
            }
            GlobalDemoOverlayManager.setEnabled(overlayEnabled);
        });
    }

    @Test
    public void coldHotRepeatedStepAndNextSessionConfirmWithSeparateEventCounts() throws Exception {
        String hot = step(ScreenId.FEED_HOT, 80);
        Result first = new Result();
        onMain(() -> {
            installTrace(session(11, hot + "," + hot) + "," + session(12, hot));
            assertTrue(replay.startReplay(0, first));
        });
        assertCompleted(first, 3, 2);
        assertEquals(ScreenId.FEED_HOT, visibleScreen());
        assertTrue(logContains("replaySessionId=11 sessionOrdinal=1 traceStep=2 expected=FEED_HOT observed=FEED_HOT"));

        onMain(() -> Navigator.navigateToScreen(context, ScreenId.FEED_NEWEST));
        await(() -> visibleScreen() == ScreenId.FEED_NEWEST);
        instrumentation.waitForIdleSync();
        assertTrue(logContainsLiveBaseline(ScreenId.FEED_NEWEST));

        Result second = new Result();
        onMain(() -> assertTrue(replay.startReplay(0, second)));
        assertCompleted(second, 3, 2);
        assertNotEquals(first.runId, second.runId);
        assertEquals(ScreenId.FEED_HOT, visibleScreen());
    }

    @Test
    public void pauseWhileWaitingAndDwellingKeepsTheSameStep() throws Exception {
        Result progress = new Result() {
            @Override
            public void onProgress(int session, int sessions, int step, int steps, ScreenId screen) {
                super.onProgress(session, sessions, step, steps, screen);
                replay.pauseReplay();
            }
        };
        onMain(() -> {
            installTrace(session(1, step(ScreenId.FEED_HOT, 750)));
            replay.setConfirmationTimeoutMs(1_000);
            assertTrue(replay.startReplay(1, progress));
            replay.pauseReplay();
        });
        await(() -> visibleScreen() == ScreenId.FEED_HOT);
        SystemClock.sleep(1_100);
        assertEquals(ReplayEngine.State.PAUSED, replay.getState());
        assertEquals(0, replay.getConfirmedStepCount());
        onMain(replay::resumeReplay);
        await(() -> replay.getState() == ReplayEngine.State.PAUSED);
        SystemClock.sleep(850);
        assertEquals(1, replay.getConfirmedStepCount());
        assertEquals(ReplayEngine.State.PAUSED, replay.getState());
        onMain(replay::resumeReplay);
        assertCompleted(progress, 1, 1);
    }

    @Test
    public void stopWhileWaitingAndDuringDwellCleansUpWithoutRerouting() throws Exception {
        Result waiting = new Result();
        onMain(() -> {
            installTrace(session(1, step(ScreenId.FEED_HOT, 2_000)));
            assertTrue(replay.startReplay(1, waiting));
            replay.stopReplay();
        });
        assertEquals("STOPPED", waiting.terminal);
        assertEquals(0, replay.getConfirmedStepCount());
        await(() -> visibleScreen() == ScreenId.FEED_HOT);
        instrumentation.waitForIdleSync();
        assertFalse(logContainsLiveBaseline(ScreenId.FEED_HOT));

        Result dwell = new Result();
        onMain(() -> {
            assertTrue(replay.startReplay(1, dwell));
            assertEquals(1, replay.getConfirmedStepCount());
            replay.stopReplay();
        });
        SystemClock.sleep(2_100);
        assertEquals("STOPPED", dwell.terminal);
        assertEquals(ReplayEngine.State.IDLE, replay.getState());
        assertEquals(1, replay.getConfirmedStepCount());
        assertEquals(ScreenId.FEED_HOT, visibleScreen());
        assertNotEquals(waiting.runId, dwell.runId);
    }

    @Test
    public void guestSettingsTimesOutAndBatchRejectsRun() throws Exception {
        CountDownLatch failure = new CountDownLatch(1);
        List<String> result = new ArrayList<>();
        onMain(() -> {
            installTrace(session(1, step(ScreenId.SETTINGS, 80)));
            replay.setConfirmationTimeoutMs(1_000);
            BatchExperimentRunner batch = container.getBatchRunner();
            batch.setListener(new BatchExperimentRunner.OnBatchProgressListener() {
                @Override public void onConfigStarted(int index, int total, BatchExperimentRunner.ExperimentConfig config) {}
                @Override public void onBatchCompleted(int total) {
                    result.add("COMPLETED");
                    failure.countDown();
                }
                @Override public void onBatchFailed(int index, UUID runId, String reason) {
                    result.add(index + ":" + reason);
                    failure.countDown();
                }
            });
            batch.setupKeyBenchmarkMatrix(1);
            batch.startBatch();
        });
        assertTrue(failure.await(10, TimeUnit.SECONDS));
        assertEquals(java.util.Collections.singletonList("1:TIMEOUT"), result);
        assertEquals(ReplayEngine.State.INVALID, replay.getState());
        assertEquals(0, replay.getConfirmedStepCount());
        assertFalse(container.getBatchRunner().isRunning());
        assertTrue(logContains("result=INVALID_TIMEOUT"));
    }

    @Test
    public void followingFallbackIsWrongDestinationAfterDeadline() throws Exception {
        Result result = new Result();
        onMain(() -> {
            signInFixture();
            installTrace(session(1, step(ScreenId.FEED_FOLLOWING, 80)));
            replay.setConfirmationTimeoutMs(1_500);
            assertTrue(replay.startReplay(1, result));
            container.getSessionManager().logout();
        });
        assertTrue(result.done.await(10, TimeUnit.SECONDS));
        assertEquals("WRONG_DESTINATION", result.terminal);
        assertEquals(0, replay.getConfirmedStepCount());
        assertTrue(logContains("expected=FEED_FOLLOWING observed=FEED_NEWEST"));
    }

    @Test
    public void bundledAssetConfirmsAllSessionsAndSteps() throws Exception {
        List<String> expected = new ArrayList<>();
        try (InputStreamReader reader = new InputStreamReader(context.getAssets().open(
                ReplayEngine.DEFAULT_ASSET_FILE), StandardCharsets.UTF_8)) {
            List<ReplayEngine.ReplaySession> sessions = ReplayEngine.parseSessions(reader);
            assertEquals(100, sessions.size());
            for (int i = 0; i < sessions.size(); i++) {
                List<ReplayEngine.ReplayStep> steps = sessions.get(i).steps;
                for (int j = 0; j < steps.size(); j++) {
                    expected.add((i + 1) + ":" + (j + 1) + ":" + steps.get(j).screen);
                }
            }
        }
        Result result = new Result();
        float replayTimeScale = Float.parseFloat(
                InstrumentationRegistry.getArguments().getString("replayTimeScale", "10"));
        onMain(() -> {
            signInFixture();
            container.getPrefetchEngine().setCurrentPolicy(PrefetchEngine.Policy.P0_NONE);
            container.getPrefetchEngine().setTimeScale(replayTimeScale);
            assertTrue(replay.startReplay(100, result));
        });
        assertTrue("Replay did not finish", result.done.await(900, TimeUnit.SECONDS));
        assertEquals("FINISHED", result.terminal);
        assertEquals(expected, result.progress);
        assertEquals(514, replay.getConfirmedStepCount());
        assertEquals(514, replay.getNavigationEventCount());
    }

    private void assertCompleted(Result result, int steps, int events) throws InterruptedException {
        assertTrue(result.done.await(12, TimeUnit.SECONDS));
        assertEquals("FINISHED", result.terminal);
        assertEquals(steps, replay.getConfirmedStepCount());
        assertEquals(events, replay.getNavigationEventCount());
    }

    private void installTrace(String sessions) {
        replay.replaceSessions(ReplayEngine.parseSessions(new StringReader("{\"sessions\":[" + sessions + "]}")));
    }

    private static String session(int id, String steps) {
        return "{\"session_id\":" + id + ",\"steps\":[" + steps + "]}";
    }

    private static String step(ScreenId screen, int dwell) {
        return "{\"screen\":\"" + screen.getId() + "\",\"dwell_ms\":" + dwell + "}";
    }

    private void signInFixture() {
        UserResource user = new UserResource();
        user.id = 1;
        user.name = "Replay confirmation test";
        container.getSessionManager().saveSession("replay-confirmation-test-fixture", user);
    }

    private ScreenId visibleScreen() {
        ScreenId[] screen = new ScreenId[1];
        onMain(() -> screen[0] = container.getNavigationObserver().getVisibleScreen());
        return screen[0];
    }

    private void onMain(Runnable action) {
        instrumentation.runOnMainSync(action);
    }

    private void await(BooleanSupplier condition) {
        long deadline = SystemClock.elapsedRealtime() + 5_000;
        while (!condition.getAsBoolean() && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(25);
        }
        assertTrue(condition.getAsBoolean());
    }

    private boolean logContainsLiveBaseline(ScreenId screen) throws Exception {
        return readLogs().stream().anyMatch(line -> line.contains("sequence=1 ")
                && line.contains("fromScreen=null toScreen=" + screen + " ")
                && line.endsWith("source=LIVE"));
    }

    private boolean logContains(String text) throws Exception {
        return readLogs().stream().anyMatch(line -> line.contains(text));
    }

    private List<String> readLogs() throws Exception {
        ParcelFileDescriptor output = instrumentation.getUiAutomation().executeShellCommand(
                "logcat -d -v raw --pid=" + Process.myPid() + " -s ReplayConfirmation:I NavigationTelemetry:I BatchRunner:I");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ParcelFileDescriptor.AutoCloseInputStream(output), StandardCharsets.UTF_8))) {
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) lines.add(line);
            return lines;
        }
    }

    private static class Result implements ReplayEngine.OnReplayProgressListener {
        final CountDownLatch done = new CountDownLatch(1);
        final List<String> progress = new ArrayList<>();
        UUID runId;
        String terminal;

        @Override public void onReplayRunStarted(UUID id) { runId = id; }
        @Override public void onProgress(int session, int sessions, int step, int steps, ScreenId screen) {
            progress.add(session + ":" + step + ":" + screen);
        }
        @Override public void onSessionCompleted(int sessionId, int totalSessions) {}
        @Override public void onReplayFinished() { complete("FINISHED"); }
        @Override public void onReplayStopped() { complete("STOPPED"); }
        @Override public void onReplayFailed(UUID id, String reason) { complete(reason); }

        private void complete(String status) {
            terminal = status;
            done.countDown();
        }
    }
}
