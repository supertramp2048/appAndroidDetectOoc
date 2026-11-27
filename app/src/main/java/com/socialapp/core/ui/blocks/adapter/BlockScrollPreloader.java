package com.socialapp.core.ui.blocks.adapter;

import android.content.Context;
import android.content.res.Resources;
import android.util.DisplayMetrics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.RequestManager;
import com.socialapp.core.ui.blocks.layout.BlockPrecomputer;
import com.socialapp.core.ui.blocks.model.CommentBlock;
import com.socialapp.core.ui.blocks.model.ContentBlock;
import com.socialapp.core.ui.blocks.model.FeedPostBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * Sliding-window L1/L2 warmup scroll listener with velocity-aware Glide pause/resume
 * for 120 Hz scroll smoothness.
 */
public class BlockScrollPreloader extends RecyclerView.OnScrollListener {

    public interface BlockListProvider {
        @NonNull
        List<ContentBlock> getBlocks();
    }

    public static final int DEFAULT_LOOKAHEAD_COUNT = 10;
    private static final float PAUSE_THRESHOLD_DP_PER_EVENT = 140f;
    private static final float RESUME_THRESHOLD_DP_PER_EVENT = 45f;

    @NonNull
    private final BlockWarmupEngine warmupEngine;
    @NonNull
    private final BlockListProvider blockListProvider;
    private final int lookaheadCount;

    @Nullable
    private RequestManager requestManager;
    private int lastPrefetchStart = -1;
    private int lastPrefetchEnd = -1;
    private boolean requestsPaused = false;

    public BlockScrollPreloader(
            @NonNull BlockWarmupEngine warmupEngine,
            @NonNull BlockListProvider blockListProvider) {
        this(warmupEngine, blockListProvider, DEFAULT_LOOKAHEAD_COUNT);
    }

    public BlockScrollPreloader(
            @NonNull BlockWarmupEngine warmupEngine,
            @NonNull BlockListProvider blockListProvider,
            int lookaheadCount) {
        this.warmupEngine = warmupEngine;
        this.blockListProvider = blockListProvider;
        this.lookaheadCount = Math.max(1, lookaheadCount);
    }

    public void setRequestManager(@Nullable RequestManager requestManager) {
        if (this.requestManager != null && this.requestManager != requestManager) {
            resumeMediaIfPaused();
        }
        this.requestManager = requestManager;
    }

    public void resetWindow() {
        lastPrefetchStart = -1;
        lastPrefetchEnd = -1;
    }

    public void detachCleanup() {
        resumeMediaIfPaused();
        resetWindow();
        this.requestManager = null;
    }

    /**
     * Pure window calculation: returns [startInclusive, endExclusive], or [0, 0] when nothing to prefetch.
     */
    @NonNull
    public static int[] computePrefetchRange(
            int firstVisible,
            int lastVisible,
            int totalCount,
            int dy,
            int lookaheadCount) {
        if (totalCount <= 0 || lookaheadCount <= 0) {
            return new int[]{0, 0};
        }
        if (firstVisible < 0
                || lastVisible < 0
                || firstVisible >= totalCount
                || lastVisible >= totalCount
                || firstVisible > lastVisible) {
            return new int[]{0, 0};
        }
        if (dy >= 0) {
            int start = Math.min(totalCount, lastVisible + 1);
            int end = Math.min(totalCount, start + lookaheadCount);
            return end > start ? new int[]{start, end} : new int[]{0, 0};
        } else {
            int end = Math.max(0, firstVisible);
            int start = Math.max(0, end - lookaheadCount);
            return end > start ? new int[]{start, end} : new int[]{0, 0};
        }
    }

    /**
     * Returns true during high-velocity ballistic fling settling when decoding/uploading new bitmaps
     * would jeopardize the 8.33 ms RenderThread frame budget.
     */
    public static boolean shouldPauseMediaForFling(
            int scrollState,
            int absDyPx,
            int velocityPauseThresholdPx) {
        return scrollState == RecyclerView.SCROLL_STATE_SETTLING
                && velocityPauseThresholdPx > 0
                && absDyPx >= velocityPauseThresholdPx;
    }

