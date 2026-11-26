package com.socialapp.core.ui.blocks.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.socialapp.core.network.dto.AdvertisementResource;
import com.socialapp.core.ui.blocks.model.AdSlotBlock;
import com.socialapp.core.ui.blocks.model.BlockPayloads;
import com.socialapp.core.ui.blocks.model.CommentBlock;
import com.socialapp.core.ui.blocks.model.FeedPostBlock;

import org.junit.Test;

public class ContentBlockDiffCallbackTest {

    private final ContentBlockDiffCallback callback = new ContentBlockDiffCallback();

    @Test
    public void testFeedPostBlockDiffAndPayloads() {
        FeedPostBlock oldPost = new FeedPostBlock(
                10, "Tiêu đề bài báo", "https://img/1.jpg",
                5, "An", "https://img/a.jpg", "Thời sự", "VnExpress",
                "2026-11-24T10:00:00Z", 10, 0, 3
        );
        FeedPostBlock samePost = new FeedPostBlock(
                10, "Tiêu đề bài báo", "https://img/1.jpg",
                5, "An", "https://img/a.jpg", "Thời sự", "VnExpress",
                "2026-11-24T10:00:00Z", 10, 0, 3
        );
        FeedPostBlock metricsChanged = new FeedPostBlock(
                10, "Tiêu đề bài báo", "https://img/1.jpg",
                5, "An", "https://img/a.jpg", "Thời sự", "VnExpress",
                "2026-11-24T10:00:00Z", 11, 1, 4
        );
        FeedPostBlock titleChanged = new FeedPostBlock(
                10, "Tiêu đề đã sửa", "https://img/1.jpg",
                5, "An", "https://img/a.jpg", "Thời sự", "VnExpress",
                "2026-11-24T10:00:00Z", 11, 1, 4
        );
        FeedPostBlock otherPost = new FeedPostBlock(
                11, "Tiêu đề bài báo", "https://img/1.jpg",
                5, "An", "https://img/a.jpg", "Thời sự", "VnExpress",
                "2026-11-24T10:00:00Z", 10, 0, 3
        );

        assertTrue(callback.areItemsTheSame(oldPost, samePost));
        assertTrue(callback.areContentsTheSame(oldPost, samePost));
        assertNull(callback.getChangePayload(oldPost, samePost));

        assertTrue(callback.areItemsTheSame(oldPost, metricsChanged));
        assertFalse(callback.areContentsTheSame(oldPost, metricsChanged));
        Object payload = callback.getChangePayload(oldPost, metricsChanged);
        assertTrue(payload instanceof BlockPayloads.FeedMetricsPayload);
        BlockPayloads.FeedMetricsPayload metrics = (BlockPayloads.FeedMetricsPayload) payload;
        assertEquals(11, metrics.voteScore);
        assertEquals(1, metrics.userVote);
        assertEquals(4, metrics.commentsCount);

        assertTrue(callback.areItemsTheSame(oldPost, titleChanged));
        assertFalse(callback.areContentsTheSame(oldPost, titleChanged));
        assertNull(callback.getChangePayload(oldPost, titleChanged));

        assertFalse(callback.areItemsTheSame(oldPost, otherPost));
    }

    @Test
    public void testCommentBlockDiffAndPayloads() {
        CommentBlock c1 = new CommentBlock(
                42, 10, null, 3, "Bình", null,
                "Nội dung gốc", "published",
                "2026-11-24T10:00:00Z", "2026-11-24T10:00:00Z",
                2, 0, false
        );
        CommentBlock cExpanded = c1.withRepliesState(true, 2);
        CommentBlock cEdited = cExpanded.withUpdatedContent("Nội dung mới", "2026-11-24T10:05:00Z");

        assertTrue(callback.areItemsTheSame(c1, cExpanded));
        assertFalse(callback.areContentsTheSame(c1, cExpanded));
        Object repliesPayload = callback.getChangePayload(c1, cExpanded);
        assertTrue(repliesPayload instanceof BlockPayloads.CommentRepliesPayload);
        assertTrue(((BlockPayloads.CommentRepliesPayload) repliesPayload).repliesExpanded);

        assertTrue(callback.areItemsTheSame(cExpanded, cEdited));
        assertFalse(callback.areContentsTheSame(cExpanded, cEdited));
        Object contentPayload = callback.getChangePayload(cExpanded, cEdited);
        assertTrue(contentPayload instanceof BlockPayloads.CommentContentPayload);
        assertEquals("Nội dung mới", ((BlockPayloads.CommentContentPayload) contentPayload).content);
        assertTrue(((BlockPayloads.CommentContentPayload) contentPayload).edited);
    }

    @Test
    public void testAdSlotAndCrossTypeIsolation() {
        AdSlotBlock adEmpty = new AdSlotBlock("feed_ad_0", null);
        AdvertisementResource adDto = new AdvertisementResource();
        adDto.id = 1;
        adDto.imageUrl = "https://img/ad.png";
        AdSlotBlock adFilled = new AdSlotBlock("feed_ad_0", adDto);
        FeedPostBlock post = new FeedPostBlock(1, "T", null, 1, "A", null, null, null, null, 0, 0, 0);

        assertTrue(callback.areItemsTheSame(adEmpty, adFilled));
        assertFalse(callback.areContentsTheSame(adEmpty, adFilled));
        assertNotNull(adFilled.toAdvertisementResource());
        assertFalse(callback.areItemsTheSame(adEmpty, post));
    }
}
