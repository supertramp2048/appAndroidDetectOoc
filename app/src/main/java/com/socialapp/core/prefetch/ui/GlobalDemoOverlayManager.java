package com.socialapp.core.prefetch.ui;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.socialapp.MyApplication;
import com.socialapp.core.di.AppContainer;
import com.socialapp.core.prefetch.ReplayEngine;
import com.socialapp.core.prefetch.ScreenId;

/**
 * Quản lý đính kèm thanh Demo Overlay toàn cục lên mọi Activity đang hiển thị.
 */
public class GlobalDemoOverlayManager {

    private static final String TAG_PREFETCH_OVERLAY = "TAG_PREFETCH_OVERLAY";
    private static boolean isEnabled = true;
    private static PrefetchDemoOverlayView currentActiveOverlay = null;

    public static void init(@NonNull Application application) {
        AppContainer container = MyApplication.getAppContainer(application);

        // Đăng ký nhận thông báo từ MetricsLogger và PrefetchEngine để cập nhật UI thời gian thực
        container.getMetricsLogger().setOnMetricsUpdatedListener((totalSteps, hitCount, missCount, lastLatencyMs, totalWastedBytes) -> {
            if (currentActiveOverlay != null) {
                currentActiveOverlay.refreshUI();
            }
        });

        container.getPrefetchEngine().setPredictionListener((currentScreen, predictions, policy) -> {
            if (currentActiveOverlay != null) {
                currentActiveOverlay.refreshUI();
            }
        });

        container.getReplayEngine().setListener(new ReplayEngine.OnReplayProgressListener() {
            @Override
            public void onProgress(int currentSession, int totalSessions, int currentStep, int totalSteps, ScreenId screen) {
                if (currentActiveOverlay != null) {
                    currentActiveOverlay.refreshUI();
                }
            }

            @Override
            public void onSessionCompleted(int sessionId, int totalSessions) {
                if (currentActiveOverlay != null) {
                    currentActiveOverlay.refreshUI();
                }
            }

            @Override
            public void onReplayFinished() {
                if (currentActiveOverlay != null) {
                    currentActiveOverlay.refreshUI();
                }
            }

            @Override
            public void onReplayStopped() {
                if (currentActiveOverlay != null) {
                    currentActiveOverlay.refreshUI();
                }
            }
        });

        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                if (!isEnabled) return;
                attachOverlay(activity);
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
                if (currentActiveOverlay != null && currentActiveOverlay.getContext() == activity) {
                    currentActiveOverlay = null;
                }
            }

            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}
            @Override
            public void onActivityStarted(@NonNull Activity activity) {}
            @Override
            public void onActivityStopped(@NonNull Activity activity) {}
            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}
            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {}
        });
    }

    private static void attachOverlay(@NonNull Activity activity) {
        FrameLayout content = activity.findViewById(android.R.id.content);
        if (content == null) return;

        View existing = content.findViewWithTag(TAG_PREFETCH_OVERLAY);
        if (existing instanceof PrefetchDemoOverlayView) {
            currentActiveOverlay = (PrefetchDemoOverlayView) existing;
            currentActiveOverlay.refreshUI();
            return;
        }

        PrefetchDemoOverlayView overlayView = new PrefetchDemoOverlayView(activity);
        overlayView.setTag(TAG_PREFETCH_OVERLAY);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.BOTTOM;
        // Chừa một chút margin đáy nếu cần
        params.bottomMargin = 0;

        content.addView(overlayView, params);
        currentActiveOverlay = overlayView;
        overlayView.refreshUI();
    }

    public static void setEnabled(boolean enabled) {
        isEnabled = enabled;
        if (!enabled && currentActiveOverlay != null) {
            currentActiveOverlay.setVisibility(View.GONE);
        } else if (enabled && currentActiveOverlay != null) {
            currentActiveOverlay.setVisibility(View.VISIBLE);
            currentActiveOverlay.refreshUI();
        }
    }

    public static boolean isEnabled() {
        return isEnabled;
    }
}

