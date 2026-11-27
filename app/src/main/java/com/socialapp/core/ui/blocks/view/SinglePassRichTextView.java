package com.socialapp.core.ui.blocks.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.RenderNode;
import android.os.Build;
import android.os.Bundle;
import android.text.Layout;
import android.text.Spanned;
import android.text.StaticLayout;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import com.socialapp.core.ui.blocks.layout.BlockPrecomputer;
import com.socialapp.core.ui.blocks.layout.MeasuredTextBlockSpec;
import com.socialapp.core.ui.blocks.model.RichTextBlock;
import com.socialapp.core.ui.blocks.parser.UrlClickableSpan;

import java.util.List;

/**
 * Single-pass Custom View for PostDetail headings, paragraphs, lead paragraphs, and list items.
 * Supports off-main-thread StaticLayout, API 29+ RenderNode caching, line/offset link hit-testing
 * (including multi-line links), Resize Guard, and TalkBack accessibility.
 */
public class SinglePassRichTextView extends View {

    public interface OnLinkClickListener {
        void onLinkClick(@NonNull String url);
    }

    private static final int VIRTUAL_TEXT = 1;
    private static final int VIRTUAL_LINK_BASE = 100;

    private final int touchSlop;
    @Nullable
    private final AccessibilityManager accessibilityManager;
    private final RichTextExploreHelper touchHelper;

    private BlockPrecomputer precomputer;
    @Nullable
    private RichTextBlock boundBlock;
    @Nullable
    private MeasuredTextBlockSpec spec;
    @Nullable
    private OnLinkClickListener linkClickListener;

    @Nullable
    private Object renderNodeObj;
    private boolean renderNodeDirty = true;

    private boolean isPointerDown = false;
    private float downX;
    private float downY;
    @Nullable
    private String downLinkUrl;

    public SinglePassRichTextView(@NonNull Context context, @NonNull BlockPrecomputer precomputer) {
        super(context);
        this.precomputer = precomputer;
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        AccessibilityManager am = null;
        try {
            Object svc = context.getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (svc instanceof AccessibilityManager) {
                am = (AccessibilityManager) svc;
            }
        } catch (Throwable ignored) {
        }
        this.accessibilityManager = am;
        setFocusable(true);
        this.touchHelper = new RichTextExploreHelper(this);
        ViewCompat.setAccessibilityDelegate(this, this.touchHelper);
    }

    public void setPrecomputer(@NonNull BlockPrecomputer precomputer) {
        this.precomputer = precomputer;
    }

    public void setOnLinkClickListener(@Nullable OnLinkClickListener listener) {
        this.linkClickListener = listener;
    }

    public void bind(@NonNull RichTextBlock block, int targetWidthPx) {
        RichTextBlock prevBlock = this.boundBlock;
        this.boundBlock = block;
        int safeWidth = targetWidthPx > 0 ? targetWidthPx
                : (getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels);
        float density = getResources().getDisplayMetrics().density;
        float fontScale = getResources().getConfiguration().fontScale;

        MeasuredTextBlockSpec prev = this.spec;
        if (prevBlock != null
                && prev != null
                && prevBlock.getBlockId().equals(block.getBlockId())
                && prevBlock.getContentHash() == block.getContentHash()
                && prev.matchesWidthAndScale(safeWidth, fontScale)) {
            return;
        }

        MeasuredTextBlockSpec next = precomputer.precomputeRichText(block, safeWidth, density, fontScale);
        boolean heightChanged = (prev == null || prev.getTotalHeightPx() != next.getTotalHeightPx());
        this.spec = next;
        this.renderNodeDirty = true;
        invalidateAccessibilityIfEnabled();

        if (heightChanged) {
            requestLayout();
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width <= 0) {
            width = getResources().getDisplayMetrics().widthPixels;
        }
        if (boundBlock != null) {
            float density = getResources().getDisplayMetrics().density;
            float fontScale = getResources().getConfiguration().fontScale;
            if (spec == null
                    || !spec.matchesWidthAndScale(width, fontScale)
                    || spec.getContentHash() != boundBlock.getContentHash()) {
                spec = precomputer.precomputeRichText(boundBlock, width, density, fontScale);
                renderNodeDirty = true;
                invalidateAccessibilityIfEnabled();
            }
        }
        setMeasuredDimension(width, spec != null ? spec.getTotalHeightPx() : 0);
    }

