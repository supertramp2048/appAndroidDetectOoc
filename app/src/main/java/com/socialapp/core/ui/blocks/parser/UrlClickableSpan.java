package com.socialapp.core.ui.blocks.parser;

import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Custom ClickableSpan for inline <a href="..."> elements in PostDetail blocks.
 * Styled in NYT Editorial primary blue (#1E5AA8) with underline.
 */
public class UrlClickableSpan extends ClickableSpan {

    public static final int LINK_COLOR = 0xFF1E5AA8;

    public interface OnUrlClickListener {
        void onUrlClick(@NonNull String url);
    }

    @NonNull
    private final String url;
    @Nullable
    private final OnUrlClickListener listener;

    public UrlClickableSpan(@NonNull String url) {
        this(url, null);
    }

    public UrlClickableSpan(@NonNull String url, @Nullable OnUrlClickListener listener) {
        this.url = url;
        this.listener = listener;
    }

    @NonNull
    public String getUrl() {
        return url;
    }

    @Override
    public void onClick(@NonNull View widget) {
        if (listener != null) {
            listener.onUrlClick(url);
        }
    }

    @Override
    public void updateDrawState(@NonNull TextPaint ds) {
        ds.setColor(LINK_COLOR);
        ds.setUnderlineText(true);
    }
}
