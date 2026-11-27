package com.socialapp.core.prefetch;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.socialapp.MyApplication;
import com.socialapp.core.di.AppContainer;
import com.socialapp.core.prefetch.profile.ProfileStore;
import com.socialapp.core.prefetch.ui.GlobalDemoOverlayManager;
import com.socialapp.feature.home.HomeActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class P5CalibrationTest {
    @Test
    public void runSelectedSessions() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Bundle arguments = InstrumentationRegistry.getArguments();
        boolean verifyLivePersistence = Boolean.parseBoolean(arguments.getString("verifyLivePersistence", "false"));
        String selection = arguments.getString("sessionIds");
        org.junit.Assume.assumeTrue("Calibration must be invoked with a pinned workload", selection != null);
        PrefetchEngine.P5Mode mode = PrefetchEngine.P5Mode.valueOf(arguments.getString("p5Mode", ""));
        assertTrue(mode == PrefetchEngine.P5Mode.FIXED_LAMBDA || mode == PrefetchEngine.P5Mode.ADAPTIVE_LAMBDA);
        Set<Integer> ids = new LinkedHashSet<>();
        for (String id : selection.split(",")) {
            assertTrue("Repeated session " + id, ids.add(Integer.parseInt(id)));
        }
        List<ReplayEngine.ReplaySession> selected = new ArrayList<>();
        try (InputStreamReader reader = new InputStreamReader(instrumentation.getTargetContext().getAssets()
                .open(ReplayEngine.DEFAULT_ASSET_FILE), StandardCharsets.UTF_8)) {
            for (ReplayEngine.ReplaySession session : ReplayEngine.parseSessions(reader)) {
                if (ids.contains(session.sessionId)) selected.add(session);
            }
        }
        assertEquals(ids.size(), selected.size());
        assertFalse(ReplayEngine.requiresLogin(selected, selected.size()));
        int steps = selected.stream().mapToInt(s -> s.steps.size()).sum();
        CountDownLatch terminal = new CountDownLatch(1);
        AppContainer container = MyApplication.getAppContainer(instrumentation.getTargetContext());
        ReplayEngine replay = container.getReplayEngine();
        PrefetchEngine engine = container.getPrefetchEngine();
        boolean overlay = GlobalDemoOverlayManager.isEnabled();
        PrefetchEngine.Policy policy = engine.getCurrentPolicy();
        PrefetchEngine.NetworkProfile network = engine.getCurrentNetwork();
        PrefetchEngine.P5Mode previousMode = engine.getNextP5Mode();
        float speed = engine.getTimeScale();
        float payload = engine.getPayloadScale();
        boolean simulation = engine.isSimulationMode();
        try {
            // Replay may replace the original HomeActivity. Avoid holding a scenario for that instance.
            instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), HomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            File liveFile = new File(instrumentation.getTargetContext().getFilesDir(), ProfileStore.LIVE_FILE_NAME);
            byte[] liveBefore = verifyLivePersistence ? Files.readAllBytes(liveFile.toPath()) : null;
            instrumentation.runOnMainSync(() -> {
                GlobalDemoOverlayManager.setEnabled(false);
                engine.setCurrentPolicy(PrefetchEngine.Policy.P5_ADAPTIVE_NETWORK);
                engine.setCurrentNetwork(PrefetchEngine.NetworkProfile.LTE);
                engine.setNextP5Mode(mode);
                engine.setPayloadScale(1);
                engine.setTimeScale(5);
                engine.setSimulationMode(true);
                engine.setLambdaConfig(AdaptiveLambdaController.Config.DEFAULT);
                replay.replaceSessions(selected);
                String prefix = verifyLivePersistence ? "t6d1_feedback" : "t4b1_" + mode.name().toLowerCase(Locale.ROOT);
                assertTrue(replay.startReplay(selected.size(), prefix,
                        new ReplayEngine.OnReplayProgressListener() {
                            @Override public void onReplayRunStarted(UUID runId) {
                                Log.i("P5Calibration", "mode=" + mode + " runId=" + runId + " sessions=" + ids);
                            }
                            @Override public void onProgress(int s, int sessions, int t, int total, ScreenId screen) {}
                            @Override public void onSessionCompleted(int id, int count) {}
                            @Override public void onReplayFinished() { terminal.countDown(); }
                            @Override public void onReplayStopped() { terminal.countDown(); }
                            @Override public void onReplayFailed(UUID runId, String reason) { terminal.countDown(); }
                        }));
            });
            assertTrue("Replay timed out awaiting export", terminal.await(150, TimeUnit.SECONDS));
            ReplayResult result = replay.getLatestResult();
            assertNotNull(result);
            assertEquals(result.reason, ReplayResult.Outcome.COMPLETED, result.outcome);
            assertEquals(steps, result.confirmedSteps);
            assertEquals(mode, result.lambda.mode);
            assertTrue(result.getExportStatus().isComplete());
            if (verifyLivePersistence) {
                assertArrayEquals("Replay changed the persisted LIVE checkpoint",
                        liveBefore, Files.readAllBytes(liveFile.toPath()));
                Log.i("P5Calibration", "LIVE_PERSISTENCE_UNCHANGED runId=" + result.runId);
            }
        } finally {
            instrumentation.runOnMainSync(() -> {
                if (replay.isActive()) replay.stopReplay();
                replay.loadSessionsFromAsset(ReplayEngine.DEFAULT_ASSET_FILE);
                engine.setCurrentPolicy(policy);
                engine.setCurrentNetwork(network);
                engine.setNextP5Mode(previousMode);
                engine.setPayloadScale(payload);
                engine.setTimeScale(speed);
                engine.setSimulationMode(simulation);
                GlobalDemoOverlayManager.setEnabled(overlay);
            });
        }
    }
}
