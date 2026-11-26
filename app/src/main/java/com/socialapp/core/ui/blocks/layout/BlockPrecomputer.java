package com.socialapp.core.ui.blocks.layout;

import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.socialapp.core.ui.blocks.model.CommentBlock;
import com.socialapp.core.ui.blocks.model.ContentBlock;
import com.socialapp.core.ui.blocks.model.FeedPostBlock;
import com.socialapp.core.ui.blocks.model.QuoteBlock;
import com.socialapp.core.ui.blocks.model.RichTextBlock;
import com.socialapp.core.ui.blocks.parser.SpannableConverter;
import com.socialapp.core.util.DateTimeUtils;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Off-main-thread layout and text shaping precomputer (Section 4.1.3).
 * Uses StaticLayout.Builder (API 23+, safe on minSdk 24) and immutable TextPaint snapshots
 * from TextPaintRegistry to populate BlockLayoutCache without touching Android Views.
 */
public class BlockPrecomputer {

    public static final int FEED_HORIZONTAL_PADDING_DP = 16;
    public static final int FEED_THUMB_WIDTH_DP = 104;
    public static final int FEED_THUMB_HEIGHT_DP = 78;
    public static final int FEED_THUMB_GAP_DP = 14;
    public static final int FEED_AVATAR_SIZE_DP = 22;

    public static final int COMMENT_HORIZONTAL_PADDING_DP = 16;
    public static final int COMMENT_INDENT_PER_DEPTH_DP = 24;
    public static final int COMMENT_AVATAR_SIZE_DP = 34;
    public static final int COMMENT_AVATAR_GAP_DP = 10;
    public static final int COMMENT_MENU_SIZE_DP = 28;

    public static final int DETAIL_HORIZONTAL_PADDING_DP = 18;
    public static final int DETAIL_QUOTE_INNER_PADDING_DP = 20;
    public static final int MIN_TOUCH_TARGET_DP = 48;

    private final TextPaintRegistry paintRegistry;
    private final BlockLayoutCache layoutCache;
    private final ExecutorService workerExecutor;

