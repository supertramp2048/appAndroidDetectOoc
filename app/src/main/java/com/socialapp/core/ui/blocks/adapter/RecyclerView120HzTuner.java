package com.socialapp.core.ui.blocks.adapter;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

/**
 * Applies uniform 120 Hz RecyclerView configuration across all reading-path lists:
 * fixed size optimization (guarded against WRAP_CONTENT), extended view cache,
 * GapWorker initial prefetching, animation suppression where appropriate,
 * and API 35+ scroll frame-rate boosting.
 */
public final class RecyclerView120HzTuner {

    private static final int READING_VIEW_CACHE_SIZE = 6;
    private static final int INITIAL_PREFETCH_ITEM_COUNT = 6;

    private RecyclerView120HzTuner() {
    }

    public static void tuneReadingList(
            @NonNull RecyclerView recyclerView,
            @NonNull LinearLayoutManager layoutManager,
            boolean disableItemAnimator) {
        ViewGroup.LayoutParams lp = recyclerView.getLayoutParams();
        boolean isFixedSizeSafe = lp != null
                && lp.width != ViewGroup.LayoutParams.WRAP_CONTENT
                && lp.height != ViewGroup.LayoutParams.WRAP_CONTENT;
        recyclerView.setHasFixedSize(isFixedSizeSafe);
        recyclerView.setItemViewCacheSize(READING_VIEW_CACHE_SIZE);

        layoutManager.setRecycleChildrenOnDetach(true);
        layoutManager.setItemPrefetchEnabled(true);
        layoutManager.setInitialPrefetchItemCount(INITIAL_PREFETCH_ITEM_COUNT);

        if (disableItemAnimator) {
            recyclerView.setItemAnimator(null);
        } else {
            RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
            if (animator instanceof SimpleItemAnimator) {
                ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
            }
        }

        HighRefreshRateHelper.attachScrollFrameRateBoost(recyclerView);
    }
}
