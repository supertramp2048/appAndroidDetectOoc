package com.socialapp.core.ui.blocks.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentCallbacks2;

import com.socialapp.MyApplication;
import com.socialapp.core.network.dto.CategoryResource;
import com.socialapp.core.network.dto.CommentResource;
import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.network.dto.UserResource;
import com.socialapp.core.ui.blocks.adapter.BlockWarmupEngine;
import com.socialapp.core.ui.blocks.adapter.UnifiedBlockViewPool;
import com.socialapp.core.ui.blocks.layout.BlockCacheKey;
import com.socialapp.core.ui.blocks.layout.BlockLayoutCache;
import com.socialapp.core.ui.blocks.layout.BlockPrecomputer;
import com.socialapp.core.ui.blocks.layout.MeasuredBlockSpec;
import com.socialapp.core.ui.blocks.layout.StyleId;
import com.socialapp.core.ui.blocks.layout.TextPaintRegistry;

import org.junit.Test;

public class ContentBlockModelTest {

    @Test
    public void testFeedPostBlockSnapshotsAreImmutableAgainstDtoMutation() {
        PostResource dto = new PostResource();
        dto.id = 99;
        dto.title = "Tiêu đề gốc";
        dto.voteScore = 5;
        dto.userVote = 0;
        dto.commentsCount = 2;
        dto.author = new UserResource();
        dto.author.id = 7;
        dto.author.name = "Tác giả";
        dto.category = new CategoryResource();
        dto.category.name = "Công nghệ";

        FeedPostBlock blockBefore = FeedPostBlock.fromPostResource(dto);
        int hashBefore = blockBefore.getContentHash();

        // Mutate original DTO in-place (simulating ViewModel / Adapter mutation bug)
        dto.voteScore = 6;
        dto.userVote = 1;
        dto.commentsCount = 3;

        assertEquals(5, blockBefore.getVoteScore());
        assertEquals(hashBefore, blockBefore.getContentHash());

        FeedPostBlock blockAfter = FeedPostBlock.fromPostResource(dto);
        assertEquals(blockBefore.getBlockId(), blockAfter.getBlockId());
        assertNotEquals(blockBefore.getContentHash(), blockAfter.getContentHash());

        Object payload = blockBefore.getChangePayload(blockAfter);
        assertTrue(payload instanceof BlockPayloads.FeedMetricsPayload);
        BlockPayloads.FeedMetricsPayload metrics = (BlockPayloads.FeedMetricsPayload) payload;
        assertEquals(6, metrics.voteScore);
        assertEquals(1, metrics.userVote);
        assertEquals(3, metrics.commentsCount);
    }

    @Test
    public void testCommentBlockDepthClampAndPayloads() {
        CommentResource dto = new CommentResource();
        dto.id = 55;
        dto.postId = 99;
        dto.content = "Bình luận ban đầu";
        dto.status = "published";
        dto.createdAt = "2026-11-24T10:00:00Z";
        dto.updatedAt = "2026-11-24T10:00:00Z";
        dto.repliesCount = 2;

        CommentBlock b1 = CommentBlock.fromCommentResource(dto, 4, false);
        assertEquals(CommentBlock.MAX_DEPTH, b1.getDepth());
        assertFalse(b1.isEdited());
        assertFalse(b1.isRemoved());

        CommentBlock b2 = b1.withRepliesState(true, 3);
        Object repliesPayload = b1.getChangePayload(b2);
        assertTrue(repliesPayload instanceof BlockPayloads.CommentRepliesPayload);
        assertTrue(((BlockPayloads.CommentRepliesPayload) repliesPayload).repliesExpanded);
        assertEquals(3, ((BlockPayloads.CommentRepliesPayload) repliesPayload).repliesCount);

        CommentBlock b3 = b2.withUpdatedContent("Nội dung đã sửa", "2026-11-24T10:05:00Z");
        Object contentPayload = b2.getChangePayload(b3);
        assertTrue(contentPayload instanceof BlockPayloads.CommentContentPayload);
        assertTrue(((BlockPayloads.CommentContentPayload) contentPayload).edited);
    }

    @Test
    public void testMemoryTrimDispatchHandlesModerateLowUiHiddenAndCritical() {
        BlockLayoutCache l1Cache = new BlockLayoutCache(20);
        UnifiedBlockViewPool l3Pool = new UnifiedBlockViewPool();
        BlockWarmupEngine engine = new BlockWarmupEngine(
                new BlockPrecomputer(new TextPaintRegistry(), l1Cache),
                l3Pool
        );

        for (int i = 0; i < 8; i++) {
            l1Cache.put(
                    new BlockCacheKey("k_" + i, i, 1080, 1.0f, StyleId.FEED_TITLE),
                    new MeasuredBlockSpec("k_" + i, i, 1080, 1.0f, StyleId.FEED_TITLE, 100) {}
            );
        }
        assertEquals(8, l1Cache.size());

        // RUNNING_MODERATE -> trims L1 by 50% (8 -> 4), trims L3 capacities
        MyApplication.dispatchMemoryTrim(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE, l1Cache, l3Pool, engine, null);
        assertEquals(4, l1Cache.size());
        assertTrue(l3Pool.isTrimmedForPressure());
        assertFalse(engine.isMemoryPressureActive());

        // UI_HIDDEN (numeric 20 > RUNNING_CRITICAL 15!) must go to LOW/HIDDEN branch (keep 25% = 1), NOT critical clear!
        MyApplication.dispatchMemoryTrim(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN, l1Cache, l3Pool, engine, null);
        assertEquals(1, l1Cache.size());
        assertTrue(engine.isMemoryPressureActive());

        // RUNNING_CRITICAL -> clears L1 completely
        MyApplication.dispatchMemoryTrim(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL, l1Cache, l3Pool, engine, null);
        assertEquals(0, l1Cache.size());

        // Restoring normal pressure restores pool capacities
        engine.setMemoryPressureActive(false);
        assertFalse(l3Pool.isTrimmedForPressure());
        assertNotNull(new AdSlotBlock("ad_0", null).getBlockId());
        assertNull(new AdSlotBlock("ad_0", null).toAdvertisementResource());
    }
}
