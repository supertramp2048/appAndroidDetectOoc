package com.socialapp.baselineprofile;

import androidx.benchmark.macro.MacrobenchmarkScope;
import androidx.benchmark.macro.junit4.BaselineProfileRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

import static org.junit.Assume.assumeTrue;

@RunWith(AndroidJUnit4.class)
public class BaselineProfileGenerator {
    @Rule public final BaselineProfileRule baselineProfile = new BaselineProfileRule();

    @Test
    public void startup() {
        collect(true, scope -> {
            FixtureJourneys.openNewestFeed(scope);
            return Unit.INSTANCE;
        });
    }

    @Test
    public void feedToPostDetail() {
        collect(false, scope -> {
            FixtureJourneys.openFixturePost(scope);
            return Unit.INSTANCE;
        });
    }

    @Test
    public void postDetailToComments() {
        collect(false, scope -> {
            FixtureJourneys.openFixtureComments(scope);
            return Unit.INSTANCE;
        });
    }

    @Test
    public void feedToSearch() {
        assumeTrue("Requires explicit T6 journey selection", fixedIterations() != null);
        collect(false, scope -> {
            FixtureJourneys.openSearch(scope);
            return Unit.INSTANCE;
        });
    }

    @Test
    public void feedToFactCheck() {
        assumeTrue("Requires explicit T6 journey selection", fixedIterations() != null);
        collect(false, scope -> {
            FixtureJourneys.openFactCheck(scope);
            return Unit.INSTANCE;
        });
    }

    private void collect(boolean includeInStartupProfile, Function1<MacrobenchmarkScope, Unit> journey) {
        Integer fixed = fixedIterations();
        int maxIterations = fixed == null ? 15 : fixed;
        int stableIterations = fixed == null ? 3 : fixed;
        baselineProfile.collect(FixtureJourneys.targetPackage(), maxIterations, stableIterations, null,
                includeInStartupProfile, false, rule -> true, scope -> {
                    scope.getDevice().pressHome();
                    return journey.invoke(scope);
                });
    }

    private static Integer fixedIterations() {
        String value = InstrumentationRegistry.getArguments().getString("t6ProfileIterations");
        if (value == null) return null;
        int count = Integer.parseInt(value);
        if (count < 1) throw new IllegalArgumentException("t6ProfileIterations must be positive");
        return count;
    }
}
