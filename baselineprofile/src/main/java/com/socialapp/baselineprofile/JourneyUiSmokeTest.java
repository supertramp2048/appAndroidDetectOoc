package com.socialapp.baselineprofile;

import androidx.benchmark.macro.MacrobenchmarkScope;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class JourneyUiSmokeTest {
    @Test public void startup() { FixtureJourneys.openNewestFeed(freshScope()); }
    @Test public void feedToPostDetail() { FixtureJourneys.openFixturePost(freshScope()); }
    @Test public void postDetailToComments() { FixtureJourneys.openFixtureComments(freshScope()); }
    @Test public void feedToSearch() { FixtureJourneys.openSearch(freshScope()); }
    @Test public void feedToFactCheck() { FixtureJourneys.openFactCheck(freshScope()); }

    private static MacrobenchmarkScope freshScope() {
        MacrobenchmarkScope scope = new MacrobenchmarkScope(FixtureJourneys.targetPackage(), true);
        scope.killProcess();
        scope.getDevice().pressHome();
        return scope;
    }
}
