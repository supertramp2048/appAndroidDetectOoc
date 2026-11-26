package com.socialapp.feature.home;

import android.app.Instrumentation;
import android.content.Context;
import android.os.SystemClock;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.tabs.TabLayout;
import com.socialapp.MyApplication;
import com.socialapp.R;
import com.socialapp.core.di.AppContainer;
import com.socialapp.core.navigation.Navigator;
import com.socialapp.core.network.dto.UserResource;
import com.socialapp.core.prefetch.BatchExperimentRunner;
import com.socialapp.core.prefetch.ReplayEngine;
import com.socialapp.core.prefetch.ScreenId;
import com.socialapp.feature.feed.FeedFragment;
import com.socialapp.feature.feed.FeedViewModel;
import com.socialapp.feature.feed.FeedViewModel.FeedTab;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class ReplayFeedNavigationTest {

    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();
    private final AppContainer container = MyApplication.getAppContainer(context);
    private Instrumentation.ActivityMonitor homeMonitor;
    private String previousToken;
    private UserResource previousUser;

    @Before
    public void setUp() {
        homeMonitor = instrumentation.addMonitor(HomeActivity.class.getName(), null, false);
        previousToken = container.getSessionManager().getAuthToken();
        previousUser = container.getSessionManager().getCurrentUser();
    }

    @After
    public void tearDown() {
        instrumentation.runOnMainSync(() -> {
            if (container.getReplayEngine().isRunning()) {
                container.getReplayEngine().stopReplay();
            }
            if (previousToken != null && previousUser != null) {
                container.getSessionManager().saveSession(previousToken, previousUser);
            } else {
                container.getSessionManager().logout();
            }
            if (homeMonitor.getLastActivity() != null) {
                homeMonitor.getLastActivity().finish();
            }
        });
        instrumentation.waitForIdleSync();
        instrumentation.removeMonitor(homeMonitor);
    }

    @Test
    public void coldHotAndNewIntentsSelectTheRequestedFeedTab() {
        navigate(ScreenId.FEED_HOT);
        HomeActivity home = (HomeActivity) homeMonitor.waitForActivityWithTimeout(5000);
        assertNotNull(home);
        assertFeed(home, FeedTab.HOT);

        AtomicReference<FeedViewModel> initialModel = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> initialModel.set(feedViewModel(home)));

        navigate(ScreenId.FEED_NEWEST);
        assertFeed(home, FeedTab.NEWEST);
        instrumentation.runOnMainSync(() -> {
            assertEquals("newest", home.getIntent().getStringExtra(HomeActivity.EXTRA_FEED_TAB));
            assertSame(initialModel.get(), feedViewModel(home));
            saveFixtureSession();
            Navigator.navigateTo(context, Navigator.ROUTE_SEARCH);
        });
        await("Search destination", 5000, () -> isAt(home, R.id.nav_search));

        navigate(ScreenId.FEED_FOLLOWING);
        assertFeed(home, FeedTab.FOLLOWING);
        instrumentation.runOnMainSync(() -> {
            assertEquals("following", home.getIntent().getStringExtra(HomeActivity.EXTRA_FEED_TAB));
            feedTabs(home).getTabAt(1).select();
        });
        assertFeed(home, FeedTab.HOT);

        instrumentation.runOnMainSync(home::recreate);
        await("recreated Home restores HOT without replaying the FOLLOWING intent", 5000, () -> {
            HomeActivity recreated = (HomeActivity) homeMonitor.getLastActivity();
            return recreated != null && recreated != home && showsFeed(recreated, FeedTab.HOT);
        });
    }

    @Test
    public void takingFollowingWithoutLoginRejectsReplayAndBatch() {
        instrumentation.runOnMainSync(() -> {
            container.getSessionManager().logout();
            ReplayEngine replay = container.getReplayEngine();
            ReplayEngine.State before = replay.getState();
            assertFalse(replay.startReplay(3));
            assertSame(before, replay.getState());

            BatchExperimentRunner batch = container.getBatchRunner();
            batch.setupKeyBenchmarkMatrix(3);
            batch.startBatch();
            assertFalse(batch.isRunning());
            assertSame(before, replay.getState());
        });
    }

    @Test
    public void replayFromRealTraceReachesFollowing() {
        float originalTimeScale = container.getPrefetchEngine().getTimeScale();
        try {
            instrumentation.runOnMainSync(() -> {
                saveFixtureSession();
                container.getPrefetchEngine().setTimeScale(4.0f);
                assertTrue(container.getReplayEngine().startReplay(3));
            });
            await("Replay opens FOLLOWING from session 3", 25000, () -> {
                HomeActivity home = (HomeActivity) homeMonitor.getLastActivity();
                return home != null && showsFeed(home, FeedTab.FOLLOWING);
            });
        } finally {
            instrumentation.runOnMainSync(() -> {
                if (container.getReplayEngine().isRunning()) {
                    container.getReplayEngine().stopReplay();
                }
                container.getPrefetchEngine().setTimeScale(originalTimeScale);
            });
        }
    }

    private void saveFixtureSession() {
        UserResource user = new UserResource();
        user.id = 1;
        user.name = "Replay test";
        container.getSessionManager().saveSession("replay-instrumentation-fixture", user);
    }

    private void navigate(ScreenId screen) {
        instrumentation.runOnMainSync(() -> Navigator.navigateToScreen(context, screen));
    }

    private void assertFeed(HomeActivity home, FeedTab tab) {
        await("Feed tab " + tab, 5000, () -> showsFeed(home, tab));
    }

    private boolean showsFeed(HomeActivity home, FeedTab tab) {
        if (!isAt(home, R.id.nav_home)) {
            return false;
        }
        Fragment current = navHost(home).getChildFragmentManager().getPrimaryNavigationFragment();
        return current instanceof FeedFragment && current.getView() != null
                && feedTabs(home).getSelectedTabPosition() == tab.ordinal()
                && feedViewModel(home).getActiveTabLiveData().getValue() == tab;
    }

    private boolean isAt(HomeActivity home, int destinationId) {
        NavHostFragment navHost = home.isDestroyed() ? null : navHost(home);
        return navHost != null && navHost.getNavController().getCurrentDestination() != null
                && navHost.getNavController().getCurrentDestination().getId() == destinationId;
    }

    private NavHostFragment navHost(HomeActivity home) {
        return (NavHostFragment) home.getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
    }

    private TabLayout feedTabs(HomeActivity home) {
        return home.findViewById(R.id.feed_tab_layout);
    }

    private FeedViewModel feedViewModel(HomeActivity home) {
        Fragment feed = navHost(home).getChildFragmentManager().getPrimaryNavigationFragment();
        return new ViewModelProvider(feed).get(FeedViewModel.class);
    }

    private void await(String description, long timeoutMs, BooleanSupplier condition) {
        AtomicBoolean matched = new AtomicBoolean();
        long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        do {
            instrumentation.runOnMainSync(() -> matched.set(condition.getAsBoolean()));
            if (matched.get()) {
                return;
            }
            SystemClock.sleep(25);
        } while (SystemClock.elapsedRealtime() < deadline);
        assertTrue(description, matched.get());
    }
}
