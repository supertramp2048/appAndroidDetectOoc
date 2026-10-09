package com.socialapp.feature.auth;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.socialapp.R;

public class AuthActivity extends AppCompatActivity {

    public static final String EXTRA_SCREEN = "EXTRA_SCREEN";
    public static final String SCREEN_LOGIN = "SCREEN_LOGIN";
    public static final String SCREEN_REGISTER = "SCREEN_REGISTER";
    public static final String SCREEN_FORGOT_PASSWORD = "SCREEN_FORGOT_PASSWORD";
    public static final String SCREEN_RESET_PASSWORD = "SCREEN_RESET_PASSWORD";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        if (savedInstanceState == null) {
            handleIntent(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        Uri data = intent != null ? intent.getData() : null;
        if (data != null && "socialapp".equalsIgnoreCase(data.getScheme())) {
            if ("reset-password".equals(data.getHost())) {
                String token = data.getQueryParameter("token");
                String email = data.getQueryParameter("email");
                openFragment(ResetPasswordFragment.newInstance(token, email));
                return;
            }
        }

        String screen = intent != null ? intent.getStringExtra(EXTRA_SCREEN) : null;
        if (SCREEN_REGISTER.equals(screen)) {
            openFragment(new RegisterFragment());
        } else if (SCREEN_FORGOT_PASSWORD.equals(screen)) {
            openFragment(new ForgotPasswordFragment());
        } else if (SCREEN_RESET_PASSWORD.equals(screen)) {
            openFragment(new ResetPasswordFragment());
        } else {
            openFragment(new LoginFragment());
        }
    }

    public void openFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.auth_container, fragment)
                .commit();
    }
}
