package com.socialapp.feature.home;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.OneShotPreDrawListener;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.socialapp.MyApplication;
import com.socialapp.R;
import com.socialapp.core.di.AppContainer;
import com.socialapp.core.navigation.Navigator;
import com.socialapp.core.prefetch.ScreenId;
import com.socialapp.core.session.SessionManager;
import com.socialapp.core.telemetry.TelemetryActivity;
import com.socialapp.core.telemetry.NavigationTiming;
import com.socialapp.core.ui.RequireLoginBottomSheet;
import com.socialapp.databinding.ActivityHomeBinding;
import com.socialapp.feature.factcheck.AiChatBottomSheet;
import com.socialapp.feature.factcheck.FactCheckTabFragment;
import com.socialapp.feature.feed.FeedFragment;
import com.socialapp.feature.feed.FeedViewModel.FeedTab;
import com.socialapp.feature.feed.SearchFragment;
import com.socialapp.feature.profile.ProfileFragment;

public class HomeActivity extends TelemetryActivity {

    public static final String EXTRA_NAV_DESTINATION = "EXTRA_NAV_DESTINATION";
    public static final String EXTRA_FEED_TAB = "EXTRA_FEED_TAB";
    private static final String STATE_PENDING_FEED_TAB = "pending_feed_tab";
    private static final String STATE_FULLY_DRAWN = "home_fully_drawn";

    private ActivityHomeBinding binding;
    private NavHostFragment navHostFragment;
    private NavController navController;
    private SessionManager sessionManager;
    private FeedTab pendingFeedTab;
    private boolean startupEligible;
    private View pendingStartupView;
    private boolean startupReported;
    private NavigationTiming factCheckTiming;

    private final NavController.OnDestinationChangedListener telemetryDestinationListener =
            (controller, destination, arguments) -> {
                if (destination.getId() != R.id.nav_factcheck) factCheckTiming = null;
                if (destination.getId() != R.id.nav_home) startupEligible = false;
                onHomeScreenChanged();
            };
    private final FragmentManager.FragmentLifecycleCallbacks telemetryFragmentCallbacks =
            new FragmentManager.FragmentLifecycleCallbacks() {
                @Override
                public void onFragmentResumed(@NonNull FragmentManager manager, @NonNull Fragment fragment) {
                    onHomeScreenChanged();
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        startupEligible = !getIntent().hasExtra(EXTRA_NAV_DESTINATION)
                || getIntent().getIntExtra(EXTRA_NAV_DESTINATION, -1) == R.id.nav_home;
        startupReported = savedInstanceState != null && savedInstanceState.getBoolean(STATE_FULLY_DRAWN);
        FeedTab launchTab = savedInstanceState == null ? readFeedTab(getIntent()) : null;
        pendingFeedTab = savedInstanceState == null ? launchTab
                : FeedTab.fromRouteValue(savedInstanceState.getString(STATE_PENDING_FEED_TAB));
        super.onCreate(savedInstanceState);
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = MyApplication.getAppContainer(this).getSessionManager();

        navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(binding.bottomNavigation, navController);
            binding.bottomNavigation.setOnItemSelectedListener(item -> {
                beginFactCheckMeasurement(item.getItemId());
                boolean navigated = NavigationUI.onNavDestinationSelected(item, navController);
                if (!navigated && item.getItemId() == R.id.nav_factcheck) factCheckTiming = null;
                return navigated;
            });
            navController.addOnDestinationChangedListener(telemetryDestinationListener);
            navHostFragment.getChildFragmentManager()
                    .registerFragmentLifecycleCallbacks(telemetryFragmentCallbacks, false);
        }

        if (savedInstanceState == null) {
            handleNavigationIntent(getIntent(), launchTab != null);
        }

        binding.fabCreatePost.setOnClickListener(v -> {
            if (sessionManager.isLoggedIn()) {
                Navigator.navigateToCreatePost(this, null, null, null);
            } else {
                RequireLoginBottomSheet bottomSheet = RequireLoginBottomSheet.newInstance();
                bottomSheet.setOnLoginClickListener(() -> Navigator.navigateTo(this, Navigator.ROUTE_LOGIN));
                bottomSheet.show(getSupportFragmentManager(), "RequireLogin");
            }
        });

        binding.fabAiAssistant.setOnClickListener(v -> {
            AiChatBottomSheet bottomSheet = AiChatBottomSheet.newInstanceForSearch();
            bottomSheet.show(getSupportFragmentManager(), "AiChatBottomSheet");
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        pendingFeedTab = readFeedTab(intent);
        handleNavigationIntent(intent, pendingFeedTab != null);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_FULLY_DRAWN, startupReported || !startupEligible);
        if (pendingFeedTab != null) {
            outState.putString(STATE_PENDING_FEED_TAB, pendingFeedTab.getRouteValue());
        }
    }

    @Nullable
    public FeedTab consumeFeedTabRequest() {
        FeedTab tab = pendingFeedTab;
        pendingFeedTab = null;
        return tab;
    }

    @Nullable
    private FeedTab readFeedTab(@Nullable Intent intent) {
        if (intent == null || intent.getIntExtra(EXTRA_NAV_DESTINATION, -1) != R.id.nav_home) {
            return null;
        }
        return FeedTab.fromRouteValue(intent.getStringExtra(EXTRA_FEED_TAB));
    }

    private void handleNavigationIntent(Intent intent, boolean hasFeedTabRequest) {
        if (intent != null && intent.hasExtra(EXTRA_NAV_DESTINATION) && navController != null) {
            int destId = intent.getIntExtra(EXTRA_NAV_DESTINATION, -1);
            if (hasFeedTabRequest && navController.getCurrentDestination() != null
                    && navController.getCurrentDestination().getId() == R.id.nav_home) {
                Fragment current = navHostFragment.getChildFragmentManager().getPrimaryNavigationFragment();
                if (current instanceof FeedFragment
                        && current.getView() != null
                        && current.getViewLifecycleOwner().getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)
                        && pendingFeedTab != null) {
                    ((FeedFragment) current).applyRequestedTab(consumeFeedTabRequest());
                }
                return;
            }
            if (destId != -1) {
                beginFactCheckMeasurement(destId);
                try {
                    navController.navigate(destId);
                } catch (Exception ignored) {
                    pendingFeedTab = null;
                    factCheckTiming = null;
                }
            }
        }
    }

