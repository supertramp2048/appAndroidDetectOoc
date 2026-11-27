package com.socialapp.core.ui.blocks.layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.socialapp.core.ui.blocks.model.CommentBlock;
import com.socialapp.core.ui.blocks.model.FeedPostBlock;

import org.junit.Test;

public class BlockPrecomputerMathTest {

    @Test
    public void testFeedTitleWidthFormulaWithAndWithoutThumbnail() {
        int screenWidth = 1080;
        float density = 3.0f;

        // W_title(no thumb) = 1080 - 32dp(96) = 984
        int noThumbWidth = BlockPrecomputer.computeFeedTitleWidthPx(screenWidth, density, false);
        assertEquals(984, noThumbWidth);

        // W_title(thumb) = 1080 - 32dp(96) - (104dp(312) + 14dp(42)) = 630
        int withThumbWidth = BlockPrecomputer.computeFeedTitleWidthPx(screenWidth, density, true);
        assertEquals(630, withThumbWidth);
        assertEquals(noThumbWidth - (312 + 42), withThumbWidth);
    }

    @Test
    public void testCommentBodyWidthFormulaAcrossDepthsAndClamping() {
        int screenWidth = 1080;
        float density = 2.0f;

        // 32dp = 64px, 34dp(avatar) = 68px, 10dp(gap) = 20px, 24dp(indent) = 48px
        assertEquals(1080 - 64 - 68 - 20, BlockPrecomputer.computeCommentBodyWidthPx(screenWidth, density, 0));
        assertEquals(1080 - 64 - 48 - 68 - 20, BlockPrecomputer.computeCommentBodyWidthPx(screenWidth, density, 1));
        assertEquals(1080 - 64 - 96 - 68 - 20, BlockPrecomputer.computeCommentBodyWidthPx(screenWidth, density, 2));

        // Depth > 2 clamps to 2; depth < 0 clamps to 0
        assertEquals(
                BlockPrecomputer.computeCommentBodyWidthPx(screenWidth, density, 2),
                BlockPrecomputer.computeCommentBodyWidthPx(screenWidth, density, 5)
        );
        assertEquals(
                BlockPrecomputer.computeCommentBodyWidthPx(screenWidth, density, 0),
                BlockPrecomputer.computeCommentBodyWidthPx(screenWidth, density, -2)
        );
    }

    @Test
    public void testDetailBodyAndQuoteWidthFormulas() {
        int screenWidth = 1080;
        float density = 2.0f;

        int bodyWidth = BlockPrecomputer.computeDetailBodyWidthPx(screenWidth, density);
        int quoteWidth = BlockPrecomputer.computeDetailQuoteWidthPx(screenWidth, density);

        assertEquals(1080 - 72, bodyWidth);
        assertEquals(bodyWidth - 80, quoteWidth);
    }

