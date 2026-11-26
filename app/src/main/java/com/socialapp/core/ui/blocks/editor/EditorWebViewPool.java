package com.socialapp.core.ui.blocks.editor;

import android.annotation.SuppressLint;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.MutableContextWrapper;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.socialapp.core.ui.blocks.adapter.BlockWarmupEngine;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Singleton WebView pool for ComposeActivity's rich-text editor (Section 4.1.6).
 *
 * Pre-warms at most 1 WebView using MutableContextWrapper(applicationContext) during post-first-frame
 * idle time so opening ComposeActivity avoids cold Chromium initialization. Uses WebAppInterfaceProxy
 * and strict base-context resetting on release so ComposeActivity instances are never leaked.
 */
public class EditorWebViewPool {

    public static final String EDITOR_ASSET_URL = "file:///android_asset/editor/editor.html";
    private static final String JS_BRIDGE_NAME = "AndroidBridge";
    private static final String JS_RESET_DOM =
            "if (window.getSelection) { window.getSelection().removeAllRanges(); } setHtml('');";

    /**
     * Handle representing a checked-out editor WebView session bound to an active ComposeActivity.
     */
    public static final class EditorHandle {
        @Nullable
        private final WebView webView;
        @Nullable
        private final Object token;
        @NonNull
        private final WebAppInterfaceProxy bridgeProxy;
        private final boolean pooled;
        private boolean released = false;

        EditorHandle(
                @Nullable WebView webView,
                @Nullable Object token,
                @NonNull WebAppInterfaceProxy bridgeProxy,
                boolean pooled) {
            this.webView = webView;
            this.token = token;
            this.bridgeProxy = bridgeProxy;
            this.pooled = pooled;
        }

        @Nullable
        public WebView getWebView() {
            return webView;
        }

        @Nullable
        Object getToken() {
            return token;
        }

        @NonNull
        public WebAppInterfaceProxy getBridgeProxy() {
            return bridgeProxy;
        }

        public boolean isPooled() {
            return pooled;
        }

        public boolean isReleased() {
            return released;
        }

        void markReleased() {
            this.released = true;
        }
    }

    /**
     * Abstraction over WebView creation & DOM evaluation so the pool's state machine and
     * leak-prevention lifecycle can be verified deterministically in JVM tests without Robolectric.
     */
    interface WebViewDriver {
        @NonNull
        Object createContextWrapper(@NonNull Context baseContext);

        void setBaseContext(@NonNull Object contextWrapper, @NonNull Context newBase);

        @NonNull
        Object createEditorToken(
                @NonNull Object contextWrapper,
                @NonNull WebAppInterfaceProxy proxy,
                @NonNull Runnable onPageFinished);

        @Nullable
        WebView asWebView(@NonNull Object token);

        void loadUrl(@NonNull Object token, @NonNull String url);

        void evaluateJavascript(
                @NonNull Object token,
                @NonNull String script,
                @Nullable ValueCallback<String> callback);

        void onResume(@NonNull Object token);

        void onPause(@NonNull Object token);

        void clearHistory(@NonNull Object token);

        void detachFromParent(@NonNull Object token);

        void destroy(@NonNull Object token);
    }

    private final Context applicationContext;
    private final WebViewDriver driver;
    private final WebAppInterfaceProxy pooledProxy = new WebAppInterfaceProxy();

    private final AtomicBoolean warmupScheduled = new AtomicBoolean(false);

    @Nullable
    private Object pooledContextWrapper;
    @Nullable
    private Object pooledToken;
    private boolean editorLoaded = false;
    private boolean pooledCheckedOut = false;
    private int activeUnpooledCount = 0;

    @Nullable
    private Runnable pendingReadyCallback;

    public EditorWebViewPool(@NonNull Context context) {
        this(context.getApplicationContext(), new AndroidWebViewDriver());
    }

    @VisibleForTesting
    EditorWebViewPool(@NonNull Context applicationContext, @NonNull WebViewDriver driver) {
        this.applicationContext = applicationContext;
        this.driver = driver;
    }

    /**
     * Schedules background-priority idle warmup on the Main Looper without delaying Cold Start.
     */
    public void scheduleIdleWarmup(
            @NonNull View anchorView,
            @NonNull BlockWarmupEngine warmupEngine) {
        if (pooledToken != null || pooledCheckedOut || warmupEngine.isMemoryPressureActive()) {
            return;
        }
        if (!warmupScheduled.compareAndSet(false, true)) {
            return;
        }
        anchorView.post(() -> {
            if (pooledToken != null || pooledCheckedOut || warmupEngine.isMemoryPressureActive()) {
                warmupScheduled.set(false);
                return;
            }
            Looper.myQueue().addIdleHandler(() -> {
                warmupScheduled.set(false);
                if (!warmupEngine.isMemoryPressureActive()) {
                    warmup();
                }
                return false;
            });
        });
    }

