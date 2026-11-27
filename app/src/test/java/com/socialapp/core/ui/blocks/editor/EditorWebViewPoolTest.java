package com.socialapp.core.ui.blocks.editor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.ContextWrapper;
import android.webkit.ValueCallback;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.socialapp.MyApplication;
import com.socialapp.core.ui.blocks.adapter.BlockWarmupEngine;
import com.socialapp.core.ui.blocks.adapter.UnifiedBlockViewPool;
import com.socialapp.core.ui.blocks.layout.BlockLayoutCache;
import com.socialapp.core.ui.blocks.layout.BlockPrecomputer;
import com.socialapp.core.ui.blocks.layout.TextPaintRegistry;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class EditorWebViewPoolTest {

    private static final class FakeWrapper {
        Context baseContext;

        FakeWrapper(Context baseContext) {
            this.baseContext = baseContext;
        }
    }

    private static final class FakeWebViewToken {
        final FakeWrapper wrapper;
        final WebAppInterfaceProxy proxy;
        final Runnable onPageFinished;
        final List<String> loadedUrls = new ArrayList<>();
        final List<String> scripts = new ArrayList<>();
        int resumeCount = 0;
        int pauseCount = 0;
        int clearHistoryCount = 0;
        int detachCount = 0;
        int destroyCount = 0;

        FakeWebViewToken(FakeWrapper wrapper, WebAppInterfaceProxy proxy, Runnable onPageFinished) {
            this.wrapper = wrapper;
            this.proxy = proxy;
            this.onPageFinished = onPageFinished;
        }
    }

    private static final class RecordingDriver implements EditorWebViewPool.WebViewDriver {
        final List<FakeWebViewToken> createdTokens = new ArrayList<>();

        @NonNull
        @Override
        public Object createContextWrapper(@NonNull Context baseContext) {
            return new FakeWrapper(baseContext);
        }

        @Override
        public void setBaseContext(@NonNull Object contextWrapper, @NonNull Context newBase) {
            ((FakeWrapper) contextWrapper).baseContext = newBase;
        }

        @NonNull
        @Override
        public Object createEditorToken(
                @NonNull Object contextWrapper,
                @NonNull WebAppInterfaceProxy proxy,
                @NonNull Runnable onPageFinished) {
            FakeWebViewToken token = new FakeWebViewToken((FakeWrapper) contextWrapper, proxy, onPageFinished);
            createdTokens.add(token);
            return token;
        }

        @Nullable
        @Override
        public WebView asWebView(@NonNull Object token) {
            return null;
        }

        @Override
        public void loadUrl(@NonNull Object token, @NonNull String url) {
            ((FakeWebViewToken) token).loadedUrls.add(url);
        }

        @Override
        public void evaluateJavascript(
                @NonNull Object token,
                @NonNull String script,
                @Nullable ValueCallback<String> callback) {
            FakeWebViewToken fake = (FakeWebViewToken) token;
            fake.scripts.add(script);
            if (callback != null && "getHtml();".equals(script)) {
                callback.onReceiveValue("\"<p>Submitted</p>\"");
            }
        }

        @Override
        public void onResume(@NonNull Object token) {
            ((FakeWebViewToken) token).resumeCount++;
        }

        @Override
        public void onPause(@NonNull Object token) {
            ((FakeWebViewToken) token).pauseCount++;
        }

        @Override
        public void clearHistory(@NonNull Object token) {
            ((FakeWebViewToken) token).clearHistoryCount++;
        }

        @Override
        public void detachFromParent(@NonNull Object token) {
            ((FakeWebViewToken) token).detachCount++;
        }

        @Override
        public void destroy(@NonNull Object token) {
            ((FakeWebViewToken) token).destroyCount++;
        }
    }

    private static Context stubContext() {
        return new ContextWrapper(null);
    }

    @Test
    public void testWarmupReuseAndLeakFreeContextLifecycleAcrossMultipleSessions() {
        Context appContext = stubContext();
        Context activity1 = stubContext();
        Context activity2 = stubContext();
        RecordingDriver driver = new RecordingDriver();
        EditorWebViewPool pool = new EditorWebViewPool(appContext, driver);

        // 1. Idle warmup uses ApplicationContext
        pool.warmup();
        assertTrue(pool.hasWarmWebView());
        assertFalse(pool.isInUse());
        assertFalse(pool.isEditorLoaded());
        assertEquals(1, driver.createdTokens.size());

        FakeWebViewToken pooledToken = driver.createdTokens.get(0);
        assertSame(appContext, pooledToken.wrapper.baseContext);
        assertEquals(EditorWebViewPool.EDITOR_ASSET_URL, pooledToken.loadedUrls.get(0));

        pool.triggerLoadedForTesting();
        assertTrue(pool.isEditorLoaded());

        // 2. Acquire for ComposeActivity #1 -> immediate ready, baseContext swapped to activity1
        AtomicInteger ready1 = new AtomicInteger(0);
        AtomicInteger pickCount1 = new AtomicInteger(0);
        List<String> edits1 = new ArrayList<>();
        EditorWebViewPool.EditorHandle handle1 = pool.acquire(activity1, new WebAppInterfaceProxy.EditorBridgeDelegate() {
            @Override
            public void onPickImageRequested() {
                pickCount1.incrementAndGet();
            }

            @Override
            public void onContentChanged(@NonNull String html) {
                edits1.add(html);
            }
        }, ready1::incrementAndGet);

        assertTrue(handle1.isPooled());
        assertTrue(pool.isInUse());
        assertEquals(1, ready1.get());
        assertSame(activity1, pooledToken.wrapper.baseContext);
        assertTrue(handle1.getBridgeProxy().hasDelegate());

        pool.setHtml(handle1, "<p>Bài cũ</p>");
        pool.insertImage(handle1, "https://cdn.example.com/pic1.jpg");
        pooledToken.proxy.pickImage();
        pooledToken.proxy.onContentChanged("<p>Đang gõ</p>");
        assertEquals(1, pickCount1.get());
        assertEquals(1, edits1.size());

        // 3. Release on ComposeActivity #1 onDestroy() -> clears delegate, resets DOM, restores appContext
        pool.release(handle1);
        assertTrue(handle1.isReleased());
        assertFalse(pool.isInUse());
        assertTrue(pool.hasWarmWebView());
        assertFalse(pooledToken.proxy.hasDelegate());
        assertSame(appContext, pooledToken.wrapper.baseContext);
        assertNotSame(activity1, pooledToken.wrapper.baseContext);
        assertEquals(1, pooledToken.clearHistoryCount);
        assertEquals(1, pooledToken.pauseCount);
        assertTrue(pooledToken.scripts.get(pooledToken.scripts.size() - 1).contains("setHtml('')"));

        // Late Chromium JS callback after release must not reach old Activity
        pooledToken.proxy.pickImage();
        pooledToken.proxy.onContentChanged("<p>Late ghost edit</p>");
        assertEquals(1, pickCount1.get());
        assertEquals(1, edits1.size());

        // 4. Acquire for ComposeActivity #2 -> reuses the exact same WebView, zero re-load
        AtomicInteger ready2 = new AtomicInteger(0);
        List<String> edits2 = new ArrayList<>();
        EditorWebViewPool.EditorHandle handle2 = pool.acquire(activity2, new WebAppInterfaceProxy.EditorBridgeDelegate() {
            @Override
            public void onPickImageRequested() {
            }

            @Override
            public void onContentChanged(@NonNull String html) {
                edits2.add(html);
            }
        }, ready2::incrementAndGet);

        assertEquals(1, driver.createdTokens.size());
        assertEquals(1, ready2.get());
        assertSame(activity2, pooledToken.wrapper.baseContext);

        pool.setHtml(handle2, "");
        assertEquals("setHtml(\"\");", pooledToken.scripts.get(pooledToken.scripts.size() - 1));
        pooledToken.proxy.onContentChanged("<p>Bài mới</p>");
        assertEquals(1, edits2.size());
        assertEquals("<p>Bài mới</p>", edits2.get(0));

        pool.release(handle2);
        assertSame(appContext, pooledToken.wrapper.baseContext);
        assertEquals(0, pooledToken.destroyCount);
    }

    @Test
    public void testColdAcquireAndEarlyReleaseBeforePageFinished() {
        Context appContext = stubContext();
        Context activity = stubContext();
        RecordingDriver driver = new RecordingDriver();
        EditorWebViewPool pool = new EditorWebViewPool(appContext, driver);

        AtomicBoolean readyFired = new AtomicBoolean(false);
        EditorWebViewPool.EditorHandle handle = pool.acquire(activity, new WebAppInterfaceProxy.EditorBridgeDelegate() {
            @Override
            public void onPickImageRequested() {
            }

            @Override
            public void onContentChanged(@NonNull String html) {
            }
        }, () -> readyFired.set(true));

        assertFalse(readyFired.get());
        FakeWebViewToken token = driver.createdTokens.get(0);
        assertSame(activity, token.wrapper.baseContext);

        // User closes Activity before editor.html finished loading
        pool.release(handle);
        assertSame(appContext, token.wrapper.baseContext);

        // Late onPageFinished must not fire the cancelled Activity's ready callback
        token.onPageFinished.run();
        assertFalse(readyFired.get());
        assertTrue(pool.isEditorLoaded());
    }

    @Test
    public void testConcurrentAcquireFallsBackToUnpooledAndDestroysSafely() {
        Context appContext = stubContext();
        Context activity1 = stubContext();
        Context activity2 = stubContext();
        RecordingDriver driver = new RecordingDriver();
        EditorWebViewPool pool = new EditorWebViewPool(appContext, driver);

        pool.warmup();
        pool.triggerLoadedForTesting();

        EditorWebViewPool.EditorHandle pooledHandle = pool.acquire(activity1, new WebAppInterfaceProxy.EditorBridgeDelegate() {
            @Override
            public void onPickImageRequested() {
            }

            @Override
            public void onContentChanged(@NonNull String html) {
            }
        }, () -> {});

        EditorWebViewPool.EditorHandle fallbackHandle = pool.acquire(activity2, new WebAppInterfaceProxy.EditorBridgeDelegate() {
            @Override
            public void onPickImageRequested() {
            }

            @Override
            public void onContentChanged(@NonNull String html) {
            }
        }, () -> {});

        assertTrue(pooledHandle.isPooled());
        assertFalse(fallbackHandle.isPooled());
        assertEquals(2, driver.createdTokens.size());

        pool.release(fallbackHandle);
        assertEquals(0, driver.createdTokens.get(0).destroyCount);
        assertEquals(1, driver.createdTokens.get(1).destroyCount);
        assertTrue(pool.isInUse());

        pool.release(pooledHandle);
        assertFalse(pool.isInUse());
        assertTrue(pool.hasWarmWebView());
    }

    @Test
    public void testMemoryTrimDestroysIdlePoolOnlyWhenComposeIsNotOpen() {
        Context appContext = stubContext();
        Context activity = stubContext();
        RecordingDriver driver = new RecordingDriver();
        EditorWebViewPool editorPool = new EditorWebViewPool(appContext, driver);

        BlockLayoutCache l1Cache = new BlockLayoutCache(16);
        UnifiedBlockViewPool l3Pool = new UnifiedBlockViewPool();
        BlockWarmupEngine warmupEngine = new BlockWarmupEngine(
                new BlockPrecomputer(new TextPaintRegistry(), l1Cache),
                l3Pool
        );

        editorPool.warmup();
        editorPool.triggerLoadedForTesting();
        FakeWebViewToken token = driver.createdTokens.get(0);

        // Open ComposeActivity: memory trim must NOT destroy active editor WebView
        EditorWebViewPool.EditorHandle handle = editorPool.acquire(activity, new WebAppInterfaceProxy.EditorBridgeDelegate() {
            @Override
            public void onPickImageRequested() {
            }

            @Override
            public void onContentChanged(@NonNull String html) {
            }
        }, () -> {});

        MyApplication.dispatchMemoryTrim(
                ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN, l1Cache, l3Pool, warmupEngine, editorPool, null
        );
        assertTrue(editorPool.hasWarmWebView());
        assertEquals(0, token.destroyCount);

        // Release back to idle: moderate keeps pool, UI_HIDDEN / RUNNING_LOW / CRITICAL destroys idle WebView
        editorPool.release(handle);
        MyApplication.dispatchMemoryTrim(
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE, l1Cache, l3Pool, warmupEngine, editorPool, null
        );
        assertTrue(editorPool.hasWarmWebView());
        assertEquals(0, token.destroyCount);

        MyApplication.dispatchMemoryTrim(
                ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN, l1Cache, l3Pool, warmupEngine, editorPool, null
        );
        assertFalse(editorPool.hasWarmWebView());
        assertEquals(1, token.destroyCount);
    }

    @Test
    public void testJsStringEscaping() {
        String raw = "Xin chào 'O'Connor', \"quotes\", line1\nline2\\path</script>";
        String literal = EditorWebViewPool.toJsStringLiteral(raw);
        assertEquals(
                "\"Xin chào 'O'Connor', \\\"quotes\\\", line1\\nline2\\\\path\\u003C/script>\"",
                literal
        );
        assertEquals("\"\"", EditorWebViewPool.toJsStringLiteral(""));
        assertEquals("\"\"", EditorWebViewPool.toJsStringLiteral(null));
    }
}