    @Test
    public void testPrecomputeFeedPostGeometryCacheAndTouchTargets() {
        TextPaintRegistry registry = new TextPaintRegistry();
        BlockLayoutCache cache = new BlockLayoutCache();
        BlockPrecomputer precomputer = new BlockPrecomputer(registry, cache);

        FeedPostBlock withThumb = new FeedPostBlock(
                10,
                "Tiêu đề bài viết thử nghiệm dài vừa đủ để kiểm tra tính toán chiều cao",
                "https://example.com/thumb.jpg",
                5,
                "Nguyễn Văn A",
                "https://example.com/avatar.jpg",
                "Thời sự",
                "Báo Mới",
                "2026-11-24T10:00:00Z",
                12,
                1,
                8
        );
        FeedPostBlock withoutThumb = new FeedPostBlock(
                10,
                withThumb.getTitle(),
                null,
                5,
                "Nguyễn Văn A",
                "https://example.com/avatar.jpg",
                "Thời sự",
                "Báo Mới",
                "2026-11-24T10:00:00Z",
                12,
                1,
                8
        );

        float density = 2.0f;
        int minTouch = BlockPrecomputer.dpToPx(48, density);

        MeasuredCardSpec specThumb = precomputer.precomputeFeedPost(withThumb, 1080, density, 1.0f);
        assertTrue(specThumb.hasThumbnail);
        assertTrue(specThumb.hasAvatar);
        assertTrue(specThumb.hasCategory);
        assertTrue(specThumb.hasSource);
        assertTrue(specThumb.getTotalHeightPx() > 0);
        assertEquals(BlockPrecomputer.computeFeedTitleWidthPx(1080, density, true), specThumb.titleRect.width());
        assertTrue(specThumb.avatarTouchRect.width() >= minTouch);
        assertTrue(specThumb.avatarTouchRect.height() >= minTouch);
        assertTrue(specThumb.commentsTouchRect.width() >= minTouch);
        assertTrue(specThumb.commentsTouchRect.height() >= minTouch);
        assertEquals(StyleId.FEED_VOTE_POSITIVE, specThumb.voteStyleId);

        // Cache hit on repeat call with identical 5-field key
        assertSame(specThumb, precomputer.precomputeFeedPost(withThumb, 1080, density, 1.0f));

        // Rotating screen width misses old key and generates new layout spec
        MeasuredCardSpec rotatedSpec = precomputer.precomputeFeedPost(withThumb, 2160, density, 1.0f);
        assertNotSame(specThumb, rotatedSpec);
        assertEquals(BlockPrecomputer.computeFeedTitleWidthPx(2160, density, true), rotatedSpec.titleRect.width());

        // Without thumbnail widens titleRect by 118dp (236px at 2x)
        MeasuredCardSpec specNoThumb = precomputer.precomputeFeedPost(withoutThumb, 1080, density, 1.0f);
        assertFalse(specNoThumb.hasThumbnail);
        assertEquals(BlockPrecomputer.computeFeedTitleWidthPx(1080, density, false), specNoThumb.titleRect.width());
    }

    @Test
    public void testPrecomputeCommentGeometryAndRepliesToggleReuse() {
        TextPaintRegistry registry = new TextPaintRegistry();
        BlockLayoutCache cache = new BlockLayoutCache();
        BlockPrecomputer precomputer = new BlockPrecomputer(registry, cache);

        CommentBlock comment = new CommentBlock(
                77,
                10,
                null,
                3,
                "Trần Thị B",
                null,
                "Nội dung bình luận chi tiết kiểm chứng nguồn tin.",
                "published",
                "2026-11-24T10:00:00Z",
                "2026-11-24T10:05:00Z",
                4,
                1,
                false
        );

        float density = 2.0f;
        int minTouch = BlockPrecomputer.dpToPx(48, density);
        MeasuredCommentSpec spec = precomputer.precomputeComment(comment, 1080, density, 1.0f);

        assertEquals(1, spec.depth);
        assertTrue(spec.isEdited);
        assertFalse(spec.isRemoved);
        assertTrue(spec.hasRepliesButton);
        assertEquals("Xem 4 phản hồi", spec.repliesButtonText);
        assertEquals(BlockPrecomputer.computeCommentBodyWidthPx(1080, density, 1), spec.contentRect.width());
        assertTrue(spec.replyTouchRect.width() >= minTouch);
        assertTrue(spec.loadRepliesTouchRect.width() >= minTouch);
        assertTrue(spec.menuTouchRect.width() >= minTouch);

        CommentBlock expanded = comment.withRepliesState(true, 4);
        MeasuredCommentSpec expandedSpec = precomputer.updateCommentRepliesState(
                spec,
                expanded.getContentHash(),
                expanded.getRepliesCount(),
                expanded.isRepliesExpanded(),
                density,
                1.0f
        );
        assertEquals("Ẩn phản hồi", expandedSpec.repliesButtonText);
        assertEquals(spec.getTotalHeightPx(), expandedSpec.getTotalHeightPx());
    }
}
