package com.socialapp.core.ui.blocks.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import com.socialapp.core.ui.blocks.layout.BlockPrecomputer;
import com.socialapp.core.ui.blocks.layout.MeasuredTextBlockSpec;
import com.socialapp.core.ui.blocks.model.QuoteBlock;

import java.util.List;

/**
 * Single-pass Custom View for PostDetail QuoteBlock matching buildEditorialHtml() blockquote style:
 * background #F8FAFC, 3dp left accent bar #1E5AA8, right corner radius 4dp, serif italic #334155.
 */
public class SinglePassQuoteView extends View {

    private static final int VIRTUAL_QUOTE = 1;

    private final Paint quoteBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accentBarPaint = new Paint();
    private final Path quoteBgPath = new Path();
    private final RectF bgRectF = new RectF();
    private final int touchSlop;
    @Nullable
    private final AccessibilityManager accessibilityManager;
    private final QuoteExploreHelper touchHelper;

    private BlockPrecomputer precomputer;
    @Nullable
    private QuoteBlock boundBlock;
    @Nullable
    private MeasuredTextBlockSpec spec;
    @Nullable
    private SinglePassRichTextView.OnLinkClickListener linkClickListener;

    @Nullable
    private Object renderNodeObj;
    private boolean renderNodeDirty = true;

    private boolean isPointerDown = false;
    private float downX;
    private float downY;
    @Nullable
    private String downLinkUrl;

    public SinglePassQuoteView(@NonNull Context context, @NonNull BlockPrecomputer precomputer) {
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

        quoteBgPaint.setColor(0xFFF8FAFC);
        quoteBgPaint.setStyle(Paint.Style.FILL);

        accentBarPaint.setColor(0xFF1E5AA8);
        accentBarPaint.setStyle(Paint.Style.FILL);

        setFocusable(true);
        this.touchHelper = new QuoteExploreHelper(this);
        ViewCompat.setAccessibilityDelegate(this, this.touchHelper);
    }

    public void setPrecomputer(@NonNull BlockPrecomputer precomputer) {
        this.precomputer = precomputer;
    }

    public void setOnLinkClickListener(@Nullable SinglePassRichTextView.OnLinkClickListener listener) {
        this.linkClickListener = listener;
    }

    public void bind(@NonNull QuoteBlock block, int targetWidthPx) {
        QuoteBlock prevBlock = this.boundBlock;
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

        MeasuredTextBlockSpec next = precomputer.precomputeQuote(block, safeWidth, density, fontScale);
        boolean heightChanged = (prev == null || prev.getTotalHeightPx() != next.getTotalHeightPx());
        this.spec = next;
        this.renderNodeDirty = true;
        updateBgPath(next);
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
                spec = precomputer.precomputeQuote(boundBlock, width, density, fontScale);
                renderNodeDirty = true;
                updateBgPath(spec);
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
            drawQuoteContent(canvas, spec);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void drawWithRenderNode(@NonNull Canvas canvas, @NonNull MeasuredTextBlockSpec quoteSpec) {
        if (!canvas.isHardwareAccelerated()) {
            drawQuoteContent(canvas, quoteSpec);
            return;
        }
        RenderNode node = (RenderNode) renderNodeObj;
        if (node == null) {
            node = new RenderNode("QuoteBlockNode");
            renderNodeObj = node;
            renderNodeDirty = true;
        }
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        node.setPosition(0, 0, w, h);

        if (renderNodeDirty || !node.hasDisplayList()) {
            RecordingCanvas rec = node.beginRecording(w, h);
            try {
                drawQuoteContent(rec, quoteSpec);
            } finally {
                node.endRecording();
            }
            renderNodeDirty = false;
        }
        canvas.drawRenderNode(node);
    }

    private void drawQuoteContent(@NonNull Canvas canvas, @NonNull MeasuredTextBlockSpec quoteSpec) {
        if (!quoteSpec.backgroundRect.isEmpty()) {
            canvas.drawPath(quoteBgPath, quoteBgPaint);
        }
        if (!quoteSpec.accentBarRect.isEmpty()) {
            canvas.drawRect(
                    quoteSpec.accentBarRect.left,
                    quoteSpec.accentBarRect.top,
                    quoteSpec.accentBarRect.right,
                    quoteSpec.accentBarRect.bottom,
                    accentBarPaint
            );
        }
        if (quoteSpec.textLayout != null) {
            canvas.save();
            canvas.translate(quoteSpec.textRect.left, quoteSpec.textRect.top);
            quoteSpec.textLayout.draw(canvas);
            canvas.restore();
        }
    }

    private void updateBgPath(@NonNull MeasuredTextBlockSpec quoteSpec) {
        quoteBgPath.reset();
        if (!quoteSpec.backgroundRect.isEmpty()) {
            bgRectF.set(
                    quoteSpec.backgroundRect.left,
                    quoteSpec.backgroundRect.top,
                    quoteSpec.backgroundRect.right,
                    quoteSpec.backgroundRect.bottom
            );
            float r = quoteSpec.cornerRadiusPx;
            float[] radii = {0f, 0f, r, r, r, r, 0f, 0f};
            quoteBgPath.addRoundRect(bgRectF, radii, Path.Direction.CW);
        }
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (spec == null || spec.spannedText == null || spec.textLayout == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downLinkUrl = SinglePassRichTextView.resolveUrlAtTouch(
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
                    String upUrl = SinglePassRichTextView.resolveUrlAtTouch(
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

    private final class QuoteExploreHelper extends ExploreByTouchHelper {

        QuoteExploreHelper(@NonNull View host) {
            super(host);
        }

        @Override
        protected int getVirtualViewAt(float x, float y) {
            if (spec == null) return INVALID_ID;
            return spec.backgroundRect.contains(Math.round(x), Math.round(y)) ? VIRTUAL_QUOTE : INVALID_ID;
        }

        @Override
        protected void getVisibleVirtualViews(@NonNull List<Integer> virtualViewIds) {
            if (spec != null && boundBlock != null) {
                virtualViewIds.add(VIRTUAL_QUOTE);
            }
        }

        @Override
        protected void onPopulateNodeForVirtualView(int virtualViewId, @NonNull AccessibilityNodeInfoCompat node) {
            Rect bounds = new Rect();
            if (spec != null && boundBlock != null) {
                spec.backgroundRect.toAndroidRect(bounds);
                node.setContentDescription("Trích dẫn: " + boundBlock.getStyledText().getText());
            } else {
                node.setContentDescription("");
            }
            if (bounds.isEmpty()) bounds.set(0, 0, 1, 1);
            node.setBoundsInParent(bounds);
            node.setClickable(false);
        }

        @Override
        protected boolean onPerformActionForVirtualView(int virtualViewId, int action, @Nullable Bundle arguments) {
            return false;
        }
    }
}
