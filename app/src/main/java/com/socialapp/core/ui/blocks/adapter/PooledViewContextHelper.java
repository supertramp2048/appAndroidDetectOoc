package com.socialapp.core.ui.blocks.adapter;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.ContextThemeWrapper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.socialapp.R;

/**
 * Provides a non-leaking themed {@code ApplicationContext} for views stored inside
 * app-wide {@link UnifiedBlockViewPool} and resolves Glide {@link RequestManager}
 * instances safely without throwing {@code IllegalArgumentException} if an Activity
 * context has already been destroyed.
 */
public final class PooledViewContextHelper {

    @Nullable
    private static Context cachedBaseAppContext;
    @Nullable
    private static Context cachedThemedAppContext;

    private PooledViewContextHelper() {
    }

    /**
     * Returns an ApplicationContext wrapped with {@link R.style#Theme_SocialApp} so pooled
     * views retain Material/AppCompat styling without holding any Activity reference.
     */
    @NonNull
    public static Context getThemedApplicationContext(@NonNull Context context) {
        Context appCtx = context.getApplicationContext();
        if (appCtx == null) {
            appCtx = context;
        }
        if (cachedThemedAppContext != null && cachedBaseAppContext == appCtx) {
            return cachedThemedAppContext;
        }
        synchronized (PooledViewContextHelper.class) {
            if (cachedThemedAppContext == null || cachedBaseAppContext != appCtx) {
                cachedBaseAppContext = appCtx;
                cachedThemedAppContext = new ContextThemeWrapper(appCtx, R.style.Theme_SocialApp);
            }
            return cachedThemedAppContext;
        }
    }

    public static boolean isActivityContextAlive(@Nullable Context context) {
        Context cur = context;
        while (cur instanceof ContextWrapper) {
            if (cur instanceof Activity) {
                Activity activity = (Activity) cur;
                return !activity.isFinishing() && !activity.isDestroyed();
            }
            cur = ((ContextWrapper) cur).getBaseContext();
        }
        return cur != null;
    }

    @Nullable
    public static RequestManager resolveSafeRequestManager(
            @NonNull View view,
            @Nullable RequestManager provided) {
        if (provided != null) {
            return provided;
        }
        Context ctx = view.getContext();
        if (isActivityContextAlive(ctx)) {
            try {
                return Glide.with(view);
            } catch (Throwable ignored) {
            }
        }
        if (ctx != null) {
            Context appCtx = ctx.getApplicationContext();
            try {
                return Glide.with(appCtx != null ? appCtx : ctx);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