    private void invalidateAccessibilityIfEnabled() {
        if (accessibilityManager != null && accessibilityManager.isEnabled()) {
            touchHelper.invalidateRoot();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw || h != oldh) {
            renderNodeDirty = true;
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (spec == null || spec.textLayout == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            drawWithRenderNode(canvas, spec);
        } else {
            drawTextLayout(canvas, spec);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void drawWithRenderNode(@NonNull Canvas canvas, @NonNull MeasuredTextBlockSpec textSpec) {
        if (!canvas.isHardwareAccelerated()) {
            drawTextLayout(canvas, textSpec);
            return;
        }
        RenderNode node = (RenderNode) renderNodeObj;
        if (node == null) {
            node = new RenderNode("RichTextBlockNode");
            renderNodeObj = node;
            renderNodeDirty = true;
        }
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        node.setPosition(0, 0, w, h);

        if (renderNodeDirty || !node.hasDisplayList()) {
            RecordingCanvas rec = node.beginRecording(w, h);
            try {
                drawTextLayout(rec, textSpec);
            } finally {
                node.endRecording();
            }
            renderNodeDirty = false;
        }
        canvas.drawRenderNode(node);
    }

    private static void drawTextLayout(@NonNull Canvas canvas, @NonNull MeasuredTextBlockSpec textSpec) {
        if (textSpec.textLayout == null) return;
        canvas.save();
        canvas.translate(textSpec.textRect.left, textSpec.textRect.top);
        textSpec.textLayout.draw(canvas);
        canvas.restore();
    }

    @Nullable
    public static String resolveUrlAtTouch(
            @Nullable StaticLayout layout,
            @Nullable Spanned spanned,
            int textLeft,
            int textTop,
            float x,
            float y) {
        if (layout == null || spanned == null) {
            return null;
        }
        float relY = y - textTop;
        float relX = x - textLeft;
        if (relY < 0 || relY > layout.getHeight()) {
            return null;
        }
        int line = layout.getLineForVertical((int) relY);
        if (relX < layout.getLineLeft(line) || relX > layout.getLineRight(line)) {
            return null;
        }
        int offset = layout.getOffsetForHorizontal(line, relX);
        UrlClickableSpan[] spans = spanned.getSpans(offset, offset, UrlClickableSpan.class);
        if (spans != null && spans.length > 0) {
            return spans[0].getUrl();
        }
        return null;
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (spec == null || spec.spannedText == null || spec.textLayout == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downLinkUrl = resolveUrlAtTouch(
                        spec.textLayout, spec.spannedText,
                        spec.textRect.left, spec.textRect.top,
                        event.getX(), event.getY()
                );
                if (downLinkUrl == null) {
                    return false;
                }
                isPointerDown = true;
                downX = event.getX();
                downY = event.getY();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (isPointerDown) {
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    if ((dx * dx + dy * dy) > (touchSlop * touchSlop)) {
                        isPointerDown = false;
                        downLinkUrl = null;
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (isPointerDown) {
                    isPointerDown = false;
                    String upUrl = resolveUrlAtTouch(
                            spec.textLayout, spec.spannedText,
                            spec.textRect.left, spec.textRect.top,
                            event.getX(), event.getY()
                    );
                    String targetUrl = upUrl != null ? upUrl : downLinkUrl;
                    downLinkUrl = null;
                    if (targetUrl != null && linkClickListener != null) {
                        performClick();
                        linkClickListener.onLinkClick(targetUrl);
                    }
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                isPointerDown = false;
                downLinkUrl = null;
                return true;

            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    protected boolean dispatchHoverEvent(MotionEvent event) {
        return touchHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return touchHelper.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, @Nullable Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        touchHelper.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
    }

    private final class RichTextExploreHelper extends ExploreByTouchHelper {

        RichTextExploreHelper(@NonNull View host) {
            super(host);
        }

        @Override
        protected int getVirtualViewAt(float x, float y) {
            if (spec == null) return INVALID_ID;
            if (spec.spannedText != null && spec.textLayout != null) {
                String url = resolveUrlAtTouch(spec.textLayout, spec.spannedText, spec.textRect.left, spec.textRect.top, x, y);
                if (url != null) {
                    UrlClickableSpan[] spans = spec.spannedText.getSpans(0, spec.spannedText.length(), UrlClickableSpan.class);
                    for (int i = 0; i < spans.length; i++) {
                        if (url.equals(spans[i].getUrl())) {
                            return VIRTUAL_LINK_BASE + i;
                        }
                    }
                }
            }
            return spec.textRect.contains(Math.round(x), Math.round(y)) ? VIRTUAL_TEXT : INVALID_ID;
        }

        @Override
        protected void getVisibleVirtualViews(@NonNull List<Integer> virtualViewIds) {
            if (spec == null || boundBlock == null) return;
            virtualViewIds.add(VIRTUAL_TEXT);
            if (spec.spannedText != null) {
                UrlClickableSpan[] spans = spec.spannedText.getSpans(0, spec.spannedText.length(), UrlClickableSpan.class);
                for (int i = 0; i < spans.length; i++) {
                    virtualViewIds.add(VIRTUAL_LINK_BASE + i);
                }
            }
        }

        @Override
        protected void onPopulateNodeForVirtualView(int virtualViewId, @NonNull AccessibilityNodeInfoCompat node) {
            if (spec == null || boundBlock == null) {
                node.setContentDescription("");
                node.setBoundsInParent(new Rect(0, 0, 1, 1));
                return;
            }
            Rect bounds = new Rect();
            spec.textRect.toAndroidRect(bounds);
            if (bounds.isEmpty()) bounds.set(0, 0, 1, 1);

            if (virtualViewId >= VIRTUAL_LINK_BASE && spec.spannedText != null) {
                UrlClickableSpan[] spans = spec.spannedText.getSpans(0, spec.spannedText.length(), UrlClickableSpan.class);
                int idx = virtualViewId - VIRTUAL_LINK_BASE;
                if (idx >= 0 && idx < spans.length) {
                    int s = spec.spannedText.getSpanStart(spans[idx]);
                    int e = spec.spannedText.getSpanEnd(spans[idx]);
                    String label = spec.spannedText.subSequence(Math.max(0, s), Math.max(0, e)).toString();
                    node.setContentDescription("Liên kết: " + label);
                    node.setBoundsInParent(bounds);
                    node.setClickable(true);
                    node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
                    return;
                }
            }
            node.setContentDescription(boundBlock.getStyledText().getText());
            node.setBoundsInParent(bounds);
            node.setClickable(false);
        }

        @Override
        protected boolean onPerformActionForVirtualView(int virtualViewId, int action, @Nullable Bundle arguments) {
            if (action == AccessibilityNodeInfoCompat.ACTION_CLICK
                    && virtualViewId >= VIRTUAL_LINK_BASE
                    && spec != null
                    && spec.spannedText != null) {
                UrlClickableSpan[] spans = spec.spannedText.getSpans(0, spec.spannedText.length(), UrlClickableSpan.class);
                int idx = virtualViewId - VIRTUAL_LINK_BASE;
                if (idx >= 0 && idx < spans.length) {
                    if (linkClickListener != null) {
                        linkClickListener.onLinkClick(spans[idx].getUrl());
                    }
                    sendEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED);
                    return true;
                }
            }
            return false;
        }
    }
}
