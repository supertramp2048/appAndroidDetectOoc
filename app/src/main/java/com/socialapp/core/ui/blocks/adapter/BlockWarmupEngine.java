package com.socialapp.core.ui.blocks.adapter;

import android.content.Context;
import android.content.res.Resources;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.request.RequestOptions;
import com.socialapp.core.ui.blocks.layout.BlockPrecomputer;
import com.socialapp.core.ui.blocks.model.ContentBlock;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coordinates the 3-tier warmup pipeline (Section 4.1.5):
 * - L1 (Worker Thread): pre-shape StaticLayouts & geometries into BlockLayoutCache.
 * - L2 (Glide Preload): decode upcoming thumbnail / cover Bitmaps when idle.
 * - L3 (Main Thread IdleHandler): pre-allocate empty BlockViewHolders into UnifiedBlockViewPool
 *   strictly after the first frame is drawn so cold start (TTFD) is never delayed.
 */
public class BlockWarmupEngine {

    private final BlockPrecomputer blockPrecomputer;
    private final UnifiedBlockViewPool sharedViewPool;
    private final AtomicBoolean memoryPressureActive = new AtomicBoolean(false);
    private final AtomicBoolean l3WarmupCompleted = new AtomicBoolean(false);

    public BlockWarmupEngine(
            @NonNull BlockPrecomputer blockPrecomputer,
            @NonNull UnifiedBlockViewPool sharedViewPool) {
        this.blockPrecomputer = blockPrecomputer;
        this.sharedViewPool = sharedViewPool;
    }

    @NonNull
    public BlockPrecomputer getBlockPrecomputer() {
        return blockPrecomputer;
    }

    @NonNull
    public UnifiedBlockViewPool getSharedViewPool() {
        return sharedViewPool;
    }

    public void setMemoryPressureActive(boolean active) {
        memoryPressureActive.set(active);
        if (!active && sharedViewPool.isTrimmedForPressure()) {
            sharedViewPool.restoreDefaultCapacities();
        }
    }

    public boolean isMemoryPressureActive() {
        return memoryPressureActive.get();
    }

    public boolean isL3WarmupCompleted() {
        return l3WarmupCompleted.get();
    }

    public void resetL3WarmupFlag() {
        l3WarmupCompleted.set(false);
    }

    // --- L1: Off-main-thread layout & text shaping warmup ---

    @Nullable
    public Future<?> warmupL1(@NonNull Context context, @NonNull List<ContentBlock> blocks) {
        if (blocks.isEmpty()) {
            return null;
        }
        Resources res = context.getResources();
        if (res == null) {
            return null;
        }
        DisplayMetrics dm = res.getDisplayMetrics();
        int widthPx = dm != null ? dm.widthPixels : 0;
        float density = dm != null ? dm.density : 1.0f;
        float fontScale = res.getConfiguration() != null ? res.getConfiguration().fontScale : 1.0f;
        return warmupL1(blocks, widthPx, density, fontScale);
    }

    @Nullable
    public Future<?> warmupL1(
            @NonNull List<ContentBlock> blocks,
            int availableWidthPx,
            float density,
            float fontScale) {
        if (blocks.isEmpty() || availableWidthPx <= 0 || memoryPressureActive.get()) {
            return null;
        }
        return blockPrecomputer.precomputeBlocksAsync(blocks, availableWidthPx, density, fontScale);
    }

    // --- L2: Bitmap / Texture cache warmup via Glide preload ---

    public void warmupL2Images(
            @NonNull Context context,
            @NonNull List<String> imageUrls,
            int targetWidthPx,
            int targetHeightPx) {
        if (imageUrls.isEmpty() || memoryPressureActive.get() || targetWidthPx <= 0 || targetHeightPx <= 0) {
            return;
        }
        float density = 1.0f;
        try {
            Resources res = context.getResources();
            if (res != null && res.getDisplayMetrics() != null) {
                density = res.getDisplayMetrics().density;
            }
        } catch (Throwable ignored) {
        }
        int defaultRadiusPx = Math.max(1, BlockPrecomputer.dpToPx(4, density));
        warmupL2FeedThumbnails(context, null, imageUrls, targetWidthPx, targetHeightPx, defaultRadiusPx);
    }

    public void warmupL2FeedThumbnails(
            @NonNull Context context,
            @NonNull List<String> imageUrls,
            int targetWidthPx,
            int targetHeightPx,
            int radiusPx) {
        warmupL2FeedThumbnails(context, null, imageUrls, targetWidthPx, targetHeightPx, radiusPx);
    }

    public void warmupL2FeedThumbnails(
            @NonNull Context context,
            @Nullable RequestManager requestManager,
            @NonNull List<String> imageUrls,
            int targetWidthPx,
            int targetHeightPx,
            int radiusPx) {
        if (imageUrls.isEmpty() || memoryPressureActive.get() || targetWidthPx <= 0 || targetHeightPx <= 0) {
            return;
        }
        RequestOptions options = BlockImageOptions.feedThumbnailOptions(targetWidthPx, targetHeightPx, radiusPx);
        preloadWithOptions(context, requestManager, imageUrls, options, targetWidthPx, targetHeightPx);
    }

    public void warmupL2Avatars(
            @NonNull Context context,
            @NonNull List<String> avatarUrls,
            int sizePx) {
        warmupL2Avatars(context, null, avatarUrls, sizePx);
    }

    public void warmupL2Avatars(
            @NonNull Context context,
            @Nullable RequestManager requestManager,
            @NonNull List<String> avatarUrls,
            int sizePx) {
        if (avatarUrls.isEmpty() || memoryPressureActive.get() || sizePx <= 0) {
            return;
        }
        RequestOptions options = BlockImageOptions.avatarOptions(sizePx);
        preloadWithOptions(context, requestManager, avatarUrls, options, sizePx, sizePx);
    }

    private void preloadWithOptions(
            @NonNull Context context,
            @Nullable RequestManager providedManager,
            @NonNull List<String> urls,
            @NonNull RequestOptions options,
            int widthPx,
            int heightPx) {
        try {
            RequestManager rm = providedManager != null ? providedManager : Glide.with(context.getApplicationContext());
            for (String url : urls) {
                if (url == null || url.trim().isEmpty()) {
                    continue;
                }
                try {
                    rm.load(url)
                            .apply(options)
                            .preload(widthPx, heightPx);
                } catch (Throwable ignored) {
                    // Safe guard when invoked in stripped/test environments or during teardown
                }
            }
        } catch (Throwable ignored) {
        }
    }

    // --- L3: Post-first-frame IdleHandler ViewHolder pre-allocation ---

    public void scheduleL3PoolWarmup(@NonNull View anchorView, @NonNull Runnable preallocateTask) {
        if (l3WarmupCompleted.get() || memoryPressureActive.get()) {
            return;
        }
        anchorView.post(() -> {
            if (l3WarmupCompleted.get() || memoryPressureActive.get()) {
                return;
            }
            Looper.myQueue().addIdleHandler(() -> {
                if (!memoryPressureActive.get() && l3WarmupCompleted.compareAndSet(false, true)) {
                    try {
                        preallocateTask.run();
                    } catch (Throwable ignored) {
                        l3WarmupCompleted.set(false);
                    }
                }
                return false; // Run once
            });
        });
    }
}
