package com.socialapp.core.ui.blocks.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.StaticLayout;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import com.bumptech.glide.RequestManager;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.socialapp.R;
import com.socialapp.core.ui.blocks.adapter.BlockImageOptions;
import com.socialapp.core.ui.blocks.adapter.PooledViewContextHelper;
import com.socialapp.core.ui.blocks.layout.BlockPrecomputer;
import com.socialapp.core.ui.blocks.layout.BlockRect;
import com.socialapp.core.ui.blocks.layout.MeasuredCardSpec;
import com.socialapp.core.ui.blocks.layout.StyleId;
import com.socialapp.core.ui.blocks.layout.TextPaintRegistry;
import com.socialapp.core.ui.blocks.model.BlockPayloads;
import com.socialapp.core.ui.blocks.model.FeedPostBlock;

import java.util.List;

/**
 * Single-pass flattened Custom View replacing the 19-View MaterialCardView hierarchy of PostCardView.
 * - O(1) onMeasure() with automatic Resize Guard
 * - API 29+ RenderNode caching for static/semi-static editorial content (API 24-28 Canvas fallback)
 * - Material Ripple drawn strictly OUTSIDE RenderNode
 * - 48dp hit-testing prioritizing child targets before cardRect
 * - ExploreByTouchHelper virtual accessibility nodes for TalkBack & Espresso
 */
public class SinglePassFeedPostView extends View {

    public enum HitTarget {
        AVATAR,
        AUTHOR,
        COMMENTS,
        CARD,
        NONE
    }

    public interface OnFeedPostInteractionListener {
        void onCardClick(@NonNull FeedPostBlock block);
        void onAuthorClick(@NonNull FeedPostBlock block);
        void onCommentsClick(@NonNull FeedPostBlock block);
    }

    private static final int VIRTUAL_AVATAR = 1;
    private static final int VIRTUAL_AUTHOR = 2;
    private static final int VIRTUAL_COMMENTS = 3;
    private static final int VIRTUAL_CARD = 4;

    private final Paint dividerPaint = new Paint();
    private final Paint thumbBgPaint = new Paint();
    private final Paint thumbBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF thumbRectF = new RectF();
    private final Rect scratchBounds = new Rect();

    private final int touchSlop;
    @Nullable
    private final AccessibilityManager accessibilityManager;
    @Nullable
    private final Drawable rippleDrawable;
    @Nullable
    private final Drawable avatarPlaceholder;
    private final FeedExploreTouchHelper touchHelper;

    private TextPaintRegistry paintRegistry;
    private BlockPrecomputer precomputer;
    @Nullable
    private RequestManager externalRequestManager;
    @Nullable
    private FeedPostBlock boundBlock;
    @Nullable
    private MeasuredCardSpec spec;
    @Nullable
    private OnFeedPostInteractionListener listener;

    @Nullable
    private Object renderNodeObj; // android.graphics.RenderNode on API 29+
    private boolean renderNodeDirty = true;

    @Nullable
    private Drawable avatarDrawable;
    @Nullable
    private Drawable thumbnailDrawable;
    @Nullable
    private String boundAvatarUrl;
    @Nullable
    private String boundThumbnailUrl;
    @Nullable
    private ReusableAvatarTarget avatarTarget;
    private int avatarTargetSizePx = -1;
    @Nullable
    private ReusableThumbnailTarget thumbnailTarget;
    private int thumbTargetWidthPx = -1;
    private int thumbTargetHeightPx = -1;

    private boolean isPointerDown = false;
    private float touchDownX;
    private float touchDownY;

    public SinglePassFeedPostView(
            @NonNull Context context,
            @NonNull TextPaintRegistry paintRegistry,
            @NonNull BlockPrecomputer precomputer) {
        super(context);
        this.paintRegistry = paintRegistry;
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

        float density = getResources().getDisplayMetrics().density;
        dividerPaint.setColor(0xFFD8E0EA);
        dividerPaint.setStyle(Paint.Style.FILL);

        thumbBgPaint.setColor(0xFFEEF2F6);
        thumbBgPaint.setStyle(Paint.Style.FILL);

        thumbBorderPaint.setColor(0xFFD8E0EA);
        thumbBorderPaint.setStyle(Paint.Style.STROKE);
        thumbBorderPaint.setStrokeWidth(Math.max(1f, density));

        Drawable ripple = resolveSelectableItemBackground(context);
        if (ripple != null) {
            ripple = ripple.mutate();
            ripple.setCallback(this);
        }
        this.rippleDrawable = ripple;

        Drawable placeholder = AppCompatResources.getDrawable(context, R.drawable.ic_profile_placeholder);
        this.avatarPlaceholder = placeholder != null ? placeholder.mutate() : null;
        this.avatarDrawable = this.avatarPlaceholder;

        setClickable(true);
        setFocusable(true);
        this.touchHelper = new FeedExploreTouchHelper(this);
        ViewCompat.setAccessibilityDelegate(this, this.touchHelper);
    }

