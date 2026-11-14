package com.socialapp;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.socialapp.core.di.AppContainer;
import com.socialapp.core.prefetch.ui.GlobalDemoOverlayManager;

public class MyApplication extends Application {

    private static MyApplication instance;
    private static int startedActivityCount = 0;
    private AppContainer appContainer;

    public static MyApplication getInstance() {
        return instance;
    }

    public static boolean isAppInForeground() {
        return startedActivityCount > 0;
    }

    public static AppContainer getAppContainer(@NonNull Context context) {
        return ((MyApplication) context.getApplicationContext()).appContainer;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        appContainer = new AppContainer(this);
        GlobalDemoOverlayManager.init(this);

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityStarted(Activity activity) {
                startedActivityCount++;
            }

            @Override
            public void onActivityStopped(Activity activity) {
                startedActivityCount = Math.max(0, startedActivityCount - 1);
            }

            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityResumed(Activity activity) {
            }

            @Override
            public void onActivityPaused(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
            }
        });
    }

    public AppContainer getAppContainer() {
        return appContainer;
    }
}
