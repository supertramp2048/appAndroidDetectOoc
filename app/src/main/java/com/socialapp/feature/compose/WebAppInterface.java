package com.socialapp.feature.compose;

import android.webkit.JavascriptInterface;

import androidx.annotation.NonNull;

public class WebAppInterface {

    public interface OnImagePickListener {
        void onPickImageRequested();
    }

    public interface OnContentChangeListener {
        void onContentChanged(@NonNull String html);
    }

    private final OnImagePickListener imagePickListener;
    private final OnContentChangeListener contentChangeListener;

    public WebAppInterface(@NonNull OnImagePickListener imagePickListener,
                           @NonNull OnContentChangeListener contentChangeListener) {
        this.imagePickListener = imagePickListener;
        this.contentChangeListener = contentChangeListener;
    }

    @JavascriptInterface
    public void pickImage() {
        if (imagePickListener != null) {
            imagePickListener.onPickImageRequested();
        }
    }

    @JavascriptInterface
    public void onContentChanged(String html) {
        if (contentChangeListener != null && html != null) {
            contentChangeListener.onContentChanged(html);
        }
    }
}
