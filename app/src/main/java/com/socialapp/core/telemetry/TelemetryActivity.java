package com.socialapp.core.telemetry;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.socialapp.MyApplication;
import com.socialapp.core.navigation.Navigator;

/** Forwards user interaction on the Activity surfaces in the navigation study. */
public abstract class TelemetryActivity extends AppCompatActivity {
    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        if (intent.hasExtra(Navigator.EXTRA_REPLAY_REQUEST_ID)) {
            setIntent(intent);
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        MyApplication.getAppContainer(this).getNavigationObserver().onUserInteraction(this);
    }
}
