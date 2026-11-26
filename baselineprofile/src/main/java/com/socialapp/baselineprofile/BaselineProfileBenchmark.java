package com.socialapp.baselineprofile;

import androidx.benchmark.macro.BaselineProfileMode;
import androidx.benchmark.macro.CompilationMode;
import androidx.benchmark.macro.FrameTimingMetric;
import androidx.benchmark.macro.StartupMode;
import androidx.benchmark.macro.StartupTimingMetric;
import androidx.benchmark.macro.junit4.MacrobenchmarkRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.function.Consumer;

import kotlin.Unit;

@RunWith(AndroidJUnit4.class)
public class BaselineProfileBenchmark {
    @Rule public final MacrobenchmarkRule benchmark = new MacrobenchmarkRule();

    private UiObject2 navigationButton;

    @Test public void startupB0() { startup(new CompilationMode.None()); }
    @Test public void startupB1() { startup(requireBaselineProfile()); }
    @Test public void feedToPostDetailB0() { navigate(new CompilationMode.None(), false); }
    @Test public void feedToPostDetailB1() { navigate(requireBaselineProfile(), false); }
    @Test public void postDetailToCommentsB0() { navigate(new CompilationMode.None(), true); }
    @Test public void postDetailToCommentsB1() { navigate(requireBaselineProfile(), true); }
    @Test public void feedToSearchB1() { navigateToTab("nav_search", FixtureJourneys::waitForSearch); }
    @Test public void feedToFactCheckB1() { navigateToTab("nav_factcheck", FixtureJourneys::waitForFactCheck); }

    private void startup(CompilationMode compilationMode) {
        benchmark.measureRepeated(FixtureJourneys.targetPackage(),
                Collections.singletonList(new StartupTimingMetric()),
                compilationMode, StartupMode.COLD, iterations(),
                scope -> {
                    scope.getDevice().pressHome();
                    return Unit.INSTANCE;
                },
                scope -> {
                    FixtureJourneys.openNewestFeed(scope);
                    return Unit.INSTANCE;
                });
    }

    private void navigate(CompilationMode compilationMode, boolean commentsJourney) {
        benchmark.measureRepeated(FixtureJourneys.targetPackage(),
                Collections.singletonList(new FrameTimingMetric()), compilationMode, null, iterations(),
                scope -> {
                    scope.killProcess();
                    scope.getDevice().pressHome();
                    navigationButton = FixtureJourneys.openNewestFeed(scope);
                    if (commentsJourney) {
                        navigationButton.click();
                        FixtureJourneys.waitForPostDetail(scope.getDevice());
                        navigationButton = FixtureJourneys.findCommentsButton(scope.getDevice());
                    }
                    return Unit.INSTANCE;
                },
                scope -> {
                    navigationButton.click();
                    if (commentsJourney) {
                        FixtureJourneys.waitForComments(scope.getDevice());
                    } else {
                        FixtureJourneys.waitForPostDetail(scope.getDevice());
                    }
                    return Unit.INSTANCE;
                });
    }

    private static CompilationMode requireBaselineProfile() {
        return new CompilationMode.Partial(BaselineProfileMode.Require, 0);
    }

    private void navigateToTab(String tabId, Consumer<UiDevice> waitForDestination) {
        benchmark.measureRepeated(FixtureJourneys.targetPackage(),
                Collections.singletonList(new FrameTimingMetric()), requireBaselineProfile(), null, iterations(),
                scope -> {
                    scope.killProcess();
                    scope.getDevice().pressHome();
                    FixtureJourneys.openNewestFeed(scope);
                    navigationButton = FixtureJourneys.bottomNavButton(scope.getDevice(), tabId);
                    return Unit.INSTANCE;
                },
                scope -> {
                    navigationButton.click();
                    waitForDestination.accept(scope.getDevice());
                    return Unit.INSTANCE;
                });
    }

    private static int iterations() {
        int count = Integer.parseInt(InstrumentationRegistry.getArguments().getString("t6Iterations", "3"));
        if (count < 1) throw new IllegalArgumentException("t6Iterations must be positive");
        return count;
    }
}