    /**
     * Creates and pre-loads the single pooled editor WebView using MutableContextWrapper(applicationContext).
     */
    @MainThread
    public void warmup() {
        if (pooledToken != null || pooledCheckedOut) {
            return;
        }
        try {
            pooledContextWrapper = driver.createContextWrapper(applicationContext);
            editorLoaded = false;
            pooledToken = driver.createEditorToken(pooledContextWrapper, pooledProxy, this::notifyPageFinished);
            driver.loadUrl(pooledToken, EDITOR_ASSET_URL);
        } catch (Throwable ignored) {
            clearPoolStateQuietly();
        }
    }

    /**
     * Acquires an editor session for ComposeActivity, swapping MutableContextWrapper's base context
     * to the active Activity and attaching its callbacks to WebAppInterfaceProxy.
     */
    @MainThread
    @NonNull
    public EditorHandle acquire(
            @NonNull Context activityContext,
            @NonNull WebAppInterfaceProxy.EditorBridgeDelegate delegate,
            @NonNull Runnable onEditorReady) {
        if (!pooledCheckedOut) {
            if (pooledToken == null || pooledContextWrapper == null) {
                pooledContextWrapper = driver.createContextWrapper(applicationContext);
                driver.setBaseContext(pooledContextWrapper, activityContext);
                editorLoaded = false;
                pooledProxy.setDelegate(delegate);
                pendingReadyCallback = onEditorReady;
                pooledToken = driver.createEditorToken(pooledContextWrapper, pooledProxy, this::notifyPageFinished);
                pooledCheckedOut = true;
                driver.onResume(pooledToken);
                driver.loadUrl(pooledToken, EDITOR_ASSET_URL);
                return new EditorHandle(driver.asWebView(pooledToken), pooledToken, pooledProxy, true);
            }

            driver.detachFromParent(pooledToken);
            driver.setBaseContext(pooledContextWrapper, activityContext);
            pooledProxy.setDelegate(delegate);
            pooledCheckedOut = true;
            driver.onResume(pooledToken);

            if (editorLoaded) {
                pendingReadyCallback = null;
                onEditorReady.run();
            } else {
                pendingReadyCallback = onEditorReady;
            }
            return new EditorHandle(driver.asWebView(pooledToken), pooledToken, pooledProxy, true);
        }

        // Concurrent fallback if another ComposeActivity currently owns the pooled slot
        WebAppInterfaceProxy localProxy = new WebAppInterfaceProxy();
        localProxy.setDelegate(delegate);
        Object localWrapper = driver.createContextWrapper(activityContext);
        final boolean[] localLoaded = new boolean[]{false};
        Object localToken = driver.createEditorToken(localWrapper, localProxy, () -> {
            localLoaded[0] = true;
            if (localProxy.hasDelegate()) {
                onEditorReady.run();
            }
        });
        activeUnpooledCount++;
        driver.onResume(localToken);
        driver.loadUrl(localToken, EDITOR_ASSET_URL);
        return new EditorHandle(driver.asWebView(localToken), localToken, localProxy, false);
    }

    /**
     * Releases the checked-out handle back into the pool (if pooled) after clearing the Activity
     * delegate, resetting DOM state, detaching from View hierarchy, and restoring applicationContext.
     */
    @MainThread
    public void release(@Nullable EditorHandle handle) {
        if (handle == null || handle.isReleased()) {
            return;
        }
        handle.markReleased();
        handle.getBridgeProxy().clearDelegate();

        Object token = handle.getToken();
        if (handle.isPooled() && token != null && token == pooledToken) {
            pendingReadyCallback = null;
            driver.evaluateJavascript(token, JS_RESET_DOM, null);
            driver.clearHistory(token);
            driver.detachFromParent(token);
            driver.onPause(token);
            if (pooledContextWrapper != null) {
                driver.setBaseContext(pooledContextWrapper, applicationContext);
            }
            pooledCheckedOut = false;
            return;
        }

        if (token != null) {
            activeUnpooledCount = Math.max(0, activeUnpooledCount - 1);
            driver.detachFromParent(token);
            driver.destroy(token);
        }
    }

    public void setHtml(@Nullable EditorHandle handle, @Nullable String html) {
        if (handle == null || handle.isReleased() || handle.getToken() == null) {
            return;
        }
        String safe = html != null ? html : "";
        driver.evaluateJavascript(handle.getToken(), "setHtml(" + toJsStringLiteral(safe) + ");", null);
    }

    public void insertImage(@Nullable EditorHandle handle, @Nullable String imageUrl) {
        if (handle == null || handle.isReleased() || handle.getToken() == null || imageUrl == null) {
            return;
        }
        driver.evaluateJavascript(handle.getToken(), "insertImage(" + toJsStringLiteral(imageUrl) + ");", null);
    }

