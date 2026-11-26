package com.socialapp.core.ui.blocks.adapter;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared immutable Glide {@link RequestOptions} registry ensuring 100% cache-key parity between
 * L2 warmup ({@link BlockWarmupEngine} / {@link BlockScrollPreloader}) and render targets
 * ({@link com.socialapp.core.ui.blocks.view.SinglePassFeedPostView} /
 * {@link com.socialapp.core.ui.blocks.view.SinglePassCommentView}).
 */
public final class BlockImageOptions {

    private static final int MAX_CACHE_ENTRIES = 64;
    private static final ConcurrentHashMap<String, RequestOptions> THUMBNAIL_OPTIONS_CACHE =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, RequestOptions> AVATAR_OPTIONS_CACHE =
            new ConcurrentHashMap<>();

    private BlockImageOptions() {
    }

    @NonNull
    public static String buildThumbnailKey(int widthPx, int heightPx, int radiusPx) {
        int safeW = Math.max(1, widthPx);
        int safeH = Math.max(1, heightPx);
        int safeR = Math.max(1, radiusPx);
        return safeW + "x" + safeH + "_r" + safeR;
    }

    @NonNull
    public static String buildAvatarKey(int sizePx) {
        return "avatar_" + Math.max(1, sizePx);
    }

    @NonNull
    public static RequestOptions feedThumbnailOptions(int widthPx, int heightPx, int radiusPx) {
        int safeW = Math.max(1, widthPx);
        int safeH = Math.max(1, heightPx);
        int safeR = Math.max(1, radiusPx);
        String key = buildThumbnailKey(safeW, safeH, safeR);
        RequestOptions cached = THUMBNAIL_OPTIONS_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        if (THUMBNAIL_OPTIONS_CACHE.size() >= MAX_CACHE_ENTRIES) {
            THUMBNAIL_OPTIONS_CACHE.clear();
        }
        RequestOptions created = new RequestOptions()
                .override(safeW, safeH)
                .transform(new CenterCrop(), new RoundedCorners(safeR))
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC);
        RequestOptions existing = THUMBNAIL_OPTIONS_CACHE.putIfAbsent(key, created);
        return existing != null ? existing : created;
    }

    @NonNull
    public static RequestOptions avatarOptions(int sizePx) {
        int safeSize = Math.max(1, sizePx);
        String key = buildAvatarKey(safeSize);
        RequestOptions cached = AVATAR_OPTIONS_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        if (AVATAR_OPTIONS_CACHE.size() >= MAX_CACHE_ENTRIES) {
            AVATAR_OPTIONS_CACHE.clear();
        }
        RequestOptions created = new RequestOptions()
                .override(safeSize, safeSize)
                .circleCrop()
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC);
        RequestOptions existing = AVATAR_OPTIONS_CACHE.putIfAbsent(key, created);
        return existing != null ? existing : created;
    }
}
