package com.socialapp.feature.home;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemClock;
import android.view.View;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleCallback;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.socialapp.MyApplication;
import com.socialapp.R;
import com.socialapp.core.mock.FakeApi;
import com.socialapp.core.network.dto.UserResource;
import com.socialapp.core.prefetch.Repository;
import com.socialapp.core.prefetch.ui.GlobalDemoOverlayManager;
import com.socialapp.core.session.SessionManager;
import com.socialapp.feature.auth.AuthActivity;
import com.socialapp.feature.settings.SettingsActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class NavigationTelemetryTest {
    private static final Pattern EVENT = Pattern.compile(
            "^runId=([\\w-]+) sessionId=([\\w-]+) sequence=(\\d+) navigationId=([\\w-]+)"
                    + " fromScreen=(\\w+) toScreen=(\\w+) timestampMs=(\\d+) source=LIVE$");

    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();
    private final SessionManager sessions = MyApplication.getAppContainer(context).getSessionManager();
    private String previousToken;
    private UserResource previousUser;
    private boolean overlayEnabled;
    private int logOffset;

    @Before
    public void setUp() throws IOException {
        instrumentation.runOnMainSync(() -> {
            previousToken = sessions.getAuthToken();
            previousUser = sessions.getCurrentUser();
            overlayEnabled = GlobalDemoOverlayManager.isEnabled();
            GlobalDemoOverlayManager.setEnabled(false);
            sessions.logout();
        });
        // Enter a real out-of-scope destination to start each test without a predecessor.
        try (ActivityScenario<AuthActivity> auth = ActivityScenario.launch(AuthActivity.class)) {
            assertEquals(Lifecycle.State.RESUMED, auth.getState());
            instrumentation.waitForIdleSync();
        }
        instrumentation.waitForIdleSync();
        logOffset = readEvents().size();
    }

    @After
    public void tearDown() {
        instrumentation.runOnMainSync(() -> {
            if (previousToken != null && previousUser != null) {
                sessions.saveSession(previousToken, previousUser);
            } else {
                sessions.logout();
            }
            GlobalDemoOverlayManager.setEnabled(overlayEnabled);
        });
    }

    @Test
    public void coldHotAndManualHomeDestinations() throws IOException {
        instrumentation.runOnMainSync(() -> {
            UserResource user = new UserResource();
            user.id = 1;
            user.name = "Telemetry test";
            sessions.saveSession("navigation-telemetry-test-fixture", user);
        });
        try (ActivityScenario<HomeActivity> home = launchHome("hot")) {
            assertEvents("null->FEED_HOT");

            onView(withText("Mới nhất")).perform(click());
            onView(withText("Nổi bật")).perform(click());
            onView(withText("Đang theo dõi")).perform(click());
            onView(withId(R.id.nav_search)).perform(click());
            onView(withId(R.id.nav_factcheck)).perform(click());
            onView(withId(R.id.nav_profile)).perform(click());
            onView(withId(R.id.btn_profile_settings)).perform(click());
            pressBack();

            assertEvents("null->FEED_HOT", "FEED_HOT->FEED_NEWEST", "FEED_NEWEST->FEED_HOT",
                    "FEED_HOT->FEED_FOLLOWING", "FEED_FOLLOWING->SEARCH", "SEARCH->FACT_CHECK",
                    "FACT_CHECK->PROFILE", "PROFILE->SETTINGS", "SETTINGS->PROFILE");
        }
    }

    @Test
    public void articleCommentsBackAndRefreshUseVisibleDestinations() throws IOException {
        try (ActivityScenario<HomeActivity> home = launchHome("newest")) {
            assertEvents("null->FEED_NEWEST");
            onView(withText(FakeApi.getSamplePosts().get(0).title)).perform(click());
            assertEvents("null->FEED_NEWEST", "FEED_NEWEST->POST_DETAIL");
            onView(withId(R.id.btn_comments)).perform(click());
            assertEvents("null->FEED_NEWEST", "FEED_NEWEST->POST_DETAIL", "POST_DETAIL->COMMENTS");

            pressBack();
            assertEvents("null->FEED_NEWEST", "FEED_NEWEST->POST_DETAIL", "POST_DETAIL->COMMENTS",
                    "COMMENTS->POST_DETAIL");
            pressBack();
            assertEvents("null->FEED_NEWEST", "FEED_NEWEST->POST_DETAIL", "POST_DETAIL->COMMENTS",
                    "COMMENTS->POST_DETAIL", "POST_DETAIL->FEED_NEWEST");
            pullToRefresh(home);
            assertEvents("null->FEED_NEWEST", "FEED_NEWEST->POST_DETAIL", "POST_DETAIL->COMMENTS",
                    "COMMENTS->POST_DETAIL", "POST_DETAIL->FEED_NEWEST");
        }
    }

    @Test
    public void recreationAndShortBackgroundKeepCurrentScreen() throws IOException {
        try (ActivityScenario<HomeActivity> home = launchHome("hot")) {
            assertEvents("null->FEED_HOT");
            home.recreate();
            assertEvents("null->FEED_HOT");

            home.onActivity(activity -> assertTrue(activity.moveTaskToBack(true)));
            awaitActivityState(home, Lifecycle.State.CREATED);
            Intent bringToFront = new Intent(context, HomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            instrumentation.runOnMainSync(() -> context.startActivity(bringToFront));
            awaitActivityState(home, Lifecycle.State.RESUMED);
            assertEvents("null->FEED_HOT");
        }
    }

    @Test
    public void guestFollowingDialogAndAuthHaveDifferentNavigationEffects() throws IOException {
        try (ActivityScenario<HomeActivity> home = launchHome("newest")) {
            assertEvents("null->FEED_NEWEST");
            onView(withId(R.id.btn_require_login_cancel)).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist());
            onView(withText("Đang theo dõi")).perform(click());
            onView(withId(R.id.btn_require_login_cancel)).check(matches(isDisplayed()));
            assertEvents("null->FEED_NEWEST");
            onView(withId(R.id.btn_require_login_cancel)).perform(click());
            onView(withText("Nổi bật")).perform(click());
            assertEvents("null->FEED_NEWEST", "FEED_NEWEST->FEED_HOT");

            onView(withId(R.id.fab_create_post)).perform(click());
            onView(withId(R.id.btn_require_login_submit)).perform(click());
            awaitActivityState(home, Lifecycle.State.CREATED);
            pressBack();
            awaitActivityState(home, Lifecycle.State.RESUMED);
            assertEvents("null->FEED_NEWEST", "FEED_NEWEST->FEED_HOT", "null->FEED_HOT");
        }
    }

    @Test
    public void settingsFinishingForGuestDoesNotConfirmOrBreakChain() throws IOException, InterruptedException {
        try (ActivityScenario<HomeActivity> home = launchHome("newest")) {
            assertEvents("null->FEED_NEWEST");
            CountDownLatch settingsDestroyed = new CountDownLatch(1);
            ActivityLifecycleCallback callback = (activity, stage) -> {
                if (activity instanceof SettingsActivity && stage == Stage.DESTROYED) {
                    settingsDestroyed.countDown();
                }
            };
            instrumentation.runOnMainSync(() ->
                    ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(callback));
            try {
                home.onActivity(activity -> activity.startActivity(new Intent(activity, SettingsActivity.class)));
                assertTrue("Guest Settings finished", settingsDestroyed.await(5, TimeUnit.SECONDS));
            } finally {
                instrumentation.runOnMainSync(() ->
                        ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(callback));
            }
            awaitActivityState(home, Lifecycle.State.RESUMED);
            assertEvents("null->FEED_NEWEST");
            onView(withText("Nổi bật")).perform(click());
            assertEvents("null->FEED_NEWEST", "FEED_NEWEST->FEED_HOT");
        }
    }

    private ActivityScenario<HomeActivity> launchHome(String tab) {
        Intent intent = new Intent(context, HomeActivity.class)
                .putExtra(HomeActivity.EXTRA_NAV_DESTINATION, R.id.nav_home)
                .putExtra(HomeActivity.EXTRA_FEED_TAB, tab);
        return ActivityScenario.launch(intent);
    }

    private void awaitActivityState(ActivityScenario<?> scenario, Lifecycle.State expected) {
        long deadline = SystemClock.elapsedRealtime() + 5000;
        while (scenario.getState() != expected && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(50);
        }
        assertEquals(expected, scenario.getState());
    }

    private void pullToRefresh(ActivityScenario<HomeActivity> home) throws IOException {
        int[] swipe = new int[3];
        home.onActivity(activity -> {
            View refresh = activity.findViewById(R.id.feed_swipe_refresh);
            int[] location = new int[2];
            refresh.getLocationOnScreen(location);
            swipe[0] = location[0] + refresh.getWidth() / 2;
            swipe[1] = location[1] + refresh.getHeight() / 4;
            swipe[2] = location[1] + refresh.getHeight() * 3 / 4;
        });
        int previousLoadStep = Repository.getStepIndex();
        String gesture = "input swipe " + swipe[0] + " " + swipe[1] + " " + swipe[0] + " " + swipe[2] + " 300";
        ParcelFileDescriptor stream = instrumentation.getUiAutomation().executeShellCommand(gesture);
        try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(stream)) {
            input.read();
        }

        long deadline = SystemClock.elapsedRealtime() + 5000;
        while (Repository.getStepIndex() == previousLoadStep && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(50);
        }
        assertTrue("The refresh completed a data load", Repository.getStepIndex() > previousLoadStep);
    }

    private void assertEvents(String... expected) throws IOException {
        long deadline = SystemClock.elapsedRealtime() + 5000;
        List<String> events;
        do {
            instrumentation.waitForIdleSync();
            SystemClock.sleep(150);
            List<String> allEvents = readEvents();
            events = allEvents.subList(logOffset, allEvents.size());
        } while (events.size() < expected.length && SystemClock.elapsedRealtime() < deadline);

        assertEquals(events.toString(), expected.length, events.size());
        long previousSequence = 0;
        String sessionId = null;
        for (int i = 0; i < expected.length; i++) {
            Matcher matcher = EVENT.matcher(events.get(i));
            assertTrue(events.get(i), matcher.matches());
            assertEquals(expected[i], matcher.group(5) + "->" + matcher.group(6));
            long sequence = Long.parseLong(matcher.group(3));
            if (i > 0) {
                assertEquals(sessionId, matcher.group(2));
                assertEquals(previousSequence + 1, sequence);
            }
            sessionId = matcher.group(2);
            previousSequence = sequence;
        }
    }

    private List<String> readEvents() throws IOException {
        String command = "logcat -d -v raw --pid=" + Process.myPid() + " -s NavigationTelemetry:I";
        ParcelFileDescriptor stream = instrumentation.getUiAutomation().executeShellCommand(command);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ParcelFileDescriptor.AutoCloseInputStream(stream), StandardCharsets.UTF_8))) {
            List<String> events = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("runId=")) {
                    events.add(line);
                }
            }
            return events;
        }
    }
}
