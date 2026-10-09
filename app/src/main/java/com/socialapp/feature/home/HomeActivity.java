package com.socialapp.feature.home;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.socialapp.MyApplication;
import com.socialapp.R;
import com.socialapp.core.navigation.Navigator;
import com.socialapp.core.session.SessionManager;
import com.socialapp.core.ui.RequireLoginBottomSheet;
import com.socialapp.databinding.ActivityHomeBinding;

public class HomeActivity extends AppCompatActivity {

    private ActivityHomeBinding binding;
    private NavController navController;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionManager = MyApplication.getAppContainer(this).getSessionManager();

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(binding.bottomNavigation, navController);
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
    }
}
