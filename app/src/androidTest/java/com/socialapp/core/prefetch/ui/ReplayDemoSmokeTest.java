package com.socialapp.core.prefetch.ui;

import android.app.Instrumentation;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.gson.Gson;
import com.socialapp.MyApplication;
import com.socialapp.core.di.AppContainer;
import com.socialapp.core.prefetch.PrefetchEngine;
import com.socialapp.core.prefetch.ReplayResult;
import com.socialapp.feature.home.HomeActivity;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class ReplayDemoSmokeTest {
    @Test
    public void overlayOpensCalibrationComparison() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        instrumentation.startActivitySync(new Intent(context, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        click(instrumentation, "▶ Replay");
        click(instrumentation, "So sánh và xuất CSV demo T4");
        await(() -> {
            AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
            return root != null && !root.findAccessibilityNodeInfosByText("Kết quả demo T4").isEmpty();
        }, 10_000, "Calibration comparison");
    }

    @Test
    public void shareSheetOpensWithFourFixtureCsvFiles() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        Activity activity = instrumentation.startActivitySync(new Intent(context, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        File cache = new File(context.getCacheDir(), "replay-demo");
        File[] previousFolders = cache.listFiles();
        List<File> previous = previousFolders == null ? java.util.Collections.emptyList() : Arrays.asList(previousFolders);
        File raw = new File(context.getCacheDir(), "t7-smoke-source-" + UUID.randomUUID());
        assertTrue(raw.mkdir());
        List<ReplayResult> fixture = new java.util.ArrayList<>();
        for (String mode : Arrays.asList("P0", "FIXED_LAMBDA", "ADAPTIVE_LAMBDA")) {
            UUID id = UUID.randomUUID();
            String json = "{\"runId\":\"" + id + "\",\"configuration\":{\"network\":\"LTE\"},"
                    + "\"metrics\":{\"fullHits\":1}"
                    + (mode.equals("P0") ? "" : ",\"lambda\":{\"mode\":\"" + mode + "\"}") + "}";
            fixture.add(new Gson().fromJson(json, ReplayResult.class));
            Files.write(new File(raw, id + ".csv").toPath(),
                    "fixture\n".getBytes(StandardCharsets.UTF_8));
        }
        instrumentation.runOnMainSync(() -> ReplayDemoExport.share(activity, fixture,
                id -> new File(raw, id + ".csv")));
        await(() -> {
            AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
            return root != null && ("android".contentEquals(root.getPackageName())
                    || "com.android.intentresolver".contentEquals(root.getPackageName()));
        }, 10_000, "Android share sheet");
        File folder = Arrays.stream(cache.listFiles()).filter(output -> !previous.contains(output))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(4, folder.list().length);
        assertEquals(4, Files.readAllLines(new File(folder, "t4_calibration_comparison.csv").toPath(),
                StandardCharsets.UTF_8).size());
        for (ReplayResult result : fixture) {
            assertEquals("fixture\n", new String(Files.readAllBytes(
                    new File(folder, result.runId + ".csv").toPath()), StandardCharsets.UTF_8));
        }
    }

    @Test
    public void calibrationModesCanBeComparedAndShared() throws Exception {
        Assume.assumeTrue("Opt in to running three on-device Calibration replays",
                "true".equals(InstrumentationRegistry.getArguments().getString("t7Demo")));
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        instrumentation.startActivitySync(new Intent(context, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        AppContainer app = MyApplication.getAppContainer(context);
        List<PrefetchEngine.P5Mode> modes = Arrays.asList(
                null, PrefetchEngine.P5Mode.FIXED_LAMBDA, PrefetchEngine.P5Mode.ADAPTIVE_LAMBDA);
        String selected = InstrumentationRegistry.getArguments().getString("t7Mode");
        for (PrefetchEngine.P5Mode mode : modes) {
            if (selected != null && !selected.equals(mode == null ? "P0" : mode.name())) continue;
            ReplayResult previousRun = app.getReplayEngine().getLatestResult();
            instrumentation.runOnMainSync(() -> {
                PrefetchEngine engine = app.getPrefetchEngine();
                engine.setCurrentNetwork(PrefetchEngine.NetworkProfile.LTE);
                engine.setPayloadScale(1f);
                engine.setTimeScale(5f);
                engine.setSimulationMode(true);
                engine.setCurrentPolicy(mode == null ? PrefetchEngine.Policy.P0_NONE
                        : PrefetchEngine.Policy.P5_ADAPTIVE_NETWORK);
                if (mode != null) engine.setNextP5Mode(mode);
                assertTrue(app.getReplayEngine().startT4CalibrationReplay());
            });
            await(() -> {
                ReplayResult latest = app.getReplayEngine().getLatestResult();
                if (!app.getReplayEngine().isActive() && latest != null && latest != previousRun) {
                    assertEquals(latest.reason, ReplayResult.Outcome.COMPLETED, latest.outcome);
                    if (latest.getExportStatus() == null) return false;
                    assertTrue("Incomplete CSV", latest.getExportStatus().isComplete());
                    return app.getReplayEngine().getDemoResults().stream()
                            .anyMatch(result -> result.runId.equals(latest.runId));
                }
                return false;
            }, 150_000, "Calibration mode " + mode);
            click(instrumentation, "Đóng");
        }
        if (selected != null && app.getReplayEngine().getDemoResults().size() < 3) return;
        assertEquals(3, app.getReplayEngine().getDemoResults().size());
        click(instrumentation, "▶ Replay");
        click(instrumentation, "So sánh và xuất CSV demo T4");
        File cache = new File(context.getCacheDir(), "replay-demo");
        File[] existing = cache.listFiles();
        List<File> previous = existing == null ? java.util.Collections.emptyList() : Arrays.asList(existing);
        click(instrumentation, "Xuất CSV");
        await(() -> {
            File[] folders = cache.listFiles();
            if (folders == null) return false;
            return Arrays.stream(folders).filter(folder -> !previous.contains(folder))
                    .anyMatch(folder -> folder.list() != null && folder.list().length == 4);
        }, 10_000, "CSV export");
        File folder = Arrays.stream(cache.listFiles()).filter(output -> !previous.contains(output))
                .findFirst().orElseThrow(AssertionError::new);
        File summary = new File(folder, "t4_calibration_comparison.csv");
        List<String> lines = Files.readAllLines(summary.toPath(), StandardCharsets.UTF_8);
        assertEquals(4, lines.size());
        assertTrue(lines.get(1).startsWith("P0,"));
        assertTrue(lines.get(2).startsWith("P5_FIXED_LAMBDA,"));
        assertTrue(lines.get(3).startsWith("P5_ADAPTIVE_LAMBDA,"));
        await(() -> {
            AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
            return root != null && !"com.socialapp".contentEquals(root.getPackageName());
        }, 10_000, "Android share sheet");
    }

    private static void click(Instrumentation instrumentation, String text) {
        await(() -> {
            AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
            if (root == null) return false;
            for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text)) {
                if (!text.equalsIgnoreCase(node.getText() == null ? "" : node.getText().toString())) continue;
                AccessibilityNodeInfo clickable = node;
                while (clickable != null && !clickable.isClickable()) clickable = clickable.getParent();
                assertNotNull(text, clickable);
                if (clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            }
            return false;
        }, 10_000, text);
    }

    private static void await(BooleanSupplier predicate, long timeout, String description) {
        long deadline = SystemClock.elapsedRealtime() + timeout;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate.getAsBoolean()) return;
            SystemClock.sleep(100);
        }
        throw new AssertionError("Timed out waiting for " + description);
    }
}
