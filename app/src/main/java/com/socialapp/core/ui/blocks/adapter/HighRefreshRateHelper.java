package com.socialapp.core.ui.blocks.adapter;

import android.app.Activity;
import android.os.Build;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Coordinates 90/120 Hz LTPO display mode selection (API 30+) and scroll-triggered
 * frame-rate category boosting (API 35+).
 */
public final class HighRefreshRateHelper {

    private static final float BASE_REFRESH_HZ = 60.0f;
    private static final float MIN_REFRESH_DELTA_HZ = 1.0f;

    private HighRefreshRateHelper() {
    }

    /**
     * Pure JVM-testable mode selector: returns the modeId with the highest refresh rate (> 60 Hz)
     * whose physical resolution matches (currentWidth, currentHeight), or 0 if none qualifies.
     */
    public static int selectHighestRefreshModeId(
            int currentWidth,
            int currentHeight,
            @NonNull int[] modeIds,
            @NonNull int[] widths,
            @NonNull int[] heights,
            @NonNull float[] refreshRates) {
        int count = Math.min(Math.min(modeIds.length, widths.length),
                Math.min(heights.length, refreshRates.length));
        int bestModeId = 0;
        float bestRate = BASE_REFRESH_HZ + MIN_REFRESH_DELTA_HZ;

        for (int i = 0; i < count; i++) {
            if (widths[i] != currentWidth || heights[i] != currentHeight) {
                continue;
            }
            if (refreshRates[i] >= bestRate) {
                bestRate = refreshRates[i];
                bestModeId = modeIds[i];
            }
        }
        return bestModeId;
    }

    public static void requestHighRefreshRate(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return;
        }
        try {
            Display display = activity.getDisplay();
            Window window = activity.getWindow();
            if (display == null || window == null) {
                return;
            }
            Display.Mode current = display.getMode();
            Display.Mode[] supported = display.getSupportedModes();
            if (current == null || supported == null || supported.length == 0) {
                return;
            }
            int len = supported.length;
            int[] ids = new int[len];
            int[] widths = new int[len];
            int[] heights = new int[len];
            float[] rates = new float[len];
            for (int i = 0; i < len; i++) {
                Display.Mode mode = supported[i];
                ids[i] = mode.getModeId();
                widths[i] = mode.getPhysicalWidth();
                heights[i] = mode.getPhysicalHeight();
                rates[i] = mode.getRefreshRate();
            }
            int preferredId = selectHighestRefreshModeId(
                    current.getPhysicalWidth(),
                    current.getPhysicalHeight(),
                    ids,
                    widths,
                    heights,
                    rates
            );
            if (preferredId != 0 && preferredId != current.getModeId()) {
                WindowManager.LayoutParams lp = window.getAttributes();
                lp.preferredDisplayModeId = preferredId;
                window.setAttributes(lp);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void attachScrollFrameRateBoost(@NonNull RecyclerView recyclerView) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return;
        }
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    return;
                }
                try {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING
                            || newState == RecyclerView.SCROLL_STATE_SETTLING) {
                        rv.setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_HIGH);
                    } else {
                        rv.setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT);
                    }
                } catch (Throwable ignored) {
                }
            }
        });
    }
}