    public void onHomeScreenChanged() {
        MyApplication.getAppContainer(this).getNavigationObserver().onHomeScreenChanged(this);
    }

    public void onFeedContentBound(FeedFragment feed) {
        if (!startupEligible || startupReported || !isCurrentFeedForTelemetry(feed)
                || !feed.isResumed() || !feed.isSuccessfulContentBound()) return;
        View view = feed.getView();
        if (view == null || view == pendingStartupView || !view.isAttachedToWindow()) return;
        pendingStartupView = view;
        OneShotPreDrawListener.add(view, () -> view.post(() -> {
            if (pendingStartupView == view) pendingStartupView = null;
            ScreenId screen = feed.getActiveScreenId();
            if (!startupEligible || startupReported || isFinishing() || isDestroyed() || !view.isShown()
                    || !feed.isResumed() || feed.getView() != view || !isCurrentFeedForTelemetry(feed)
                    || screen == null || getVisibleScreenForTelemetry() != screen || !feed.hasLaidOutContent()) return;
            startupReported = true;
            reportFullyDrawn();
            MyApplication.getAppContainer(this).getPerformanceTelemetry()
                    .homeFullyDrawn(screen, SystemClock.elapsedRealtime());
        }));
        view.invalidate();
    }

    public void onFeedViewDestroyed(FeedFragment owner) {
        if (pendingStartupView == owner.getView()) pendingStartupView = null;
    }

    private void beginFactCheckMeasurement(int destination) {
        if (destination != R.id.nav_factcheck || navController.getCurrentDestination() == null
                || navController.getCurrentDestination().getId() == R.id.nav_factcheck) return;
        factCheckTiming = new NavigationTiming(SystemClock.elapsedRealtime());
    }

    public void onFactCheckContentBound(FactCheckTabFragment fragment, View view) {
        NavigationTiming timing = factCheckTiming;
        if (timing == null) return;
        OneShotPreDrawListener.add(view, () -> view.post(() -> {
            if (timing != factCheckTiming || !fragment.isResumed() || fragment.getView() != view || !view.isShown()
                    || isFinishing() || isDestroyed() || navHostFragment.getChildFragmentManager()
                    .getPrimaryNavigationFragment() != fragment || getVisibleScreenForTelemetry() != ScreenId.FACT_CHECK) return;
            long now = SystemClock.elapsedRealtime();
            if (!timing.markReady(now)) return;
            factCheckTiming = null;
            AppContainer app = MyApplication.getAppContainer(this);
            app.getPerformanceTelemetry().contentReady(ScreenId.FACT_CHECK, timing,
                    app.getNavigationObserver().getContentNavigation(this, ScreenId.FACT_CHECK, timing.startedAtMs), now);
        }));
        view.invalidate();
    }

    public boolean isCurrentFeedForTelemetry(FeedFragment owner) {
        return navHostFragment != null
                && navHostFragment.getChildFragmentManager().getPrimaryNavigationFragment() == owner;
    }

    @Nullable
    public ScreenId getVisibleScreenForTelemetry() {
        if (navController == null) {
            return null;
        }
        NavDestination destination = navController.getCurrentDestination();
        Fragment fragment = navHostFragment.getChildFragmentManager().getPrimaryNavigationFragment();
        if (destination == null || fragment == null || fragment.getView() == null
                || fragment.getViewLifecycleOwner().getLifecycle().getCurrentState() != Lifecycle.State.RESUMED) {
            return null;
        }

        int destinationId = destination.getId();
        if (destinationId == R.id.nav_home && fragment instanceof FeedFragment) {
            return ((FeedFragment) fragment).getActiveScreenId();
        }
        if (destinationId == R.id.nav_search && fragment instanceof SearchFragment) return ScreenId.SEARCH;
        if (destinationId == R.id.nav_profile && fragment instanceof ProfileFragment) return ScreenId.PROFILE;
        if (destinationId == R.id.nav_factcheck && fragment instanceof FactCheckTabFragment) return ScreenId.FACT_CHECK;
        return null;
    }

    @Override
    protected void onDestroy() {
        if (navController != null) {
            navController.removeOnDestinationChangedListener(telemetryDestinationListener);
            navHostFragment.getChildFragmentManager().unregisterFragmentLifecycleCallbacks(telemetryFragmentCallbacks);
        }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        com.socialapp.core.prefetch.PrefetchManager.getInstance()
                .onScreenVisited(com.socialapp.core.network.sim.ScreenPayloadConfig.SCREEN_HOME);
    }
}
