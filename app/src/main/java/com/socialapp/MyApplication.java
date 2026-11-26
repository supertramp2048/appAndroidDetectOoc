package com.socialapp;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.socialapp.core.di.AppContainer;
import com.socialapp.core.prefetch.ui.GlobalDemoOverlayManager;
import com.socialapp.core.ui.blocks.adapter.BlockWarmupEngine;
import com.socialapp.core.ui.blocks.adapter.UnifiedBlockViewPool;
import com.socialapp.core.ui.blocks.editor.EditorWebViewPool;
import com.socialapp.core.ui.blocks.layout.BlockLayoutCache;

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
                boolean returningToForeground = (startedActivityCount == 0);
                startedActivityCount++;
                if (returningToForeground && appContainer != null) {
                    appContainer.getBlockWarmupEngine().setMemoryPressureActive(false);
                }
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

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (appContainer == null) {
            return;
        }
        dispatchMemoryTrim(
                level,
                appContainer.getBlockLayoutCache(),
                appContainer.getUnifiedBlockViewPool(),
                appContainer.getBlockWarmupEngine(),
                appContainer.getEditorWebViewPool(),
                this
        );
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (appContainer == null) {
            return;
        }
        dispatchMemoryTrim(
                ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
                appContainer.getBlockLayoutCache(),
                appContainer.getUnifiedBlockViewPool(),
                appContainer.getBlockWarmupEngine(),
                appContainer.getEditorWebViewPool(),
                this
        );
    }

    /**
     * Legacy 5-arg entry point preserved for existing unit tests.
     */
    public static void dispatchMemoryTrim(
            int level,
            @NonNull BlockLayoutCache l1Cache,
            @NonNull UnifiedBlockViewPool l3Pool,
            @NonNull BlockWarmupEngine warmupEngine,
            @androidx.annotation.Nullable Context glideContext) {
        dispatchMemoryTrim(level, l1Cache, l3Pool, warmupEngine, null, glideContext);
    }

    /**
     * Memory-pressure degradation handler shared across onTrimMemory() / onLowMemory()
     * and pure-JVM unit tests (passing null context skips Glide calls in JVM).
     */
    public static void dispatchMemoryTrim(
            int level,
            @NonNull BlockLayoutCache l1Cache,
            @NonNull UnifiedBlockViewPool l3Pool,
            @NonNull BlockWarmupEngine warmupEngine,
            @androidx.annotation.Nullable EditorWebViewPool editorPool,
            @androidx.annotation.Nullable Context glideContext) {

        if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
                || level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) {
            warmupEngine.setMemoryPressureActive(true);
            warmupEngine.resetL3WarmupFlag();
            l1Cache.clear();
            l3Pool.clear();
            if (editorPool != null) {
                editorPool.trimMemory(level);
            }
            if (glideContext instanceof MyApplication && ((MyApplication) glideContext).appContainer != null) {
                ((MyApplication) glideContext).appContainer.getArticleAstCache().clear();
            }
            if (glideContext != null) {
                runOnMain(() -> {
                    try {
                        Glide.get(glideContext).clearMemory();
                    } catch (Throwable ignored) {
                    }
                });
            }
            return;
        }

        if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
                || level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
                || level == ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
                || level == ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            warmupEngine.setMemoryPressureActive(true);
            warmupEngine.resetL3WarmupFlag();
            l1Cache.trimToFraction(0.25f);
            l3Pool.clear();
            if (editorPool != null) {
                editorPool.trimMemory(level);
            }
            if (glideContext != null) {
                runOnMain(() -> {
                    try {
                        Glide.get(glideContext).trimMemory(level);
                    } catch (Throwable ignored) {
                    }
                });
            }
            return;
        }

        if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE) {
            l1Cache.trimToFraction(0.5f);
            l3Pool.trimForMemoryPressure();
        }
    }

    private static void runOnMain(@NonNull Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            new Handler(Looper.getMainLooper()).post(action);
        }
    }
}
