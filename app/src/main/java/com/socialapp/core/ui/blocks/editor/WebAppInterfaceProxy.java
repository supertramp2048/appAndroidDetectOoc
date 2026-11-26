package com.socialapp.core.ui.blocks.editor;

import android.webkit.JavascriptInterface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Leak-safe JavaScript bridge proxy attached permanently to pooled editor WebViews.
 * Avoids passing anonymous inner classes holding an Activity reference directly into
 * WebView.addJavascriptInterface(), which otherwise leaks every Activity instance.
 */
public class WebAppInterfaceProxy {

    public interface EditorBridgeDelegate {
        void onPickImageRequested();
        void onContentChanged(@NonNull String html);
    }

    @Nullable
    private volatile EditorBridgeDelegate delegate;

    public void setDelegate(@Nullable EditorBridgeDelegate delegate) {
        this.delegate = delegate;
    }

    public void clearDelegate() {
        this.delegate = null;
    }

    public boolean hasDelegate() {
        return delegate != null;
    }

    @JavascriptInterface
    public void pickImage() {
        EditorBridgeDelegate active = this.delegate;
        if (active != null) {
            active.onPickImageRequested();
        }
    }

    @JavascriptInterface
    public void onContentChanged(@Nullable String html) {
        EditorBridgeDelegate active = this.delegate;
        if (active != null && html != null) {
            active.onContentChanged(html);
        }
    }
}