    public void setDependencies(@NonNull TextPaintRegistry paintRegistry, @NonNull BlockPrecomputer precomputer) {
        this.paintRegistry = paintRegistry;
        this.precomputer = precomputer;
    }

    public void setRequestManager(@Nullable RequestManager requestManager) {
        this.externalRequestManager = requestManager;
    }

    public void setInteractionListener(@Nullable OnFeedPostInteractionListener listener) {
        this.listener = listener;
    }

    public void bind(@NonNull FeedPostBlock block, int targetWidthPx) {
        FeedPostBlock previousBlock = this.boundBlock;
        this.boundBlock = block;

        int safeWidth = resolveTargetWidth(targetWidthPx);
        float density = getResources().getDisplayMetrics().density;
        float fontScale = getResources().getConfiguration().fontScale;

        MeasuredCardSpec prevSpec = this.spec;
        if (previousBlock != null
                && prevSpec != null
                && previousBlock.getPostId() == block.getPostId()
                && previousBlock.getContentHash() == block.getContentHash()
                && prevSpec.matchesWidthAndScale(safeWidth, fontScale)) {
            String nextAvatarUrl = block.getAuthorName() != null ? block.getAuthorAvatarUrl() : null;
            String nextThumbUrl = block.getThumbnailUrl();
            if (!equalsNullable(boundAvatarUrl, nextAvatarUrl)
                    || !equalsNullable(boundThumbnailUrl, nextThumbUrl)) {
                updateImageTargetsIfNeeded(block);
            }
            return;
        }

        boolean sameLayoutStructure = previousBlock != null
                && prevSpec != null
                && previousBlock.getPostId() == block.getPostId()
                && previousBlock.getLayoutHash() == block.getLayoutHash()
                && prevSpec.matchesWidthAndScale(safeWidth, fontScale);

        if (sameLayoutStructure) {
            this.spec = precomputer.updateFeedCardMetrics(
                    prevSpec,
                    block.getContentHash(),
                    block.getVoteScore(),
                    block.getCommentsCount(),
                    density,
                    fontScale
            );
            this.renderNodeDirty = true;
            updateImageTargetsIfNeeded(block);
            invalidateAccessibilityIfEnabled();
            invalidate();
            return;
        }

        MeasuredCardSpec nextSpec = precomputer.precomputeFeedPost(block, safeWidth, density, fontScale);
        boolean heightChanged = (prevSpec == null || prevSpec.getTotalHeightPx() != nextSpec.getTotalHeightPx());
        this.spec = nextSpec;
        this.renderNodeDirty = true;
        updateImageTargetsIfNeeded(block);
        invalidateAccessibilityIfEnabled();

        if (heightChanged) {
            requestLayout();
        }
        invalidate();
    }