    @Override
    public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
        if (newState == RecyclerView.SCROLL_STATE_IDLE || newState == RecyclerView.SCROLL_STATE_DRAGGING) {
            resumeMediaIfPaused();
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                prefetchAroundViewport(recyclerView, 1);
            }
        }
    }

    @Override
    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
        if (dy == 0) {
            return;
        }
        Resources res = recyclerView.getResources();
        DisplayMetrics dm = res != null ? res.getDisplayMetrics() : null;
        float density = dm != null ? dm.density : 1.0f;

        int pauseThresholdPx = Math.max(1, Math.round(PAUSE_THRESHOLD_DP_PER_EVENT * density));
        int resumeThresholdPx = Math.max(1, Math.round(RESUME_THRESHOLD_DP_PER_EVENT * density));
        int absDy = Math.abs(dy);
        int scrollState = recyclerView.getScrollState();

        if (shouldPauseMediaForFling(scrollState, absDy, pauseThresholdPx)) {
            pauseMediaIfNeeded();
        } else if (requestsPaused && (scrollState != RecyclerView.SCROLL_STATE_SETTLING || absDy <= resumeThresholdPx)) {
            resumeMediaIfPaused();
        }

        prefetchAroundViewport(recyclerView, dy);
    }

    private void prefetchAroundViewport(@NonNull RecyclerView recyclerView, int dy) {
        RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
        if (!(lm instanceof LinearLayoutManager)) {
            return;
        }
        LinearLayoutManager linearLm = (LinearLayoutManager) lm;
        List<ContentBlock> blocks = blockListProvider.getBlocks();
        if (blocks.isEmpty()) {
            return;
        }

        int firstVisible = linearLm.findFirstVisibleItemPosition();
        int lastVisible = linearLm.findLastVisibleItemPosition();
        int[] range = computePrefetchRange(firstVisible, lastVisible, blocks.size(), dy, lookaheadCount);
        int start = range[0];
        int end = range[1];
        if (end <= start || (start == lastPrefetchStart && end == lastPrefetchEnd)) {
            return;
        }
        lastPrefetchStart = start;
        lastPrefetchEnd = end;

        Context ctx = recyclerView.getContext();
        Resources res = recyclerView.getResources();
        if (ctx == null || res == null) {
            return;
        }
        DisplayMetrics dm = res.getDisplayMetrics();
        float density = dm != null ? dm.density : 1.0f;
        float fontScale = res.getConfiguration() != null ? res.getConfiguration().fontScale : 1.0f;
        int availWidth = resolveRecyclerWidth(recyclerView, dm);
        if (availWidth <= 0) {
            return;
        }

        List<ContentBlock> slice = new ArrayList<>(end - start);
        for (int i = start; i < end; i++) {
            slice.add(blocks.get(i));
        }

        warmupEngine.warmupL1(slice, availWidth, density, fontScale);

        if (requestsPaused || warmupEngine.isMemoryPressureActive()) {
            return;
        }

        List<String> thumbUrls = null;
        List<String> feedAvatarUrls = null;
        List<String> commentAvatarUrls = null;

        for (int i = 0; i < slice.size(); i++) {
            ContentBlock b = slice.get(i);
            if (b instanceof FeedPostBlock) {
                FeedPostBlock fp = (FeedPostBlock) b;
                if (fp.getThumbnailUrl() != null) {
                    if (thumbUrls == null) thumbUrls = new ArrayList<>(slice.size());
                    thumbUrls.add(fp.getThumbnailUrl());
                }
                if (fp.getAuthorName() != null && fp.getAuthorAvatarUrl() != null) {
                    if (feedAvatarUrls == null) feedAvatarUrls = new ArrayList<>(slice.size());
                    feedAvatarUrls.add(fp.getAuthorAvatarUrl());
                }
            } else if (b instanceof CommentBlock) {
                CommentBlock cb = (CommentBlock) b;
                if (cb.getAuthorAvatarUrl() != null) {
                    if (commentAvatarUrls == null) commentAvatarUrls = new ArrayList<>(slice.size());
                    commentAvatarUrls.add(cb.getAuthorAvatarUrl());
                }
            }
        }

        if (thumbUrls != null) {
            int w = BlockPrecomputer.dpToPx(BlockPrecomputer.FEED_THUMB_WIDTH_DP, density);
            int h = BlockPrecomputer.dpToPx(BlockPrecomputer.FEED_THUMB_HEIGHT_DP, density);
            int radiusPx = Math.max(1, BlockPrecomputer.dpToPx(4, density));
            warmupEngine.warmupL2FeedThumbnails(ctx, requestManager, thumbUrls, w, h, radiusPx);
        }
        if (feedAvatarUrls != null) {
            int size = BlockPrecomputer.dpToPx(BlockPrecomputer.FEED_AVATAR_SIZE_DP, density);
            warmupEngine.warmupL2Avatars(ctx, requestManager, feedAvatarUrls, size);
        }
        if (commentAvatarUrls != null) {
            int size = BlockPrecomputer.dpToPx(BlockPrecomputer.COMMENT_AVATAR_SIZE_DP, density);
            warmupEngine.warmupL2Avatars(ctx, requestManager, commentAvatarUrls, size);
        }
    }

    private void pauseMediaIfNeeded() {
        if (!requestsPaused && requestManager != null) {
            try {
                if (!requestManager.isPaused()) {
                    requestManager.pauseRequests();
                }
                requestsPaused = true;
            } catch (Throwable ignored) {
            }
        }
    }

    private void resumeMediaIfPaused() {
        if (requestsPaused && requestManager != null) {
            try {
                if (requestManager.isPaused()) {
                    requestManager.resumeRequests();
                }
            } catch (Throwable ignored) {
            }
        }
        requestsPaused = false;
    }

    private static int resolveRecyclerWidth(@NonNull RecyclerView rv, @Nullable DisplayMetrics dm) {
        if (rv.getWidth() > 0) {
            return Math.max(1, rv.getWidth() - rv.getPaddingLeft() - rv.getPaddingRight());
        }
        return dm != null ? dm.widthPixels : 0;
    }
}
