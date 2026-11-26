package com.socialapp.feature.home;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.socialapp.MyApplication;
import com.socialapp.R;
import com.socialapp.core.di.AppContainer;
import com.socialapp.core.navigation.Navigator;
import com.socialapp.core.session.SessionManager;
import com.socialapp.core.ui.RequireLoginBottomSheet;
import com.socialapp.core.ui.blocks.adapter.HighRefreshRateHelper;
import com.socialapp.databinding.ActivityHomeBinding;
import com.socialapp.feature.factcheck.AiChatBottomSheet;
import com.socialapp.feature.feed.FeedAdapter;

public class HomeActivity extends AppCompatActivity {

    public static final String EXTRA_NAV_DESTINATION = "EXTRA_NAV_DESTINATION";

    private ActivityHomeBinding binding;
    private NavController navController;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        HighRefreshRateHelper.requestHighRefreshRate(this);
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = MyApplication.getAppContainer(this).getSessionManager();

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(binding.bottomNavigation, navController);
        }

        handleNavigationIntent(getIntent());

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

        AppContainer container = MyApplication.getAppContainer(this);
        // L3 ViewHolder pool warmup only — keep Reading Path (Feed/PostDetail/Comments) strictly
        // 0-WebView; EditorWebViewPool warms lazily on first ComposeActivity session and stays pooled.
        container.getBlockWarmupEngine().scheduleL3PoolWarmup(
                binding.getRoot(),
                () -> {
                    FeedAdapter.preallocatePostHolders(this, container.getUnifiedBlockViewPool(), 4);
                    FeedAdapter.preallocateAdHolders(this, container.getUnifiedBlockViewPool(), 2);
                }
        );
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNavigationIntent(intent);
    }

    private void handleNavigationIntent(Intent intent) {
        if (intent != null && intent.hasExtra(EXTRA_NAV_DESTINATION) && navController != null) {
            int destId = intent.getIntExtra(EXTRA_NAV_DESTINATION, -1);
            if (destId != -1) {
                try {
                    navController.navigate(destId);
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }
}
