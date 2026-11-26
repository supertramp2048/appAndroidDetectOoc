package com.socialapp.core.ui.blocks.view;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.RenderNode;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
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
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.socialapp.R;
import com.socialapp.core.ui.blocks.adapter.BlockImageOptions;
import com.socialapp.core.ui.blocks.layout.BlockPrecomputer;
import com.socialapp.core.ui.blocks.layout.MeasuredCommentSpec;
import com.socialapp.core.ui.blocks.layout.StyleId;
import com.socialapp.core.ui.blocks.layout.TextPaintRegistry;
import com.socialapp.core.ui.blocks.model.BlockPayloads;
import com.socialapp.core.ui.blocks.model.CommentBlock;

import java.util.List;

/**
 * Single-pass flattened Custom View replacing the 17-View item_comment.xml hierarchy.
 * Supports depth 0..2 indentation, RenderNode caching (API 29+), 48dp hit-testing,
 * and ExploreByTouchHelper virtual accessibility nodes.
 */
public class SinglePassCommentView extends View {

    public enum HitTarget {
        AVATAR,
        AUTHOR,
        MENU,
        LOAD_REPLIES,
        REPLY,
        NONE
    }

    public interface OnCommentInteractionListener {
        void onAuthorClick(@NonNull CommentBlock block);
        void onReplyClick(@NonNull CommentBlock block);
        void onLoadRepliesClick(@NonNull CommentBlock block);
        void onCollapseRepliesClick(@NonNull CommentBlock block);
        void onMenuClick(@NonNull CommentBlock block, @NonNull View anchorView);
    }

    private static final int VIRTUAL_AVATAR = 1;
    private static final int VIRTUAL_AUTHOR = 2;
    private static final int VIRTUAL_BODY = 3;
    private static final int VIRTUAL_REPLY = 4;
    private static final int VIRTUAL_LOAD_REPLIES = 5;
    private static final int VIRTUAL_MENU = 6;

    private final Paint dividerPaint = new Paint();
    private final Rect scratchBounds = new Rect();
    private final int touchSlop;
    @Nullable
    private final AccessibilityManager accessibilityManager;

    @Nullable
    private final Drawable avatarPlaceholder;
    @Nullable
    private final Drawable menuIconDrawable;
    private final CommentExploreTouchHelper touchHelper;

    private TextPaintRegistry paintRegistry;
    private BlockPrecomputer precomputer;
    @Nullable
    private RequestManager externalRequestManager;
    @Nullable
    private CommentBlock boundBlock;
    @Nullable
    private MeasuredCommentSpec spec;
    @Nullable
    private OnCommentInteractionListener listener;

    @Nullable
    private Object renderNodeObj; // android.graphics.RenderNode on API 29+
    private boolean renderNodeDirty = true;

    @Nullable
    private Drawable avatarDrawable;
    @Nullable
    private String boundAvatarUrl;
    @Nullable
    private ReusableAvatarTarget avatarTarget;
    private int avatarTargetSizePx = -1;

    private boolean isPointerDown = false;
    private float touchDownX;
    private float touchDownY;
    private HitTarget downHitTarget = HitTarget.NONE;

    public SinglePassCommentView(
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

        dividerPaint.setColor(0xFFD8E0EA);
        dividerPaint.setStyle(Paint.Style.FILL);

        Drawable placeholder = AppCompatResources.getDrawable(context, R.drawable.ic_profile_placeholder);
        this.avatarPlaceholder = placeholder != null ? placeholder.mutate() : null;
        this.avatarDrawable = this.avatarPlaceholder;

        Drawable rawMenu = AppCompatResources.getDrawable(context, R.drawable.ic_retake_24);
        if (rawMenu != null) {
            Drawable wrapped = DrawableCompat.wrap(rawMenu.mutate());
            DrawableCompat.setTint(wrapped, 0xFF161515);
            this.menuIconDrawable = wrapped;
        } else {
            this.menuIconDrawable = null;
        }

        setFocusable(true);
        this.touchHelper = new CommentExploreTouchHelper(this);
        ViewCompat.setAccessibilityDelegate(this, this.touchHelper);
    }

    public void setDependencies(@NonNull TextPaintRegistry paintRegistry, @NonNull BlockPrecomputer precomputer) {
        this.paintRegistry = paintRegistry;
        this.precomputer = precomputer;
    }

    public void setRequestManager(@Nullable RequestManager requestManager) {
        this.externalRequestManager = requestManager;
    }

    public void setInteractionListener(@Nullable OnCommentInteractionListener listener) {
        this.listener = listener;
    }

