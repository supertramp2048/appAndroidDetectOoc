package com.socialapp.core.ui.blocks.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.socialapp.core.network.dto.CommentResource;
import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.ui.blocks.model.CommentBlock;
import com.socialapp.core.ui.blocks.model.ContentBlock;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FeedAndCommentBlockListTest {

    private static PostResource post(int id) {
        PostResource p = new PostResource();
        p.id = id;
        p.title = "Bài " + id;
        return p;
    }

    private static CommentResource comment(int id, Integer parentId, int repliesCount) {
        CommentResource c = new CommentResource();
        c.id = id;
        c.postId = 1;
        c.parentId = parentId;
        c.content = "Comment " + id;
        c.status = "published";
        c.createdAt = "2026-11-24T10:00:00Z";
        c.updatedAt = "2026-11-24T10:00:00Z";
        c.repliesCount = repliesCount;
        return c;
    }

    @Test
    public void testFeedBlockListBuilderInsertsAdsAtFrequencyFive() {
        assertTrue(FeedBlockListBuilder.build(null).isEmpty());
        assertTrue(FeedBlockListBuilder.build(Collections.emptyList()).isEmpty());

        List<PostResource> fourPosts = new ArrayList<>();
        for (int i = 1; i <= 4; i++) fourPosts.add(post(i));
        assertEquals(4, FeedBlockListBuilder.build(fourPosts).size());

        List<PostResource> elevenPosts = new ArrayList<>();
        for (int i = 1; i <= 11; i++) elevenPosts.add(post(i));
        List<ContentBlock> built = FeedBlockListBuilder.build(elevenPosts);
        assertEquals(13, built.size());
        assertEquals(ContentBlock.TYPE_AD_SLOT, built.get(5).getBlockType());
        assertEquals(ContentBlock.TYPE_AD_SLOT, built.get(11).getBlockType());
        assertEquals(ContentBlock.TYPE_FEED_POST, built.get(12).getBlockType());
    }

    @Test
    public void testCommentTreeExpandCollapseAndPageLoadPreservation() {
        CommentResource r1 = comment(1, null, 2);
        CommentResource r2 = comment(2, null, 0);
        CommentResource reply11 = comment(11, 1, 1);
        CommentResource reply12 = comment(12, 1, 0);
        CommentResource reply111 = comment(111, 11, 0);

        List<ContentBlock> initial = CommentBlockTreeHelper.fromRootComments(Arrays.asList(r1, r2), null);
        assertEquals(2, initial.size());

        // Expand replies under r1
        List<ContentBlock> afterExpand = CommentBlockTreeHelper.insertReplies(
                initial, 1, Arrays.asList(reply11, reply12));
        // Original list remains untouched (immutable)
        assertEquals(2, initial.size());
        assertEquals(4, afterExpand.size());
        assertTrue(((CommentBlock) afterExpand.get(0)).isRepliesExpanded());
        assertEquals(1, ((CommentBlock) afterExpand.get(1)).getDepth());
        assertEquals(1, ((CommentBlock) afterExpand.get(2)).getDepth());

        // Expand nested reply under reply11 -> depth = 2 (clamped max 2)
        List<ContentBlock> afterNested = CommentBlockTreeHelper.insertReplies(
                afterExpand, 11, Collections.singletonList(reply111));
        assertEquals(5, afterNested.size());
        assertEquals(2, ((CommentBlock) afterNested.get(2)).getDepth());

        // Page 2 root comment arrives via PageLoader.loadNext() -> expanded replies under r1 are preserved!
        CommentResource r3 = comment(3, null, 0);
        List<ContentBlock> afterPage2 = CommentBlockTreeHelper.fromRootComments(
                Arrays.asList(r1, r2, r3), afterNested);
        assertEquals(6, afterPage2.size());
        assertTrue(((CommentBlock) afterPage2.get(0)).isRepliesExpanded());
        assertEquals("comment_111", afterPage2.get(2).getBlockId());
        assertEquals("comment_3", afterPage2.get(5).getBlockId());

        // Collapse r1 removes all descendants (depth > 0)
        List<ContentBlock> afterCollapse = CommentBlockTreeHelper.collapseReplies(afterPage2, 1);
        assertEquals(3, afterCollapse.size());
        assertFalse(((CommentBlock) afterCollapse.get(0)).isRepliesExpanded());
    }

    @Test
    public void testCommentEditInsertReplyAndRemoveCascadesChildren() {
        CommentResource r1 = comment(1, null, 0);
        List<ContentBlock> list = CommentBlockTreeHelper.fromRootComments(Collections.singletonList(r1), null);

        CommentResource newReply = comment(20, 1, 0);
        list = CommentBlockTreeHelper.insertReplyUnderParent(list, 1, newReply);
        assertEquals(2, list.size());
        assertEquals(1, ((CommentBlock) list.get(0)).getRepliesCount());
        assertTrue(((CommentBlock) list.get(0)).isRepliesExpanded());

        list = CommentBlockTreeHelper.updateCommentContent(list, 20, "Đã chỉnh sửa");
        assertEquals("Đã chỉnh sửa", ((CommentBlock) list.get(1)).getContent());
        assertTrue(((CommentBlock) list.get(1)).isEdited());

        // Removing parent r1 cascades removal of child reply 20
        list = CommentBlockTreeHelper.removeComment(list, 1);
        assertTrue(list.isEmpty());
    }
}
