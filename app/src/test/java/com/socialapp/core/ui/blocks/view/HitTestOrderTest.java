package com.socialapp.core.ui.blocks.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.socialapp.core.ui.blocks.layout.BlockLayoutCache;
import com.socialapp.core.ui.blocks.layout.BlockPrecomputer;
import com.socialapp.core.ui.blocks.layout.MeasuredCardSpec;
import com.socialapp.core.ui.blocks.layout.MeasuredCommentSpec;
import com.socialapp.core.ui.blocks.layout.TextPaintRegistry;
import com.socialapp.core.ui.blocks.model.CommentBlock;
import com.socialapp.core.ui.blocks.model.FeedPostBlock;

import org.junit.Test;

public class HitTestOrderTest {

    @Test
    public void testFeedPostHitTestPrioritizesChildTargetsBeforeCardRect() {
        BlockPrecomputer precomputer = new BlockPrecomputer(new TextPaintRegistry(), new BlockLayoutCache());
        FeedPostBlock postWithAuthor = new FeedPostBlock(
                1, "Tiêu đề bài báo dài để kiểm tra chạm vào card", "https://img/t.jpg",
                12, "Nguyễn Văn A", "https://img/a.jpg", "Kinh tế", "Báo Mới",
                "2026-11-24T10:00:00Z", 5, 1, 9
        );

        MeasuredCardSpec spec = precomputer.precomputeFeedPost(postWithAuthor, 1080, 2.0f, 1.0f);

        // Child targets lie inside cardRect, yet must win over CARD
        assertEquals(SinglePassFeedPostView.HitTarget.AVATAR,
                SinglePassFeedPostView.resolveHitTarget(spec, true, spec.avatarRect.centerX(), spec.avatarRect.centerY()));
        assertEquals(SinglePassFeedPostView.HitTarget.AUTHOR,
                SinglePassFeedPostView.resolveHitTarget(spec, true, spec.authorRect.centerX(), spec.authorRect.centerY()));
        assertEquals(SinglePassFeedPostView.HitTarget.COMMENTS,
                SinglePassFeedPostView.resolveHitTarget(spec, true, spec.commentsRect.centerX(), spec.commentsRect.centerY()));

        // Expanded 48dp touch area around commentsRect still hits COMMENTS
        assertEquals(SinglePassFeedPostView.HitTarget.COMMENTS,
                SinglePassFeedPostView.resolveHitTarget(spec, true, spec.commentsTouchRect.left + 1, spec.commentsTouchRect.top + 1));

        // Tapping headline falls through to CARD
        assertEquals(SinglePassFeedPostView.HitTarget.CARD,
                SinglePassFeedPostView.resolveHitTarget(spec, true, spec.titleRect.centerX(), spec.titleRect.centerY()));

        // Source-only post without navigable author falls through to CARD when tapping author area
        assertEquals(SinglePassFeedPostView.HitTarget.CARD,
                SinglePassFeedPostView.resolveHitTarget(spec, false, spec.authorRect.centerX(), spec.authorRect.centerY()));
    }

    @Test
    public void testCommentHitTestPrioritizationAndDisjointActionTargets() {
        BlockPrecomputer precomputer = new BlockPrecomputer(new TextPaintRegistry(), new BlockLayoutCache());
        CommentBlock comment = new CommentBlock(
                88, 1, null, 7, "Lê Minh", "https://img/u.jpg",
                "Nội dung bình luận đủ dài để tách nút bên dưới", "published",
                "2026-11-24T10:00:00Z", "2026-11-24T10:00:00Z",
                3, 0, false
        );

        MeasuredCommentSpec spec = precomputer.precomputeComment(comment, 1080, 2.0f, 1.0f);

        assertEquals(SinglePassCommentView.HitTarget.AVATAR,
                SinglePassCommentView.resolveHitTarget(spec, true, spec.avatarRect.centerX(), spec.avatarRect.centerY()));
        assertEquals(SinglePassCommentView.HitTarget.AUTHOR,
                SinglePassCommentView.resolveHitTarget(spec, true, spec.authorRect.centerX(), spec.authorRect.centerY()));
        assertEquals(SinglePassCommentView.HitTarget.MENU,
                SinglePassCommentView.resolveHitTarget(spec, true, spec.menuRect.centerX(), spec.menuRect.centerY()));
        assertEquals(SinglePassCommentView.HitTarget.REPLY,
                SinglePassCommentView.resolveHitTarget(spec, true, spec.replyRect.centerX(), spec.replyRect.centerY()));
        assertEquals(SinglePassCommentView.HitTarget.LOAD_REPLIES,
                SinglePassCommentView.resolveHitTarget(spec, true, spec.loadRepliesRect.centerX(), spec.loadRepliesRect.centerY()));

        // Verify replyTouchRect and loadRepliesTouchRect do not steal clicks from each other
        assertFalse(spec.replyTouchRect.contains(spec.loadRepliesRect.centerX(), spec.loadRepliesRect.centerY()));
        assertFalse(spec.loadRepliesTouchRect.contains(spec.replyRect.centerX(), spec.replyRect.centerY()));

        // Tapping empty body text returns NONE so parent scroll handles smoothly
        assertEquals(SinglePassCommentView.HitTarget.NONE,
                SinglePassCommentView.resolveHitTarget(spec, true, spec.contentRect.centerX(), spec.contentRect.centerY()));
    }
}