    public void bind(@NonNull CommentBlock block, int targetWidthPx) {
        CommentBlock previousBlock = this.boundBlock;
        this.boundBlock = block;

        int safeWidth = resolveTargetWidth(targetWidthPx);
        float density = getResources().getDisplayMetrics().density;
        float fontScale = getResources().getConfiguration().fontScale;

        MeasuredCommentSpec prevSpec = this.spec;
        if (previousBlock != null
                && prevSpec != null
                && previousBlock.getCommentId() == block.getCommentId()
                && previousBlock.getContentHash() == block.getContentHash()
                && prevSpec.matchesWidthAndScale(safeWidth, fontScale)) {
            if (!equalsNullable(boundAvatarUrl, block.getAuthorAvatarUrl())) {
                updateAvatarTargetIfNeeded(block);
            }
            return;
        }

        boolean sameLayoutStructure = previousBlock != null
                && prevSpec != null
                && previousBlock.getCommentId() == block.getCommentId()
                && previousBlock.getLayoutHash() == block.getLayoutHash()
                && prevSpec.matchesWidthAndScale(safeWidth, fontScale);

        if (sameLayoutStructure) {
            this.spec = precomputer.updateCommentRepliesState(
                    prevSpec,
                    block.getContentHash(),
                    block.getRepliesCount(),
                    block.isRepliesExpanded(),
                    density,
                    fontScale
            );
            this.renderNodeDirty = true;
            updateAvatarTargetIfNeeded(block);
            invalidateAccessibilityIfEnabled();
            invalidate();
            return;
        }

        MeasuredCommentSpec nextSpec = precomputer.precomputeComment(block, safeWidth, density, fontScale);
        boolean heightChanged = (prevSpec == null || prevSpec.getTotalHeightPx() != nextSpec.getTotalHeightPx());
        this.spec = nextSpec;
        this.renderNodeDirty = true;
        updateAvatarTargetIfNeeded(block);
        invalidateAccessibilityIfEnabled();

        if (heightChanged) {
            requestLayout();
        }
        invalidate();
    }