    public void evaluateHtml(@Nullable EditorHandle handle, @NonNull ValueCallback<String> callback) {
        if (handle == null || handle.isReleased() || handle.getToken() == null) {
            callback.onReceiveValue("\"\"");
            return;
        }
        driver.evaluateJavascript(handle.getToken(), "getHtml();", callback);
    }

    /**
     * Destroys the idle pooled WebView when RAM is low or app moves to background,
     * never evicting an editor session currently open in ComposeActivity.
     */
    public void trimMemory(int level) {
        boolean shouldReleaseWebView = level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
                || level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
                || level == ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
                || level == ComponentCallbacks2.TRIM_MEMORY_MODERATE
                || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
                || level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE;

        if (shouldReleaseWebView && !isInUse()) {
            destroyPool();
        }
    }

    public void destroyPool() {
        warmupScheduled.set(false);
        pendingReadyCallback = null;
        pooledProxy.clearDelegate();
        if (pooledToken != null && !pooledCheckedOut) {
            try {
                driver.detachFromParent(pooledToken);
                driver.destroy(pooledToken);
            } catch (Throwable ignored) {
            }
            clearPoolStateQuietly();
        }
    }

    public boolean isInUse() {
        return pooledCheckedOut || activeUnpooledCount > 0;
    }

    public boolean hasWarmWebView() {
        return pooledToken != null;
    }

    public boolean isEditorLoaded() {
        return editorLoaded;
    }

    @VisibleForTesting
    void triggerLoadedForTesting() {
        notifyPageFinished();
    }

    private void notifyPageFinished() {
        editorLoaded = true;
        Runnable callback = pendingReadyCallback;
        pendingReadyCallback = null;
        if (callback != null) {
            callback.run();
        }
    }

    private void clearPoolStateQuietly() {
        pooledToken = null;
        pooledContextWrapper = null;
        editorLoaded = false;
        pooledCheckedOut = false;
    }

    /**
     * Escapes an arbitrary Java string into a valid double-quoted JavaScript string literal
     * immune to apostrophes, newlines, backslashes, and closing script tags.
     */
    @NonNull
    public static String toJsStringLiteral(@Nullable String input) {
        if (input == null || input.isEmpty()) {
            return "\"\"";
        }
        StringBuilder sb = new StringBuilder(input.length() + 16);
        sb.append('"');
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '<':
                    sb.append("\\u003C");
                    break;
                case '\u2028':
                    sb.append("\\u2028");
                    break;
                case '\u2029':
                    sb.append("\\u2029");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private static final class AndroidWebViewDriver implements WebViewDriver {

        @NonNull
        @Override
        public Object createContextWrapper(@NonNull Context baseContext) {
            return new MutableContextWrapper(baseContext);
        }

        @Override
        public void setBaseContext(@NonNull Object contextWrapper, @NonNull Context newBase) {
            ((MutableContextWrapper) contextWrapper).setBaseContext(newBase);
        }

        @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
        @NonNull
        @Override
        public Object createEditorToken(
                @NonNull Object contextWrapper,
                @NonNull WebAppInterfaceProxy proxy,
                @NonNull Runnable onPageFinished) {
            WebView webView = new WebView((MutableContextWrapper) contextWrapper);
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setAllowFileAccess(true);
            settings.setDomStorageEnabled(true);
            webView.addJavascriptInterface(proxy, JS_BRIDGE_NAME);
            webView.setWebViewClient(new EditorClient(onPageFinished));
            return webView;
        }

        @Nullable
        @Override
        public WebView asWebView(@NonNull Object token) {
            return (WebView) token;
        }

        @Override
        public void loadUrl(@NonNull Object token, @NonNull String url) {
            ((WebView) token).loadUrl(url);
        }

        @Override
        public void evaluateJavascript(
                @NonNull Object token,
                @NonNull String script,
                @Nullable ValueCallback<String> callback) {
            ((WebView) token).evaluateJavascript(script, callback);
        }

        @Override
        public void onResume(@NonNull Object token) {
            ((WebView) token).onResume();
        }

        @Override
        public void onPause(@NonNull Object token) {
            ((WebView) token).onPause();
        }

        @Override
        public void clearHistory(@NonNull Object token) {
            ((WebView) token).clearHistory();
        }

        @Override
        public void detachFromParent(@NonNull Object token) {
            WebView webView = (WebView) token;
            webView.clearFocus();
            webView.scrollTo(0, 0);
            ViewParent parent = webView.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(webView);
            }
        }

        @Override
        public void destroy(@NonNull Object token) {
            WebView webView = (WebView) token;
            webView.stopLoading();
            webView.removeJavascriptInterface(JS_BRIDGE_NAME);
            webView.destroy();
        }
    }

    private static final class EditorClient extends WebViewClient {
        private final Runnable onPageFinished;

        EditorClient(@NonNull Runnable onPageFinished) {
            this.onPageFinished = onPageFinished;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            onPageFinished.run();
        }
    }
}