    public void applyPayload(@NonNull FeedPostBlock updatedBlock, @NonNull BlockPayloads.FeedMetricsPayload payload) {
        this.boundBlock = updatedBlock;
        if (this.spec == null) {
            bind(updatedBlock, getWidth());
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        float fontScale = getResources().getConfiguration().fontScale;
        this.spec = precomputer.updateFeedCardMetrics(
                this.spec,
                updatedBlock.getContentHash(),
                payload.voteScore,
                payload.commentsCount,
                density,
                fontScale
        );
        this.renderNodeDirty = true;
        invalidateAccessibilityIfEnabled();
        invalidate();
    }

    public void clearGlideTargets() {
        RequestManager rm = resolveRequestManager();
        if (rm != null) {
            try {
                if (avatarTarget != null) {
                    rm.clear(avatarTarget);
                }
                if (thumbnailTarget != null) {
                    rm.clear(thumbnailTarget);
                }
            } catch (Throwable ignored) {
            }
        }
        if (avatarTarget != null) {
            avatarTarget.expectedUrl = null;
        }
        if (thumbnailTarget != null) {
            thumbnailTarget.expectedUrl = null;
        }
        boundAvatarUrl = null;
        boundThumbnailUrl = null;
        avatarDrawable = avatarPlaceholder;
        thumbnailDrawable = null;
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
            // Resize Guard: recompute once if width/fontScale changed (rotation / split-screen)
            if (spec == null
                    || !spec.matchesWidthAndScale(width, fontScale)
                    || spec.getContentHash() != boundBlock.getContentHash()) {
                spec = precomputer.precomputeFeedPost(boundBlock, width, density, fontScale);
                renderNodeDirty = true;
                invalidateAccessibilityIfEnabled();
            }
        }

        int height = spec != null ? spec.getTotalHeightPx() : 0;
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (rippleDrawable != null) {
            rippleDrawable.setBounds(0, 0, w, h);
        }
        if (w != oldw || h != oldh) {
            renderNodeDirty = true;
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (spec == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            drawWithRenderNode(canvas, spec);
        } else {
            drawStaticContent(canvas, spec);
        }

        drawDynamicMedia(canvas, spec);

        if (rippleDrawable != null) {
            rippleDrawable.draw(canvas);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void drawWithRenderNode(@NonNull Canvas canvas, @NonNull MeasuredCardSpec cardSpec) {
        if (!canvas.isHardwareAccelerated()) {
            drawStaticContent(canvas, cardSpec);
            return;
        }
        RenderNode node = (RenderNode) renderNodeObj;
        if (node == null) {
            node = new RenderNode("SinglePassFeedPostNode");
            renderNodeObj = node;
            renderNodeDirty = true;
        }

        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        node.setPosition(0, 0, w, h);

        if (renderNodeDirty || !node.hasDisplayList()) {
            RecordingCanvas recordingCanvas = node.beginRecording(w, h);
            try {
                drawStaticContent(recordingCanvas, cardSpec);
            } finally {
                node.endRecording();
            }
            renderNodeDirty = false;
        }

        canvas.drawRenderNode(node);
    }

    private void drawStaticContent(@NonNull Canvas canvas, @NonNull MeasuredCardSpec cardSpec) {
        float density = getResources().getDisplayMetrics().density;
        float fontScale = cardSpec.getFontScale();
        TextPaintRegistry.PaintSnapshot paints = paintRegistry.getSnapshot(density, fontScale);

        if (cardSpec.hasCategory && cardSpec.categoryText != null) {
            canvas.drawText(
                    cardSpec.categoryText,
                    cardSpec.categoryX,
                    cardSpec.categoryBaselineY,
                    paints.get(StyleId.FEED_CATEGORY)
            );
        }

        StaticLayout titleLayout = cardSpec.titleLayout;
        if (titleLayout != null) {
            canvas.save();
            canvas.translate(cardSpec.titleRect.left, cardSpec.titleRect.top);
            titleLayout.draw(canvas);
            canvas.restore();
        }

        canvas.drawText(
                cardSpec.authorText,
                cardSpec.authorX,
                cardSpec.authorBaselineY,
                paints.get(StyleId.FEED_BYLINE)
        );

        if (cardSpec.hasTime && cardSpec.timeText != null) {
            canvas.drawText(
                    cardSpec.timeText,
                    cardSpec.timeX,
                    cardSpec.timeBaselineY,
                    paints.get(StyleId.FEED_TIME)
            );
        }

        canvas.drawText(
                cardSpec.voteText,
                cardSpec.voteX,
                cardSpec.voteBaselineY,
                paints.get(cardSpec.voteStyleId)
        );

        canvas.drawText(
                cardSpec.commentsText,
                cardSpec.commentsX,
                cardSpec.commentsBaselineY,
                paints.get(StyleId.FEED_COMMENTS)
        );

        if (cardSpec.hasSource && cardSpec.sourceText != null) {
            canvas.drawText(
                    cardSpec.sourceText,
                    cardSpec.sourceX,
                    cardSpec.sourceBaselineY,
                    paints.get(StyleId.FEED_SOURCE)
            );
        }

        canvas.drawRect(
                cardSpec.padX,
                cardSpec.dividerY,
                Math.max(cardSpec.padX, getWidth() - cardSpec.padX),
                cardSpec.dividerY + cardSpec.dividerStrokePx,
                dividerPaint
        );
    }

    private void drawDynamicMedia(@NonNull Canvas canvas, @NonNull MeasuredCardSpec cardSpec) {
        // Avoid canvas.clipPath(oval/roundRect) on every frame — non-rect Path clipping forces
        // Skia OpenGL to allocate off-screen RenderTargets and stalls GPU command submission.
        // Avatars are pre-circularized via Glide .circleCrop() and thumbnails via RoundedCorners().
        if (cardSpec.hasAvatar && !cardSpec.avatarRect.isEmpty() && avatarDrawable != null) {
            cardSpec.avatarRect.toAndroidRect(scratchBounds);
            avatarDrawable.setBounds(scratchBounds);
            avatarDrawable.draw(canvas);
        }

        if (cardSpec.hasThumbnail && !cardSpec.thumbnailRect.isEmpty()) {
            cardSpec.thumbnailRect.toAndroidRect(scratchBounds);
            thumbRectF.set(scratchBounds);
            if (thumbnailDrawable != null) {
                thumbnailDrawable.setBounds(scratchBounds);
                thumbnailDrawable.draw(canvas);
            } else {
                canvas.drawRoundRect(thumbRectF, cardSpec.thumbnailRadiusPx, cardSpec.thumbnailRadiusPx, thumbBgPaint);
            }
            canvas.drawRoundRect(thumbRectF, cardSpec.thumbnailRadiusPx, cardSpec.thumbnailRadiusPx, thumbBorderPaint);
        }
    }

    private void updateImageTargetsIfNeeded(@NonNull FeedPostBlock block) {
        String nextAvatarUrl = block.getAuthorName() != null ? block.getAuthorAvatarUrl() : null;
        if (!equalsNullable(boundAvatarUrl, nextAvatarUrl)) {
            boundAvatarUrl = nextAvatarUrl;
            RequestManager rm = resolveRequestManager();
            if (rm != null && avatarTarget != null) {
                try {
                    rm.clear(avatarTarget);
                } catch (Throwable ignored) {
                }
            }
            if (avatarTarget != null) {
                avatarTarget.expectedUrl = nextAvatarUrl;
            }
            avatarDrawable = avatarPlaceholder;

            if (nextAvatarUrl != null && rm != null) {
                int size = Math.max(1, spec != null && !spec.avatarRect.isEmpty()
                        ? spec.avatarRect.width()
                        : BlockPrecomputer.dpToPx(BlockPrecomputer.FEED_AVATAR_SIZE_DP, getResources().getDisplayMetrics().density));
                if (avatarTarget == null || avatarTargetSizePx != size) {
                    avatarTarget = new ReusableAvatarTarget(size);
                    avatarTargetSizePx = size;
                }
                avatarTarget.expectedUrl = nextAvatarUrl;
                try {
                    rm.load(nextAvatarUrl)
                            .apply(BlockImageOptions.avatarOptions(size))
                            .into(avatarTarget);
                } catch (Throwable ignored) {
                }
            }
        }

        String nextThumbUrl = block.getThumbnailUrl();
        if (!equalsNullable(boundThumbnailUrl, nextThumbUrl)) {
            boundThumbnailUrl = nextThumbUrl;
            RequestManager rm = resolveRequestManager();
            if (rm != null && thumbnailTarget != null) {
                try {
                    rm.clear(thumbnailTarget);
                } catch (Throwable ignored) {
                }
            }
            if (thumbnailTarget != null) {
                thumbnailTarget.expectedUrl = nextThumbUrl;
            }
            thumbnailDrawable = null;

            if (nextThumbUrl != null && rm != null) {
                float density = getResources().getDisplayMetrics().density;
                int w = Math.max(1, spec != null && !spec.thumbnailRect.isEmpty()
                        ? spec.thumbnailRect.width()
                        : BlockPrecomputer.dpToPx(BlockPrecomputer.FEED_THUMB_WIDTH_DP, density));
                int h = Math.max(1, spec != null && !spec.thumbnailRect.isEmpty()
                        ? spec.thumbnailRect.height()
                        : BlockPrecomputer.dpToPx(BlockPrecomputer.FEED_THUMB_HEIGHT_DP, density));
                int radiusPx = Math.max(1, spec != null ? spec.thumbnailRadiusPx : BlockPrecomputer.dpToPx(4, density));
                if (thumbnailTarget == null || thumbTargetWidthPx != w || thumbTargetHeightPx != h) {
                    thumbnailTarget = new ReusableThumbnailTarget(w, h);
                    thumbTargetWidthPx = w;
                    thumbTargetHeightPx = h;
                }
                thumbnailTarget.expectedUrl = nextThumbUrl;
                try {
                    rm.load(nextThumbUrl)
                            .apply(BlockImageOptions.feedThumbnailOptions(w, h, radiusPx))
                            .into(thumbnailTarget);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private boolean isAccessibilityEnabled() {
        return accessibilityManager != null && accessibilityManager.isEnabled();
    }

    private void invalidateAccessibilityIfEnabled() {
        if (isAccessibilityEnabled()) {
            touchHelper.invalidateRoot();
        }
    }

    @Nullable
    private RequestManager resolveRequestManager() {
        return PooledViewContextHelper.resolveSafeRequestManager(this, externalRequestManager);
    }

    private final class ReusableAvatarTarget extends CustomTarget<Drawable> {
        @Nullable
        String expectedUrl;

        ReusableAvatarTarget(int sizePx) {
            super(sizePx, sizePx);
        }

        @Override
        public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
            if (!equalsNullable(boundAvatarUrl, expectedUrl)) return;
            avatarDrawable = resource;
            invalidate();
        }

        @Override
        public void onLoadFailed(@Nullable Drawable errorDrawable) {
            if (!equalsNullable(boundAvatarUrl, expectedUrl)) return;
            avatarDrawable = errorDrawable != null ? errorDrawable : avatarPlaceholder;
            invalidate();
        }

        @Override
        public void onLoadCleared(@Nullable Drawable placeholder) {
            avatarDrawable = placeholder != null ? placeholder : avatarPlaceholder;
            invalidate();
        }
    }

    private final class ReusableThumbnailTarget extends CustomTarget<Drawable> {
        @Nullable
        String expectedUrl;

        ReusableThumbnailTarget(int widthPx, int heightPx) {
            super(widthPx, heightPx);
        }

        @Override
        public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
            if (!equalsNullable(boundThumbnailUrl, expectedUrl)) return;
            thumbnailDrawable = resource;
            invalidate();
        }

        @Override
        public void onLoadCleared(@Nullable Drawable placeholder) {
            thumbnailDrawable = placeholder;
            invalidate();
        }
    }

    // --- Pure Hit-Testing Resolver (Checked in child-before-card order) ---

    @NonNull
    public static HitTarget resolveHitTarget(
            @Nullable MeasuredCardSpec spec,
            boolean hasNavigableAuthor,
            int x,
            int y) {
        if (spec == null) {
            return HitTarget.NONE;
        }
        if (hasNavigableAuthor && spec.hasAvatar && spec.avatarTouchRect.contains(x, y)) {
            return HitTarget.AVATAR;
        }
        if (hasNavigableAuthor && spec.authorTouchRect.contains(x, y)) {
            return HitTarget.AUTHOR;
        }
        if (spec.commentsTouchRect.contains(x, y)) {
            return HitTarget.COMMENTS;
        }
        if (spec.cardRect.contains(x, y)) {
            return HitTarget.CARD;
        }
        return HitTarget.NONE;
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (spec == null || boundBlock == null) {
            return super.onTouchEvent(event);
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                isPointerDown = true;
                touchDownX = event.getX();
                touchDownY = event.getY();
                setPressed(true);
                drawableHotspotChanged(touchDownX, touchDownY);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (isPointerDown) {
                    float dx = event.getX() - touchDownX;
                    float dy = event.getY() - touchDownY;
                    if ((dx * dx + dy * dy) > (touchSlop * touchSlop)) {
                        isPointerDown = false;
                        setPressed(false);
                    } else {
                        drawableHotspotChanged(event.getX(), event.getY());
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (isPointerDown) {
                    isPointerDown = false;
                    setPressed(false);
                    int x = Math.round(event.getX());
                    int y = Math.round(event.getY());
                    HitTarget target = resolveHitTarget(spec, boundBlock.getAuthorId() > 0, x, y);
                    performClick();
                    dispatchHitTarget(target);
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                isPointerDown = false;
                setPressed(false);
                return true;

            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void dispatchHitTarget(@NonNull HitTarget target) {
        if (listener == null || boundBlock == null) {
            return;
        }
        switch (target) {
            case AVATAR:
            case AUTHOR:
                listener.onAuthorClick(boundBlock);
                break;
            case COMMENTS:
                listener.onCommentsClick(boundBlock);
                break;
            case CARD:
                listener.onCardClick(boundBlock);
                break;
            case NONE:
            default:
                break;
        }
    }

    // --- Ripple Drawable Delegation ---

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return super.verifyDrawable(who) || who == rippleDrawable;
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (rippleDrawable != null && rippleDrawable.isStateful()) {
            rippleDrawable.setState(getDrawableState());
        }
    }

    @Override
    public void drawableHotspotChanged(float x, float y) {
        super.drawableHotspotChanged(x, y);
        if (rippleDrawable != null) {
            rippleDrawable.setHotspot(x, y);
        }
    }

    // --- Accessibility Delegation (ExploreByTouchHelper) ---

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

    private final class FeedExploreTouchHelper extends ExploreByTouchHelper {

        FeedExploreTouchHelper(@NonNull View host) {
            super(host);
        }

        @Override
        protected int getVirtualViewAt(float x, float y) {
            if (spec == null || boundBlock == null) {
                return INVALID_ID;
            }
            HitTarget target = resolveHitTarget(spec, boundBlock.getAuthorId() > 0, Math.round(x), Math.round(y));
            switch (target) {
                case AVATAR:
                    return VIRTUAL_AVATAR;
                case AUTHOR:
                    return VIRTUAL_AUTHOR;
                case COMMENTS:
                    return VIRTUAL_COMMENTS;
                case CARD:
                    return VIRTUAL_CARD;
                default:
                    return INVALID_ID;
            }
        }

        @Override
        protected void getVisibleVirtualViews(@NonNull List<Integer> virtualViewIds) {
            if (spec == null || boundBlock == null) {
                return;
            }
            boolean hasAuthor = boundBlock.getAuthorId() > 0;
            if (hasAuthor && spec.hasAvatar) {
                virtualViewIds.add(VIRTUAL_AVATAR);
            }
            if (hasAuthor) {
                virtualViewIds.add(VIRTUAL_AUTHOR);
            }
            virtualViewIds.add(VIRTUAL_COMMENTS);
            virtualViewIds.add(VIRTUAL_CARD);
        }

        @Override
        protected void onPopulateNodeForVirtualView(int virtualViewId, @NonNull AccessibilityNodeInfoCompat node) {
            if (spec == null || boundBlock == null) {
                node.setContentDescription("");
                node.setBoundsInParent(new Rect(0, 0, 1, 1));
                return;
            }
            Rect bounds = new Rect();
            switch (virtualViewId) {
                case VIRTUAL_AVATAR:
                    spec.avatarTouchRect.toAndroidRect(bounds);
                    node.setContentDescription("Ảnh đại diện " + spec.authorText);
                    break;
                case VIRTUAL_AUTHOR:
                    spec.authorTouchRect.toAndroidRect(bounds);
                    node.setContentDescription(spec.authorText);
                    break;
                case VIRTUAL_COMMENTS:
                    spec.commentsTouchRect.toAndroidRect(bounds);
                    node.setContentDescription("Mở bình luận, " + boundBlock.getCommentsCount() + " bình luận");
                    break;
                case VIRTUAL_CARD:
                default:
                    spec.cardRect.toAndroidRect(bounds);
                    node.setContentDescription(boundBlock.getTitle() + ". " + spec.authorText + ". " + spec.voteText);
                    break;
            }
            if (bounds.isEmpty()) {
                bounds.set(0, 0, 1, 1);
            }
            node.setBoundsInParent(bounds);
            node.setClickable(true);
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
        }

        @Override
        protected boolean onPerformActionForVirtualView(int virtualViewId, int action, @Nullable Bundle arguments) {
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK) {
                return false;
            }
            switch (virtualViewId) {
                case VIRTUAL_AVATAR:
                    dispatchHitTarget(HitTarget.AVATAR);
                    break;
                case VIRTUAL_AUTHOR:
                    dispatchHitTarget(HitTarget.AUTHOR);
                    break;
                case VIRTUAL_COMMENTS:
                    dispatchHitTarget(HitTarget.COMMENTS);
                    break;
                case VIRTUAL_CARD:
                    dispatchHitTarget(HitTarget.CARD);
                    break;
                default:
                    return false;
            }
            sendEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED);
            return true;
        }
    }

    private int resolveTargetWidth(int requestedWidthPx) {
        if (requestedWidthPx > 0) {
            return requestedWidthPx;
        }
        if (getWidth() > 0) {
            return getWidth();
        }
        return getResources().getDisplayMetrics().widthPixels;
    }

    @Nullable
    private static Drawable resolveSelectableItemBackground(@NonNull Context context) {
        TypedValue outValue = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)) {
            return AppCompatResources.getDrawable(context, outValue.resourceId);
        }
        return null;
    }

    private static boolean equalsNullable(@Nullable String a, @Nullable String b) {
        return a == null ? b == null : a.equals(b);
    }
}