    public void applyRepliesPayload(@NonNull CommentBlock updatedBlock, @NonNull BlockPayloads.CommentRepliesPayload payload) {
        this.boundBlock = updatedBlock;
        if (this.spec == null) {
            bind(updatedBlock, getWidth());
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        float fontScale = getResources().getConfiguration().fontScale;
        this.spec = precomputer.updateCommentRepliesState(
                this.spec,
                updatedBlock.getContentHash(),
                payload.repliesCount,
                payload.repliesExpanded,
                density,
                fontScale
        );
        this.renderNodeDirty = true;
        invalidateAccessibilityIfEnabled();
        invalidate();
    }

    public void clearGlideTargets() {
        RequestManager rm = resolveRequestManager();
        if (rm != null && avatarTarget != null) {
            try {
                rm.clear(avatarTarget);
            } catch (Throwable ignored) {
            }
        }
        if (avatarTarget != null) {
            avatarTarget.expectedUrl = null;
        }
        boundAvatarUrl = null;
        avatarDrawable = avatarPlaceholder;
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
                spec = precomputer.precomputeComment(boundBlock, width, density, fontScale);
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

        if (!spec.avatarRect.isEmpty() && avatarDrawable != null) {
            // Avoid per-frame oval canvas.clipPath() GPU stall; Glide .circleCrop() pre-masks the avatar.
            spec.avatarRect.toAndroidRect(scratchBounds);
            avatarDrawable.setBounds(scratchBounds);
            avatarDrawable.draw(canvas);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void drawWithRenderNode(@NonNull Canvas canvas, @NonNull MeasuredCommentSpec commentSpec) {
        if (!canvas.isHardwareAccelerated()) {
            drawStaticContent(canvas, commentSpec);
            return;
        }
        RenderNode node = (RenderNode) renderNodeObj;
        if (node == null) {
            node = new RenderNode("SinglePassCommentNode");
            renderNodeObj = node;
            renderNodeDirty = true;
        }

        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        node.setPosition(0, 0, w, h);

        if (renderNodeDirty || !node.hasDisplayList()) {
            RecordingCanvas recordingCanvas = node.beginRecording(w, h);
            try {
                drawStaticContent(recordingCanvas, commentSpec);
            } finally {
                node.endRecording();
            }
            renderNodeDirty = false;
        }

        canvas.drawRenderNode(node);
    }

    private void drawStaticContent(@NonNull Canvas canvas, @NonNull MeasuredCommentSpec commentSpec) {
        float density = getResources().getDisplayMetrics().density;
        float fontScale = commentSpec.getFontScale();
        TextPaintRegistry.PaintSnapshot paints = paintRegistry.getSnapshot(density, fontScale);

        canvas.drawText(
                commentSpec.authorText,
                commentSpec.authorX,
                commentSpec.authorBaselineY,
                paints.get(StyleId.COMMENT_AUTHOR)
        );

        if (!commentSpec.formattedTimeText.isEmpty()) {
            canvas.drawText(
                    commentSpec.formattedTimeText,
                    commentSpec.timeX,
                    commentSpec.timeBaselineY,
                    paints.get(StyleId.COMMENT_TIME)
            );
        }

        if (commentSpec.isEdited) {
            canvas.drawText(
                    "(đã sửa)",
                    commentSpec.editedX,
                    commentSpec.editedBaselineY,
                    paints.get(StyleId.COMMENT_EDITED)
            );
        }

        if (menuIconDrawable != null && !commentSpec.menuRect.isEmpty()) {
            commentSpec.menuRect.toAndroidRect(scratchBounds);
            menuIconDrawable.setBounds(scratchBounds);
            menuIconDrawable.draw(canvas);
        }

        if (commentSpec.isRemoved) {
            canvas.drawText(
                    "Bình luận đã bị gỡ",
                    commentSpec.removedX,
                    commentSpec.removedBaselineY,
                    paints.get(StyleId.COMMENT_REMOVED)
            );
        } else {
            StaticLayout bodyLayout = commentSpec.contentLayout;
            if (bodyLayout != null) {
                canvas.save();
                canvas.translate(commentSpec.contentRect.left, commentSpec.contentRect.top);
                bodyLayout.draw(canvas);
                canvas.restore();
            }
        }

        canvas.drawText(
                "Trả lời",
                commentSpec.replyX,
                commentSpec.replyBaselineY,
                paints.get(StyleId.COMMENT_ACTION)
        );

        if (commentSpec.hasRepliesButton && commentSpec.repliesButtonText != null) {
            canvas.drawText(
                    commentSpec.repliesButtonText,
                    commentSpec.loadRepliesX,
                    commentSpec.loadRepliesBaselineY,
                    paints.get(StyleId.COMMENT_ACTION)
            );
        }

        canvas.drawRect(
                commentSpec.dividerStartX,
                commentSpec.dividerY,
                commentSpec.dividerEndX,
                commentSpec.dividerY + commentSpec.dividerStrokePx,
                dividerPaint
        );
    }

    private void updateAvatarTargetIfNeeded(@NonNull CommentBlock block) {
        String nextAvatarUrl = block.getAuthorAvatarUrl();
        if (equalsNullable(boundAvatarUrl, nextAvatarUrl)) {
            return;
        }
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
                    : BlockPrecomputer.dpToPx(BlockPrecomputer.COMMENT_AVATAR_SIZE_DP, getResources().getDisplayMetrics().density));
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
        if (externalRequestManager != null) {
            return externalRequestManager;
        }
        Context ctx = getContext();
        if (!isContextValidForGlide(ctx)) {
            return null;
        }
        try {
            return Glide.with(this);
        } catch (Throwable ignored) {
            return null;
        }
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

    // --- Pure Hit-Testing Resolver ---

    @NonNull
    public static HitTarget resolveHitTarget(
            @Nullable MeasuredCommentSpec spec,
            boolean hasNavigableAuthor,
            int x,
            int y) {
        if (spec == null) {
            return HitTarget.NONE;
        }
        if (hasNavigableAuthor && spec.avatarTouchRect.contains(x, y)) {
            return HitTarget.AVATAR;
        }
        if (hasNavigableAuthor && spec.authorTouchRect.contains(x, y)) {
            return HitTarget.AUTHOR;
        }
        if (spec.menuTouchRect.contains(x, y)) {
            return HitTarget.MENU;
        }
        if (spec.hasRepliesButton && spec.loadRepliesTouchRect.contains(x, y)) {
            return HitTarget.LOAD_REPLIES;
        }
        if (spec.replyTouchRect.contains(x, y)) {
            return HitTarget.REPLY;
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
                downHitTarget = resolveHitTarget(
                        spec,
                        boundBlock.getAuthorId() > 0,
                        Math.round(event.getX()),
                        Math.round(event.getY())
                );
                if (downHitTarget == HitTarget.NONE) {
                    return false;
                }
                isPointerDown = true;
                touchDownX = event.getX();
                touchDownY = event.getY();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (isPointerDown) {
                    float dx = event.getX() - touchDownX;
                    float dy = event.getY() - touchDownY;
                    if ((dx * dx + dy * dy) > (touchSlop * touchSlop)) {
                        isPointerDown = false;
                        downHitTarget = HitTarget.NONE;
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (isPointerDown) {
                    isPointerDown = false;
                    HitTarget upTarget = resolveHitTarget(
                            spec,
                            boundBlock.getAuthorId() > 0,
                            Math.round(event.getX()),
                            Math.round(event.getY())
                    );
                    HitTarget effectiveTarget = upTarget != HitTarget.NONE ? upTarget : downHitTarget;
                    downHitTarget = HitTarget.NONE;
                    performClick();
                    dispatchHitTarget(effectiveTarget);
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                isPointerDown = false;
                downHitTarget = HitTarget.NONE;
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
            case MENU:
                listener.onMenuClick(boundBlock, this);
                break;
            case LOAD_REPLIES:
                if (boundBlock.isRepliesExpanded()) {
                    listener.onCollapseRepliesClick(boundBlock);
                } else {
                    listener.onLoadRepliesClick(boundBlock);
                }
                break;
            case REPLY:
                listener.onReplyClick(boundBlock);
                break;
            case NONE:
            default:
                break;
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

    private final class CommentExploreTouchHelper extends ExploreByTouchHelper {

        CommentExploreTouchHelper(@NonNull View host) {
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
                case MENU:
                    return VIRTUAL_MENU;
                case LOAD_REPLIES:
                    return VIRTUAL_LOAD_REPLIES;
                case REPLY:
                    return VIRTUAL_REPLY;
                default:
                    if (spec.contentRect.contains(Math.round(x), Math.round(y))) {
                        return VIRTUAL_BODY;
                    }
                    return INVALID_ID;
            }
        }

        @Override
        protected void getVisibleVirtualViews(@NonNull List<Integer> virtualViewIds) {
            if (spec == null || boundBlock == null) {
                return;
            }
            if (boundBlock.getAuthorId() > 0) {
                virtualViewIds.add(VIRTUAL_AVATAR);
                virtualViewIds.add(VIRTUAL_AUTHOR);
            }
            virtualViewIds.add(VIRTUAL_BODY);
            virtualViewIds.add(VIRTUAL_REPLY);
            if (spec.hasRepliesButton) {
                virtualViewIds.add(VIRTUAL_LOAD_REPLIES);
            }
            virtualViewIds.add(VIRTUAL_MENU);
        }

        @Override
        protected void onPopulateNodeForVirtualView(int virtualViewId, @NonNull AccessibilityNodeInfoCompat node) {
            if (spec == null || boundBlock == null) {
                node.setContentDescription("");
                node.setBoundsInParent(new Rect(0, 0, 1, 1));
                return;
            }
            Rect bounds = new Rect();
            boolean clickable = true;
            switch (virtualViewId) {
                case VIRTUAL_AVATAR:
                    spec.avatarTouchRect.toAndroidRect(bounds);
                    node.setContentDescription("Ảnh đại diện " + spec.authorText);
                    break;
                case VIRTUAL_AUTHOR:
                    spec.authorTouchRect.toAndroidRect(bounds);
                    node.setContentDescription(spec.authorText + " " + spec.formattedTimeText);
                    break;
                case VIRTUAL_BODY:
                    spec.contentRect.toAndroidRect(bounds);
                    node.setContentDescription(spec.isRemoved ? "Bình luận đã bị gỡ" : boundBlock.getContent());
                    clickable = false;
                    break;
                case VIRTUAL_REPLY:
                    spec.replyTouchRect.toAndroidRect(bounds);
                    node.setContentDescription("Trả lời bình luận của " + spec.authorText);
                    break;
                case VIRTUAL_LOAD_REPLIES:
                    spec.loadRepliesTouchRect.toAndroidRect(bounds);
                    node.setContentDescription(spec.repliesButtonText != null ? spec.repliesButtonText : "Xem phản hồi");
                    break;
                case VIRTUAL_MENU:
                default:
                    spec.menuTouchRect.toAndroidRect(bounds);
                    node.setContentDescription("Tùy chọn bình luận");
                    break;
            }
            if (bounds.isEmpty()) {
                bounds.set(0, 0, 1, 1);
            }
            node.setBoundsInParent(bounds);
            node.setClickable(clickable);
            if (clickable) {
                node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
            }
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
                case VIRTUAL_MENU:
                    dispatchHitTarget(HitTarget.MENU);
                    break;
                case VIRTUAL_LOAD_REPLIES:
                    dispatchHitTarget(HitTarget.LOAD_REPLIES);
                    break;
                case VIRTUAL_REPLY:
                    dispatchHitTarget(HitTarget.REPLY);
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

    private static boolean isContextValidForGlide(@Nullable Context context) {
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

    private static boolean equalsNullable(@Nullable String a, @Nullable String b) {
        return a == null ? b == null : a.equals(b);
    }
}