    public BlockPrecomputer(@NonNull TextPaintRegistry paintRegistry, @NonNull BlockLayoutCache layoutCache) {
        this(paintRegistry, layoutCache, Executors.newFixedThreadPool(2, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);

            @Override
            public Thread newThread(@NonNull Runnable r) {
                Thread t = new Thread(r, "BlockPrecompute-" + counter.getAndIncrement());
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            }
        }));
    }

    public BlockPrecomputer(
            @NonNull TextPaintRegistry paintRegistry,
            @NonNull BlockLayoutCache layoutCache,
            @NonNull ExecutorService workerExecutor) {
        this.paintRegistry = paintRegistry;
        this.layoutCache = layoutCache;
        this.workerExecutor = workerExecutor;
    }

    @NonNull
    public TextPaintRegistry getPaintRegistry() {
        return paintRegistry;
    }

    @NonNull
    public BlockLayoutCache getLayoutCache() {
        return layoutCache;
    }

    // --- Pure Width & Dimension Formulas (Mandated in Section 4.1.3) ---

    public static int dpToPx(float dp, float density) {
        float safeDensity = (Float.isNaN(density) || density <= 0f) ? 1.0f : density;
        return Math.round(dp * safeDensity);
    }

    /**
     * W_title = W_screen - 32dp - I(hasThumb) * (104dp + 14dp)
     */
    public static int computeFeedTitleWidthPx(int screenWidthPx, float density, boolean hasThumbnail) {
        int outerPadding = dpToPx(FEED_HORIZONTAL_PADDING_DP * 2, density);
        int thumbReservation = hasThumbnail
                ? (dpToPx(FEED_THUMB_WIDTH_DP, density) + dpToPx(FEED_THUMB_GAP_DP, density))
                : 0;
        return Math.max(1, screenWidthPx - outerPadding - thumbReservation);
    }

    /**
     * W_comment(d) = W_screen - 32dp - d * 24dp - 34dp (avatar) - 10dp (gap)
     */
    public static int computeCommentBodyWidthPx(int screenWidthPx, float density, int depth) {
        int clampedDepth = Math.max(0, Math.min(depth, CommentBlock.MAX_DEPTH));
        int outerPadding = dpToPx(COMMENT_HORIZONTAL_PADDING_DP * 2, density);
        int indent = clampedDepth * dpToPx(COMMENT_INDENT_PER_DEPTH_DP, density);
        int avatarWidth = dpToPx(COMMENT_AVATAR_SIZE_DP, density);
        int avatarGap = dpToPx(COMMENT_AVATAR_GAP_DP, density);
        return Math.max(1, screenWidthPx - outerPadding - indent - avatarWidth - avatarGap);
    }

    /**
     * W_body = W_screen - 36dp
     */
    public static int computeDetailBodyWidthPx(int screenWidthPx, float density) {
        return Math.max(1, screenWidthPx - dpToPx(DETAIL_HORIZONTAL_PADDING_DP * 2, density));
    }

    /**
     * Quote subtracts left/right 40dp total inner padding from W_body.
     */
    public static int computeDetailQuoteWidthPx(int screenWidthPx, float density) {
        return Math.max(1, computeDetailBodyWidthPx(screenWidthPx, density) - dpToPx(DETAIL_QUOTE_INNER_PADDING_DP * 2, density));
    }

    // --- Asynchronous Batch Warmup ---

    @NonNull
    public Future<?> submitBackgroundTask(@NonNull Runnable task) {
        return workerExecutor.submit(task);
    }

    @NonNull
    public Future<?> precomputeBlocksAsync(
            @NonNull List<ContentBlock> blocks,
            int availableWidthPx,
            float density,
            float fontScale) {
        return workerExecutor.submit(() -> precomputeBlocksSync(blocks, availableWidthPx, density, fontScale));
    }

    public void precomputeBlocksSync(
            @NonNull List<ContentBlock> blocks,
            int availableWidthPx,
            float density,
            float fontScale) {
        if (availableWidthPx <= 0 || blocks.isEmpty()) {
            return;
        }
        for (ContentBlock block : blocks) {
            if (block instanceof FeedPostBlock) {
                precomputeFeedPost((FeedPostBlock) block, availableWidthPx, density, fontScale);
            } else if (block instanceof CommentBlock) {
                precomputeComment((CommentBlock) block, availableWidthPx, density, fontScale);
            } else if (block instanceof RichTextBlock) {
                precomputeRichText((RichTextBlock) block, availableWidthPx, density, fontScale);
            } else if (block instanceof QuoteBlock) {
                precomputeQuote((QuoteBlock) block, availableWidthPx, density, fontScale);
            }
        }
    }

    // --- Synchronous Precomputation (Safe on Worker Thread or Main-Thread Resize Guard) ---

    @NonNull
    public MeasuredCardSpec precomputeFeedPost(
            @NonNull FeedPostBlock block,
            int targetWidthPx,
            float density,
            float fontScale) {
        int safeWidth = Math.max(1, targetWidthPx);
        float safeDensity = (Float.isNaN(density) || density <= 0f) ? 1.0f : density;
        float safeScale = (Float.isNaN(fontScale) || fontScale <= 0f) ? 1.0f : fontScale;

        BlockCacheKey cacheKey = new BlockCacheKey(
                block.getBlockId(),
                block.getContentHash(),
                safeWidth,
                safeScale,
                StyleId.FEED_TITLE
        );
        MeasuredBlockSpec cached = layoutCache.get(cacheKey);
        if (cached instanceof MeasuredCardSpec) {
            return (MeasuredCardSpec) cached;
        }

        TextPaintRegistry.PaintSnapshot paints = paintRegistry.getSnapshot(safeDensity, safeScale);

        int padX = dpToPx(FEED_HORIZONTAL_PADDING_DP, safeDensity);
        int padTop = dpToPx(12, safeDensity);
        int padBottom = dpToPx(2, safeDensity);
        int minTouchPx = dpToPx(MIN_TOUCH_TARGET_DP, safeDensity);

        // 1. Top Row: Category Chip & Author Avatar
        boolean hasCategory = block.getCategoryName() != null;
        String categoryText = hasCategory ? block.getCategoryName().toUpperCase(Locale.getDefault()) : null;
        boolean hasAvatar = block.getAuthorName() != null;
        int avatarSize = dpToPx(FEED_AVATAR_SIZE_DP, safeDensity);

        LineMetrics categoryMetrics = measureLineMetrics(paints.get(StyleId.FEED_CATEGORY), StyleId.FEED_CATEGORY, safeDensity, safeScale);
        int topRowHeight = Math.max(hasCategory ? categoryMetrics.lineHeight : 0, hasAvatar ? avatarSize : 0);
        int topRowTop = padTop;
        int categoryX = padX;
        int categoryBaselineY = topRowTop + ((topRowHeight - categoryMetrics.lineHeight) / 2) - categoryMetrics.ascent;

        int avatarLeft = Math.max(padX, safeWidth - padX - avatarSize);
        int avatarTop = topRowTop + Math.max(0, (topRowHeight - avatarSize) / 2);
        BlockRect avatarRect = hasAvatar
                ? new BlockRect(avatarLeft, avatarTop, avatarLeft + avatarSize, avatarTop + avatarSize)
                : BlockRect.EMPTY;

        // 2. Main Row: Serif Title & Right Thumbnail
        int mainRowTop = topRowTop + topRowHeight + dpToPx(6, safeDensity);
        boolean hasThumbnail = block.hasThumbnail();
        int thumbW = dpToPx(FEED_THUMB_WIDTH_DP, safeDensity);
        int thumbH = dpToPx(FEED_THUMB_HEIGHT_DP, safeDensity);
        int titleAvailWidth = computeFeedTitleWidthPx(safeWidth, safeDensity, hasThumbnail);

        TextPaint titlePaint = paints.get(StyleId.FEED_TITLE);
        float titleSpacingExtra = 3.5f * safeDensity;
        StaticLayout titleLayout = buildStaticLayout(block.getTitle(), titlePaint, titleAvailWidth, titleSpacingExtra, 1.0f, 3, true);
        int titleHeight = measureStaticLayoutHeight(titleLayout, block.getTitle(), titleAvailWidth, StyleId.FEED_TITLE, safeDensity, safeScale, 3, titleSpacingExtra);

        BlockRect titleRect = new BlockRect(padX, mainRowTop, padX + titleAvailWidth, mainRowTop + titleHeight);
        BlockRect thumbnailRect = BlockRect.EMPTY;
        if (hasThumbnail) {
            int thumbLeft = Math.max(padX, safeWidth - padX - thumbW);
            thumbnailRect = new BlockRect(thumbLeft, mainRowTop, thumbLeft + thumbW, mainRowTop + thumbH);
        }
        int mainRowHeight = Math.max(titleHeight, hasThumbnail ? thumbH : 0);

        // 3. Byline Row: Author, Time, Vote, Comments
        int bylineTop = mainRowTop + mainRowHeight + dpToPx(8, safeDensity);
        String authorText;
        if (block.getAuthorName() != null) {
            authorText = "Bởi " + block.getAuthorName();
        } else if (block.getSourceName() != null) {
            authorText = "Bởi " + block.getSourceName();
        } else {
            authorText = "Bởi Tác giả";
        }

        String formattedRelTime = DateTimeUtils.formatRelativeTime(block.getCreatedAt());
        boolean hasTime = formattedRelTime != null && !formattedRelTime.isEmpty();
        String timeText = hasTime ? (" • " + formattedRelTime) : null;

        String voteText;
        StyleId voteStyleId;
        if (block.getVoteScore() > 0) {
            voteText = "▲ " + block.getVoteScore();
            voteStyleId = StyleId.FEED_VOTE_POSITIVE;
        } else if (block.getVoteScore() < 0) {
            voteText = "▼ " + Math.abs(block.getVoteScore());
            voteStyleId = StyleId.FEED_VOTE_NEGATIVE;
        } else {
            voteText = "• 0";
            voteStyleId = StyleId.FEED_VOTE_NEUTRAL;
        }
        String commentsText = "💬 " + block.getCommentsCount();

        LineMetrics authorMetrics = measureLineMetrics(paints.get(StyleId.FEED_BYLINE), StyleId.FEED_BYLINE, safeDensity, safeScale);
        LineMetrics timeMetrics = measureLineMetrics(paints.get(StyleId.FEED_TIME), StyleId.FEED_TIME, safeDensity, safeScale);
        LineMetrics voteMetrics = measureLineMetrics(paints.get(voteStyleId), voteStyleId, safeDensity, safeScale);
        LineMetrics commentsMetrics = measureLineMetrics(paints.get(StyleId.FEED_COMMENTS), StyleId.FEED_COMMENTS, safeDensity, safeScale);

        int bylineHeight = Math.max(
                Math.max(authorMetrics.lineHeight, timeMetrics.lineHeight),
                Math.max(voteMetrics.lineHeight, commentsMetrics.lineHeight)
        );

        int commentsWidth = measureTextWidthPx(paints.get(StyleId.FEED_COMMENTS), commentsText, StyleId.FEED_COMMENTS, safeDensity, safeScale);
        int voteWidth = measureTextWidthPx(paints.get(voteStyleId), voteText, voteStyleId, safeDensity, safeScale);
        int commentsGap = dpToPx(10, safeDensity);

        int commentsRight = Math.max(padX, safeWidth - padX);
        int commentsX = Math.max(padX, commentsRight - commentsWidth);
        int voteX = Math.max(padX, commentsX - commentsGap - voteWidth);

        int authorX = padX;
        int maxAuthorEndX = Math.max(authorX, voteX - dpToPx(8, safeDensity));
        int authorWidth = Math.min(
                measureTextWidthPx(paints.get(StyleId.FEED_BYLINE), authorText, StyleId.FEED_BYLINE, safeDensity, safeScale),
                maxAuthorEndX - authorX
        );
        int timeX = authorX + authorWidth + dpToPx(4, safeDensity);

        int authorBaselineY = bylineTop + ((bylineHeight - authorMetrics.lineHeight) / 2) - authorMetrics.ascent;
        int timeBaselineY = bylineTop + ((bylineHeight - timeMetrics.lineHeight) / 2) - timeMetrics.ascent;
        int voteBaselineY = bylineTop + ((bylineHeight - voteMetrics.lineHeight) / 2) - voteMetrics.ascent;
        int commentsBaselineY = bylineTop + ((bylineHeight - commentsMetrics.lineHeight) / 2) - commentsMetrics.ascent;

        BlockRect authorRect = new BlockRect(authorX, bylineTop, authorX + authorWidth, bylineTop + bylineHeight);
        BlockRect commentsRect = new BlockRect(commentsX, bylineTop, commentsRight, bylineTop + bylineHeight);

        // 4. Optional Source Row
        int cursorY = bylineTop + bylineHeight;
        boolean hasSource = block.getSourceName() != null
                && (block.getAuthorName() == null || !block.getSourceName().equalsIgnoreCase(block.getAuthorName()));
        String sourceText = hasSource ? ("Nguồn: " + block.getSourceName()) : null;
        int sourceX = padX;
        int sourceBaselineY = 0;
        if (hasSource) {
            int sourceTop = cursorY + dpToPx(2, safeDensity);
            LineMetrics sourceMetrics = measureLineMetrics(paints.get(StyleId.FEED_SOURCE), StyleId.FEED_SOURCE, safeDensity, safeScale);
            sourceBaselineY = sourceTop - sourceMetrics.ascent;
            cursorY = sourceTop + sourceMetrics.lineHeight;
        }

        // 5. Hairline Divider & Bottom Padding
        int dividerStrokePx = Math.max(1, dpToPx(1, safeDensity));
        int dividerY = cursorY + dpToPx(12, safeDensity);
        int totalHeightPx = dividerY + dividerStrokePx + padBottom;

        BlockRect cardRect = new BlockRect(0, 0, safeWidth, totalHeightPx);
        BlockRect avatarTouchRect = avatarRect.expandToMinTouchSize(minTouchPx, safeWidth, totalHeightPx);
        BlockRect authorTouchRect = authorRect.expandToMinTouchSize(minTouchPx, safeWidth, totalHeightPx);
        BlockRect commentsTouchRect = commentsRect.expandToMinTouchSize(minTouchPx, safeWidth, totalHeightPx);

        MeasuredCardSpec spec = new MeasuredCardSpec(
                block.getBlockId(),
                block.getContentHash(),
                safeWidth,
                safeScale,
                totalHeightPx,
                titleLayout,
                categoryText,
                authorText,
                timeText,
                sourceText,
                voteText,
                voteStyleId,
                commentsText,
                hasCategory,
                hasAvatar,
                hasThumbnail,
                hasTime,
                hasSource,
                padX,
                dpToPx(4, safeDensity),
                dividerStrokePx,
                dividerY,
                categoryX,
                categoryBaselineY,
                authorX,
                authorBaselineY,
                timeX,
                timeBaselineY,
                voteX,
                voteBaselineY,
                commentsX,
                commentsBaselineY,
                sourceX,
                sourceBaselineY,
                cardRect,
                avatarRect,
                avatarTouchRect,
                titleRect,
                thumbnailRect,
                authorRect,
                authorTouchRect,
                commentsRect,
                commentsTouchRect
        );

        layoutCache.put(cacheKey, spec);
        return spec;
    }

    /**
     * Recomputes metrics-only fields of MeasuredCardSpec while reusing existing StaticLayout.
     */
    @NonNull
    public MeasuredCardSpec updateFeedCardMetrics(
            @NonNull MeasuredCardSpec existing,
            int newContentHash,
            int voteScore,
            int commentsCount,
            float density,
            float fontScale) {
        float safeDensity = (Float.isNaN(density) || density <= 0f) ? 1.0f : density;
        float safeScale = (Float.isNaN(fontScale) || fontScale <= 0f) ? 1.0f : fontScale;
        TextPaintRegistry.PaintSnapshot paints = paintRegistry.getSnapshot(safeDensity, safeScale);

        String voteText;
        StyleId voteStyleId;
        if (voteScore > 0) {
            voteText = "▲ " + voteScore;
            voteStyleId = StyleId.FEED_VOTE_POSITIVE;
        } else if (voteScore < 0) {
            voteText = "▼ " + Math.abs(voteScore);
            voteStyleId = StyleId.FEED_VOTE_NEGATIVE;
        } else {
            voteText = "• 0";
            voteStyleId = StyleId.FEED_VOTE_NEUTRAL;
        }
        String commentsText = "💬 " + commentsCount;

        int commentsWidth = measureTextWidthPx(paints.get(StyleId.FEED_COMMENTS), commentsText, StyleId.FEED_COMMENTS, safeDensity, safeScale);
        int voteWidth = measureTextWidthPx(paints.get(voteStyleId), voteText, voteStyleId, safeDensity, safeScale);
        int commentsGap = dpToPx(10, safeDensity);
        int minTouchPx = dpToPx(MIN_TOUCH_TARGET_DP, safeDensity);

        int commentsRight = Math.max(existing.padX, existing.targetWidthPx - existing.padX);
        int commentsX = Math.max(existing.padX, commentsRight - commentsWidth);
        int voteX = Math.max(existing.padX, commentsX - commentsGap - voteWidth);

        BlockRect newCommentsRect = new BlockRect(commentsX, existing.commentsRect.top, commentsRight, existing.commentsRect.bottom);
        BlockRect newCommentsTouchRect = newCommentsRect.expandToMinTouchSize(minTouchPx, existing.targetWidthPx, existing.totalHeightPx);

        MeasuredCardSpec updated = existing.withUpdatedMetrics(
                newContentHash,
                voteText,
                voteStyleId,
                voteX,
                commentsText,
                commentsX,
                newCommentsRect,
                newCommentsTouchRect
        );
        layoutCache.put(new BlockCacheKey(existing.getBlockId(), newContentHash, existing.getTargetWidthPx(), existing.getFontScale(), StyleId.FEED_TITLE), updated);
        return updated;
    }

    @NonNull
    public MeasuredCommentSpec precomputeComment(
            @NonNull CommentBlock block,
            int targetWidthPx,
            float density,
            float fontScale) {
        int safeWidth = Math.max(1, targetWidthPx);
        float safeDensity = (Float.isNaN(density) || density <= 0f) ? 1.0f : density;
        float safeScale = (Float.isNaN(fontScale) || fontScale <= 0f) ? 1.0f : fontScale;

        BlockCacheKey cacheKey = new BlockCacheKey(
                block.getBlockId(),
                block.getContentHash(),
                safeWidth,
                safeScale,
                StyleId.COMMENT_BODY
        );
        MeasuredBlockSpec cached = layoutCache.get(cacheKey);
        if (cached instanceof MeasuredCommentSpec) {
            return (MeasuredCommentSpec) cached;
        }

        TextPaintRegistry.PaintSnapshot paints = paintRegistry.getSnapshot(safeDensity, safeScale);

        int basePadX = dpToPx(COMMENT_HORIZONTAL_PADDING_DP, safeDensity);
        int padTop = dpToPx(12, safeDensity);
        int indentPx = block.getDepth() * dpToPx(COMMENT_INDENT_PER_DEPTH_DP, safeDensity);
        int contentLeftPad = basePadX + indentPx;
        int minTouchPx = dpToPx(MIN_TOUCH_TARGET_DP, safeDensity);

        int avatarSize = dpToPx(COMMENT_AVATAR_SIZE_DP, safeDensity);
        int avatarGap = dpToPx(COMMENT_AVATAR_GAP_DP, safeDensity);
        BlockRect avatarRect = new BlockRect(contentLeftPad, padTop, contentLeftPad + avatarSize, padTop + avatarSize);

        int columnLeft = avatarRect.right + avatarGap;
        int rightEdge = Math.max(columnLeft, safeWidth - basePadX);

        // 1. Header Row: Author + Time + (Edited) + Menu
        String authorText = block.getAuthorName() != null ? block.getAuthorName() : "Thành viên";
        String formattedTimeText = DateTimeUtils.formatRelativeTime(block.getCreatedAt());
        if (formattedTimeText == null) formattedTimeText = "";
        boolean isEdited = block.isEdited();

        LineMetrics authorMetrics = measureLineMetrics(paints.get(StyleId.COMMENT_AUTHOR), StyleId.COMMENT_AUTHOR, safeDensity, safeScale);
        LineMetrics timeMetrics = measureLineMetrics(paints.get(StyleId.COMMENT_TIME), StyleId.COMMENT_TIME, safeDensity, safeScale);
        LineMetrics editedMetrics = measureLineMetrics(paints.get(StyleId.COMMENT_EDITED), StyleId.COMMENT_EDITED, safeDensity, safeScale);

        int menuSize = dpToPx(COMMENT_MENU_SIZE_DP, safeDensity);
        int headerHeight = Math.max(menuSize, Math.max(authorMetrics.lineHeight, timeMetrics.lineHeight));

        int menuLeft = Math.max(columnLeft, rightEdge - menuSize);
        int menuTop = padTop + Math.max(0, (headerHeight - menuSize) / 2);
        BlockRect menuRect = new BlockRect(menuLeft, menuTop, menuLeft + menuSize, menuTop + menuSize);

        int authorX = columnLeft;
        int authorWidth = Math.min(
                measureTextWidthPx(paints.get(StyleId.COMMENT_AUTHOR), authorText, StyleId.COMMENT_AUTHOR, safeDensity, safeScale),
                Math.max(0, menuLeft - authorX - dpToPx(4, safeDensity))
        );
        int timeX = authorX + authorWidth + dpToPx(6, safeDensity);
        int timeWidth = formattedTimeText.isEmpty() ? 0 : measureTextWidthPx(paints.get(StyleId.COMMENT_TIME), formattedTimeText, StyleId.COMMENT_TIME, safeDensity, safeScale);
        int editedX = timeX + timeWidth + dpToPx(4, safeDensity);

        int authorBaselineY = padTop + ((headerHeight - authorMetrics.lineHeight) / 2) - authorMetrics.ascent;
        int timeBaselineY = padTop + ((headerHeight - timeMetrics.lineHeight) / 2) - timeMetrics.ascent;
        int editedBaselineY = padTop + ((headerHeight - editedMetrics.lineHeight) / 2) - editedMetrics.ascent;

        BlockRect authorRect = new BlockRect(authorX, padTop, authorX + authorWidth, padTop + headerHeight);

        // 2. Body Row
        int bodyTop = padTop + headerHeight + dpToPx(2, safeDensity);
        int bodyAvailWidth = computeCommentBodyWidthPx(safeWidth, safeDensity, block.getDepth());
        boolean isRemoved = block.isRemoved();

        StaticLayout contentLayout = null;
        int bodyHeight;
        int removedX = columnLeft;
        int removedBaselineY = 0;

        if (isRemoved) {
            LineMetrics removedMetrics = measureLineMetrics(paints.get(StyleId.COMMENT_REMOVED), StyleId.COMMENT_REMOVED, safeDensity, safeScale);
            bodyHeight = removedMetrics.lineHeight;
            removedBaselineY = bodyTop - removedMetrics.ascent;
        } else {
            float spacingExtra = 3.0f * safeDensity;
            TextPaint bodyPaint = paints.get(StyleId.COMMENT_BODY);
            contentLayout = buildStaticLayout(block.getContent(), bodyPaint, bodyAvailWidth, spacingExtra, 1.0f, 0, false);
            bodyHeight = measureStaticLayoutHeight(contentLayout, block.getContent(), bodyAvailWidth, StyleId.COMMENT_BODY, safeDensity, safeScale, 0, spacingExtra);
        }
        BlockRect contentRect = new BlockRect(columnLeft, bodyTop, columnLeft + bodyAvailWidth, bodyTop + bodyHeight);

        // 3. Action Row: "Trả lời" & optional "Xem N phản hồi" / "Ẩn phản hồi"
        int actionTop = bodyTop + bodyHeight + dpToPx(6, safeDensity);
        LineMetrics actionMetrics = measureLineMetrics(paints.get(StyleId.COMMENT_ACTION), StyleId.COMMENT_ACTION, safeDensity, safeScale);
        int actionPaddingV = dpToPx(2, safeDensity);
        int actionHeight = actionMetrics.lineHeight + 2 * actionPaddingV;

        String replyText = "Trả lời";
        int replyTextWidth = measureTextWidthPx(paints.get(StyleId.COMMENT_ACTION), replyText, StyleId.COMMENT_ACTION, safeDensity, safeScale);
        int replyRightPad = dpToPx(14, safeDensity);
        int replyX = columnLeft;
        int replyBaselineY = actionTop + actionPaddingV - actionMetrics.ascent;
        BlockRect replyRect = new BlockRect(replyX, actionTop, replyX + replyTextWidth + replyRightPad, actionTop + actionHeight);

        boolean hasRepliesButton = block.getRepliesCount() > 0;
        String repliesButtonText = null;
        int loadRepliesX = replyRect.right;
        int loadRepliesBaselineY = replyBaselineY;
        BlockRect loadRepliesRect = BlockRect.EMPTY;
        if (hasRepliesButton) {
            repliesButtonText = block.isRepliesExpanded()
                    ? "Ẩn phản hồi"
                    : ("Xem " + block.getRepliesCount() + " phản hồi");
            int loadRepliesWidth = measureTextWidthPx(paints.get(StyleId.COMMENT_ACTION), repliesButtonText, StyleId.COMMENT_ACTION, safeDensity, safeScale);
            loadRepliesRect = new BlockRect(loadRepliesX, actionTop, loadRepliesX + loadRepliesWidth, actionTop + actionHeight);
        }

        // 4. Bottom Hairline Divider (below max of avatar column and content column + 10dp)
        int contentColumnBottom = actionTop + actionHeight;
        int rowBottom = Math.max(avatarRect.bottom, contentColumnBottom);
        int dividerY = rowBottom + dpToPx(10, safeDensity);
        int dividerStrokePx = Math.max(1, dpToPx(1, safeDensity));
        int totalHeightPx = dividerY + dividerStrokePx;

        BlockRect avatarTouchRect = avatarRect.expandToMinTouchSize(minTouchPx, safeWidth, totalHeightPx);
        BlockRect authorTouchRect = authorRect.expandToMinTouchSize(minTouchPx, safeWidth, totalHeightPx);
        BlockRect menuTouchRect = menuRect.expandToMinTouchSize(minTouchPx, safeWidth, totalHeightPx);
        int replyMaxRight = hasRepliesButton ? loadRepliesRect.left : safeWidth;
        BlockRect replyTouchRect = replyRect.expandToMinTouchSize(minTouchPx, replyMaxRight, totalHeightPx);
        BlockRect loadRepliesTouchRect = BlockRect.EMPTY;
        if (hasRepliesButton) {
            BlockRect expandedLoad = loadRepliesRect.expandToMinTouchSize(minTouchPx, safeWidth, totalHeightPx);
            // Keep loadRepliesTouchRect to the right of replyRect.right when adjacent
            int safeLeft = Math.max(replyRect.right, expandedLoad.left);
            loadRepliesTouchRect = new BlockRect(safeLeft, expandedLoad.top, Math.max(safeLeft + loadRepliesRect.width(), expandedLoad.right), expandedLoad.bottom);
        }

        MeasuredCommentSpec spec = new MeasuredCommentSpec(
                block.getBlockId(),
                block.getContentHash(),
                safeWidth,
                safeScale,
                totalHeightPx,
                block.getDepth(),
                indentPx,
                basePadX,
                authorText,
                formattedTimeText,
                contentLayout,
                repliesButtonText,
                isEdited,
                isRemoved,
                hasRepliesButton,
                authorX,
                authorBaselineY,
                timeX,
                timeBaselineY,
                editedX,
                editedBaselineY,
                removedX,
                removedBaselineY,
                replyX,
                replyBaselineY,
                loadRepliesX,
                loadRepliesBaselineY,
                contentLeftPad,
                rightEdge,
                dividerY,
                dividerStrokePx,
                avatarRect,
                avatarTouchRect,
                authorRect,
                authorTouchRect,
                menuRect,
                menuTouchRect,
                contentRect,
                replyRect,
                replyTouchRect,
                loadRepliesRect,
                loadRepliesTouchRect
        );

        layoutCache.put(cacheKey, spec);
        return spec;
    }

    @NonNull
    public MeasuredCommentSpec updateCommentRepliesState(
            @NonNull MeasuredCommentSpec existing,
            int newContentHash,
            int repliesCount,
            boolean expanded,
            float density,
            float fontScale) {
        float safeDensity = (Float.isNaN(density) || density <= 0f) ? 1.0f : density;
        float safeScale = (Float.isNaN(fontScale) || fontScale <= 0f) ? 1.0f : fontScale;
        TextPaintRegistry.PaintSnapshot paints = paintRegistry.getSnapshot(safeDensity, safeScale);

        boolean hasRepliesButton = repliesCount > 0;
        String newText = null;
        BlockRect newLoadRepliesRect = BlockRect.EMPTY;
        BlockRect newLoadRepliesTouchRect = BlockRect.EMPTY;

        if (hasRepliesButton) {
            newText = expanded ? "Ẩn phản hồi" : ("Xem " + repliesCount + " phản hồi");
            int width = measureTextWidthPx(paints.get(StyleId.COMMENT_ACTION), newText, StyleId.COMMENT_ACTION, safeDensity, safeScale);
            newLoadRepliesRect = new BlockRect(
                    existing.loadRepliesX,
                    existing.replyRect.top,
                    existing.loadRepliesX + width,
                    existing.replyRect.bottom
            );
            BlockRect expandedLoad = newLoadRepliesRect.expandToMinTouchSize(
                    dpToPx(MIN_TOUCH_TARGET_DP, safeDensity),
                    existing.getTargetWidthPx(),
                    existing.getTotalHeightPx()
            );
            int safeLeft = Math.max(existing.replyRect.right, expandedLoad.left);
            newLoadRepliesTouchRect = new BlockRect(safeLeft, expandedLoad.top, Math.max(safeLeft + width, expandedLoad.right), expandedLoad.bottom);
        }

        MeasuredCommentSpec updated = existing.withUpdatedRepliesState(
                newContentHash,
                newText,
                hasRepliesButton,
                newLoadRepliesRect,
                newLoadRepliesTouchRect
        );
        layoutCache.put(new BlockCacheKey(existing.getBlockId(), newContentHash, existing.getTargetWidthPx(), existing.getFontScale(), StyleId.COMMENT_BODY), updated);
        return updated;
    }

    @NonNull
    public MeasuredTextBlockSpec precomputeRichText(
            @NonNull RichTextBlock block,
            int targetWidthPx,
            float density,
            float fontScale) {
        int safeWidth = Math.max(1, targetWidthPx);
        float safeDensity = (Float.isNaN(density) || density <= 0f) ? 1.0f : density;
        float safeScale = (Float.isNaN(fontScale) || fontScale <= 0f) ? 1.0f : fontScale;

        StyleId styleId = block.getTextStyle().toStyleId();
        BlockCacheKey cacheKey = new BlockCacheKey(
                block.getBlockId(),
                block.getContentHash(),
                safeWidth,
                safeScale,
                styleId
        );
        MeasuredBlockSpec cached = layoutCache.get(cacheKey);
        if (cached instanceof MeasuredTextBlockSpec) {
            return (MeasuredTextBlockSpec) cached;
        }

        TextPaintRegistry.PaintSnapshot paints = paintRegistry.getSnapshot(safeDensity, safeScale);
        TextPaint paint = paints.get(styleId);

        int padX = dpToPx(DETAIL_HORIZONTAL_PADDING_DP, safeDensity);
        int extraIndent = block.getTextStyle() == RichTextBlock.TextStyle.LIST_ITEM
                ? dpToPx(12 + block.getListLevel() * 14, safeDensity)
                : 0;
        int availWidth = Math.max(1, safeWidth - (padX * 2) - extraIndent);

        int padTop;
        int padBottom;
        float lineSpacingMultiplier;
        if (block.getTextStyle().isHeading()) {
            padTop = dpToPx(16, safeDensity);
            padBottom = dpToPx(8, safeDensity);
            lineSpacingMultiplier = 1.3f;
        } else if (block.getTextStyle() == RichTextBlock.TextStyle.LEAD) {
            padTop = dpToPx(2, safeDensity);
            padBottom = dpToPx(16, safeDensity);
            lineSpacingMultiplier = 1.5f;
        } else if (block.getTextStyle() == RichTextBlock.TextStyle.LIST_ITEM) {
            padTop = dpToPx(2, safeDensity);
            padBottom = dpToPx(8, safeDensity);
            lineSpacingMultiplier = 1.5f;
        } else {
            padTop = dpToPx(2, safeDensity);
            padBottom = dpToPx(14, safeDensity);
            lineSpacingMultiplier = 1.55f;
        }

        CharSequence charSeq = SpannableConverter.toCharSequence(block.getStyledText());
        StaticLayout layout = buildStaticLayout(charSeq, paint, availWidth, 0f, lineSpacingMultiplier, 0, false);
        int textHeight = measureStaticLayoutHeight(
                layout, charSeq, availWidth, styleId, safeDensity, safeScale, 0, 0f
        );
        if (layout != null && layout.getHeight() > 0) {
            textHeight = layout.getHeight();
        }

        int left = padX + extraIndent;
        BlockRect textRect = new BlockRect(left, padTop, left + availWidth, padTop + textHeight);
        int totalHeight = padTop + textHeight + padBottom;
        android.text.Spanned spanned = (charSeq instanceof android.text.Spanned) ? (android.text.Spanned) charSeq : null;

        MeasuredTextBlockSpec spec = new MeasuredTextBlockSpec(
                block.getBlockId(),
                block.getContentHash(),
                safeWidth,
                safeScale,
                styleId,
                totalHeight,
                layout,
                spanned,
                textRect,
                BlockRect.EMPTY,
                BlockRect.EMPTY,
                0,
                false
        );
        layoutCache.put(cacheKey, spec);
        return spec;
    }

    @NonNull
    public MeasuredTextBlockSpec precomputeQuote(
            @NonNull QuoteBlock block,
            int targetWidthPx,
            float density,
            float fontScale) {
        int safeWidth = Math.max(1, targetWidthPx);
        float safeDensity = (Float.isNaN(density) || density <= 0f) ? 1.0f : density;
        float safeScale = (Float.isNaN(fontScale) || fontScale <= 0f) ? 1.0f : fontScale;

        BlockCacheKey cacheKey = new BlockCacheKey(
                block.getBlockId(),
                block.getContentHash(),
                safeWidth,
                safeScale,
                StyleId.DETAIL_QUOTE
        );
        MeasuredBlockSpec cached = layoutCache.get(cacheKey);
        if (cached instanceof MeasuredTextBlockSpec) {
            return (MeasuredTextBlockSpec) cached;
        }

        TextPaintRegistry.PaintSnapshot paints = paintRegistry.getSnapshot(safeDensity, safeScale);
        TextPaint paint = paints.get(StyleId.DETAIL_QUOTE);

        int outerPadX = dpToPx(DETAIL_HORIZONTAL_PADDING_DP, safeDensity);
        int marginV = dpToPx(12, safeDensity);
        int innerPadV = dpToPx(14, safeDensity);
        int innerPadLeft = dpToPx(20, safeDensity);
        int innerPadRight = dpToPx(20, safeDensity);
        int accentBarWidth = Math.max(2, dpToPx(3, safeDensity));

        int boxLeft = outerPadX;
        int boxRight = Math.max(boxLeft + innerPadLeft + innerPadRight + 1, safeWidth - outerPadX);
        int availWidth = Math.max(1, (boxRight - boxLeft) - innerPadLeft - innerPadRight);

        CharSequence charSeq = SpannableConverter.toCharSequence(block.getStyledText());
        StaticLayout layout = buildStaticLayout(charSeq, paint, availWidth, 0f, 1.45f, 0, false);
        int textHeight = measureStaticLayoutHeight(
                layout, charSeq, availWidth, StyleId.DETAIL_QUOTE, safeDensity, safeScale, 0, 0f
        );
        if (layout != null && layout.getHeight() > 0) {
            textHeight = layout.getHeight();
        }

        int boxTop = marginV;
        int textLeft = boxLeft + innerPadLeft;
        int textTop = boxTop + innerPadV;
        int boxBottom = textTop + textHeight + innerPadV;
        int totalHeight = boxBottom + marginV;

        BlockRect textRect = new BlockRect(textLeft, textTop, textLeft + availWidth, textTop + textHeight);
        BlockRect backgroundRect = new BlockRect(boxLeft, boxTop, boxRight, boxBottom);
        BlockRect accentBarRect = new BlockRect(boxLeft, boxTop, boxLeft + accentBarWidth, boxBottom);
        android.text.Spanned spanned = (charSeq instanceof android.text.Spanned) ? (android.text.Spanned) charSeq : null;

        MeasuredTextBlockSpec spec = new MeasuredTextBlockSpec(
                block.getBlockId(),
                block.getContentHash(),
                safeWidth,
                safeScale,
                StyleId.DETAIL_QUOTE,
                totalHeight,
                layout,
                spanned,
                textRect,
                backgroundRect,
                accentBarRect,
                dpToPx(4, safeDensity),
                true
        );
        layoutCache.put(cacheKey, spec);
        return spec;
    }

    @Nullable
    public static StaticLayout buildStaticLayout(
            @NonNull CharSequence text,
            @NonNull TextPaint paint,
            int widthPx,
            float lineSpacingExtraPx,
            float lineSpacingMultiplier,
            int maxLines,
            boolean ellipsizeEnd) {
        int safeWidth = Math.max(1, widthPx);
        StaticLayout.Builder builder = StaticLayout.Builder.obtain(text, 0, text.length(), paint, safeWidth);
        if (builder == null) {
            return null;
        }
        builder.setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(lineSpacingExtraPx, lineSpacingMultiplier)
                .setIncludePad(false);
        if (maxLines > 0) {
            builder.setMaxLines(maxLines);
        }
        if (ellipsizeEnd) {
            builder.setEllipsize(TextUtils.TruncateAt.END);
        }
        return builder.build();
    }

    private static int measureStaticLayoutHeight(
            @Nullable StaticLayout layout,
            @NonNull CharSequence text,
            int availWidthPx,
            @NonNull StyleId styleId,
            float density,
            float fontScale,
            int maxLines,
            float lineSpacingExtraPx) {
        if (layout != null) {
            int h = layout.getHeight();
            if (h > 0) {
                return h;
            }
        }
        // Fallback estimation strictly for pure-JVM unit tests running without Robolectric
        if (text.length() == 0) {
            return 0;
        }
        float textSizePx = styleId.computeTextSizePx(density, fontScale);
        float avgCharW = Math.max(1f, textSizePx * 0.52f);
        int charsPerLine = Math.max(1, (int) (availWidthPx / avgCharW));
        int lines = Math.max(1, (int) Math.ceil(text.length() / (double) charsPerLine));
        if (maxLines > 0) {
            lines = Math.min(lines, maxLines);
        }
        int singleLineH = Math.max(1, Math.round(textSizePx * 1.25f + lineSpacingExtraPx));
        return lines * singleLineH;
    }

    private static int measureTextWidthPx(
            @NonNull TextPaint paint,
            @NonNull String text,
            @NonNull StyleId styleId,
            float density,
            float fontScale) {
        if (text.isEmpty()) {
            return 0;
        }
        float measured = paint.measureText(text);
        if (measured > 0f) {
            return Math.round(measured);
        }
        float textSizePx = styleId.computeTextSizePx(density, fontScale);
        return Math.max(1, Math.round(text.length() * textSizePx * 0.52f));
    }

    private static LineMetrics measureLineMetrics(
            @NonNull TextPaint paint,
            @NonNull StyleId styleId,
            float density,
            float fontScale) {
        android.graphics.Paint.FontMetricsInt fm = paint.getFontMetricsInt();
        if (fm != null && (fm.descent - fm.ascent) > 0) {
            return new LineMetrics(fm.ascent, fm.descent);
        }
        float textSizePx = styleId.computeTextSizePx(density, fontScale);
        int ascent = -Math.max(1, Math.round(textSizePx * 0.92f));
        int descent = Math.max(1, Math.round(textSizePx * 0.28f));
        return new LineMetrics(ascent, descent);
    }

    private static final class LineMetrics {
        final int ascent;
        final int descent;
        final int lineHeight;

        LineMetrics(int ascent, int descent) {
            this.ascent = ascent;
            this.descent = descent;
            this.lineHeight = Math.max(1, descent - ascent);
        }
    }

    public void shutdown() {
        workerExecutor.shutdownNow();
    }
}
