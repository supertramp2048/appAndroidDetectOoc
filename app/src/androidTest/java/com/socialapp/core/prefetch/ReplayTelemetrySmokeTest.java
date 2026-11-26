package com.socialapp.core.prefetch;

import android.app.Instrumentation;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.socialapp.MyApplication;
import com.socialapp.core.di.AppContainer;
import com.socialapp.core.prefetch.ui.GlobalDemoOverlayManager;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class ReplayTelemetrySmokeTest {
    @Test
    public void exportsOneGuestCompatibleSessionFromBundledAsset() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        ReplayEngine.ReplaySession session;
        try (InputStreamReader reader = new InputStreamReader(
                context.getAssets().open(ReplayEngine.DEFAULT_ASSET_FILE), StandardCharsets.UTF_8)) {
            session = ReplayEngine.parseSessions(reader).get(3); // Session 4, with no Following or Settings.
        }
        runSmoke(session, PrefetchEngine.Policy.P0_NONE, PrefetchEngine.P5Mode.LEGACY);
    }

    @Test
    public void exportsAdaptiveP5FromAShortFeedSession() throws Exception {
        String json = "{\"sessions\":[{\"session_id\":1,\"steps\":["
                + "{\"screen\":\"FEED_HOT\",\"dwell_ms\":900},"
                + "{\"screen\":\"FEED_NEWEST\",\"dwell_ms\":900}]}]}";
        ReplayEngine.ReplaySession session = ReplayEngine.parseSessions(new StringReader(json)).get(0);
        runSmoke(session, PrefetchEngine.Policy.P5_ADAPTIVE_NETWORK, PrefetchEngine.P5Mode.ADAPTIVE_LAMBDA);
    }

    private void runSmoke(ReplayEngine.ReplaySession session, PrefetchEngine.Policy testPolicy,
                          PrefetchEngine.P5Mode testMode) throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        AppContainer container = MyApplication.getAppContainer(instrumentation.getTargetContext());
        ReplayEngine replay = container.getReplayEngine();
        PrefetchEngine engine = container.getPrefetchEngine();
        CountDownLatch completed = new CountDownLatch(1);
        boolean overlay = GlobalDemoOverlayManager.isEnabled();
        float speed = engine.getTimeScale();
        PrefetchEngine.Policy policy = engine.getCurrentPolicy();
        PrefetchEngine.P5Mode mode = engine.getNextP5Mode();
        boolean simulation = engine.isSimulationMode();
        try {
            instrumentation.runOnMainSync(() -> {
                GlobalDemoOverlayManager.setEnabled(false);
                engine.setSimulationMode(true);
                engine.setCurrentPolicy(testPolicy);
                engine.setNextP5Mode(testMode);
                engine.setTimeScale(3);
                replay.replaceSessions(Collections.singletonList(session));
                String prefix = testPolicy == PrefetchEngine.Policy.P0_NONE ? "t3_smoke" : "t4a_smoke";
                assertTrue(replay.startReplay(1, prefix, new ReplayEngine.OnReplayProgressListener() {
                    @Override public void onProgress(int session, int sessions, int step, int steps, ScreenId screen) {}
                    @Override public void onSessionCompleted(int sessionId, int sessions) {}
                    @Override public void onReplayFinished() { completed.countDown(); }
                    @Override public void onReplayStopped() { completed.countDown(); }
                    @Override public void onReplayFailed(java.util.UUID runId, String reason) { completed.countDown(); }
                }));
            });
            assertTrue("Replay and export did not finish", completed.await(45, TimeUnit.SECONDS));
            ReplayResult result = replay.getLatestResult();
            assertNotNull(result);
            assertEquals(result.reason, ReplayResult.Outcome.COMPLETED, result.outcome);
            assertEquals(session.steps.size(), result.confirmedSteps);
            assertTrue(result.getExportStatus().isComplete());
            if (testPolicy == PrefetchEngine.Policy.P5_ADAPTIVE_NETWORK) {
                assertNotNull(result.lambda);
                assertEquals(testMode, result.lambda.mode);
                assertEquals(AdaptiveLambdaController.Config.DEFAULT.initial, result.lambda.initial, 0);
            }
            android.util.Log.i("ReplayTelemetrySmoke", "runId=" + result.runId);
        } finally {
            instrumentation.runOnMainSync(() -> {
                if (replay.isActive()) replay.stopReplay();
                replay.loadSessionsFromAsset(ReplayEngine.DEFAULT_ASSET_FILE);
                engine.setCurrentPolicy(policy);
                engine.setNextP5Mode(mode);
                engine.setTimeScale(speed);
                engine.setSimulationMode(simulation);
                GlobalDemoOverlayManager.setEnabled(overlay);
            });
        }
    }
}
