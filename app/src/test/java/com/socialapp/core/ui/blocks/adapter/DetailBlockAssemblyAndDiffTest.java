package com.socialapp.core.ui.blocks.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.socialapp.core.network.dto.CategoryResource;
import com.socialapp.core.network.dto.PostResource;
import com.socialapp.core.network.dto.UserResource;
import com.socialapp.core.ui.blocks.layout.BlockLayoutCache;
import com.socialapp.core.ui.blocks.layout.BlockPrecomputer;
import com.socialapp.core.ui.blocks.layout.MeasuredTextBlockSpec;
import com.socialapp.core.ui.blocks.layout.StyleId;
import com.socialapp.core.ui.blocks.layout.TextPaintRegistry;
import com.socialapp.core.ui.blocks.model.AdSlotBlock;
import com.socialapp.core.ui.blocks.model.BlockPayloads;
import com.socialapp.core.ui.blocks.model.ContentBlock;
import com.socialapp.core.ui.blocks.model.DetailActionBlock;
import com.socialapp.core.ui.blocks.model.DetailHeaderBlock;
import com.socialapp.core.ui.blocks.model.QuoteBlock;
import com.socialapp.core.ui.blocks.model.RichTextBlock;
import com.socialapp.core.ui.blocks.parser.HtmlBlockAstParser;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class DetailBlockAssemblyAndDiffTest {

    private final ContentBlockDiffCallback diffCallback = new ContentBlockDiffCallback();

    @Test
    public void testAssembleImageCollectionAndPartialDiffPayloads() {
        PostResource post = createSamplePost();
        List<ContentBlock> bodyBlocks = HtmlBlockAstParser.parse(post.id, post.contentHtml);
        assertEquals(4, bodyBlocks.size()); // Paragraph (LEAD), Quote, InlineImage x2

        List<String> urls = PostDetailBlockAssembler.collectImageUrls(post, bodyBlocks);
        assertEquals(Arrays.asList(
                "https://cdn.example.com/cover.jpg",
                "https://cdn.example.com/inline.jpg"
        ), urls);

        List<ContentBlock> initial = PostDetailBlockAssembler.assemble(
                post, 999, false, bodyBlocks, null
        );
        assertEquals(7, initial.size());
        assertTrue(initial.get(0) instanceof DetailHeaderBlock);
        assertTrue(initial.get(1) instanceof RichTextBlock);
        assertTrue(initial.get(2) instanceof QuoteBlock);
        assertEquals(ContentBlock.TYPE_INLINE_IMAGE, initial.get(3).getBlockType());
        assertEquals(ContentBlock.TYPE_INLINE_IMAGE, initial.get(4).getBlockType());
        assertTrue(initial.get(5) instanceof DetailActionBlock);
        assertTrue(initial.get(6) instanceof AdSlotBlock);

        // Toggle follow -> only DetailHeaderBlock emits DetailFollowPayload; body blocks remain identical
        List<ContentBlock> afterFollow = PostDetailBlockAssembler.withFollowState(initial, true);
        assertEquals(initial.size(), afterFollow.size());
        assertFalse(diffCallback.areContentsTheSame(initial.get(0), afterFollow.get(0)));
        Object followPayload = diffCallback.getChangePayload(initial.get(0), afterFollow.get(0));
        assertTrue(followPayload instanceof BlockPayloads.DetailFollowPayload);
        assertTrue(((BlockPayloads.DetailFollowPayload) followPayload).followingAuthor);
        for (int i = 1; i < initial.size(); i++) {
            assertTrue(diffCallback.areItemsTheSame(initial.get(i), afterFollow.get(i)));
            assertTrue(diffCallback.areContentsTheSame(initial.get(i), afterFollow.get(i)));
            assertNull(diffCallback.getChangePayload(initial.get(i), afterFollow.get(i)));
        }

        // Optimistic upvote -> only DetailActionBlock emits DetailActionPayload; header & body untouched
        post.voteScore = 43;
        post.userVote = 1;
        List<ContentBlock> afterVote = PostDetailBlockAssembler.assemble(
                post, 999, true, bodyBlocks, null
        );
        for (int i = 0; i < 5; i++) {
            assertTrue(diffCallback.areItemsTheSame(afterFollow.get(i), afterVote.get(i)));
            assertTrue(diffCallback.areContentsTheSame(afterFollow.get(i), afterVote.get(i)));
        }
        assertFalse(diffCallback.areContentsTheSame(afterFollow.get(5), afterVote.get(5)));
        Object votePayload = diffCallback.getChangePayload(afterFollow.get(5), afterVote.get(5));
        assertTrue(votePayload instanceof BlockPayloads.DetailActionPayload);
        BlockPayloads.DetailActionPayload detailVote = (BlockPayloads.DetailActionPayload) votePayload;
        assertEquals(43, detailVote.voteScore);
        assertEquals(1, detailVote.userVote);
    }

    @Test
    public void testRichTextAndQuotePrecomputationAndCacheReuse() {
        TextPaintRegistry registry = new TextPaintRegistry();
        BlockLayoutCache cache = new BlockLayoutCache(32);
        BlockPrecomputer precomputer = new BlockPrecomputer(registry, cache);

        List<ContentBlock> parsed = HtmlBlockAstParser.parse(
                55,
                "<p>Đoạn mở đầu kiểm thử cache layout.</p><blockquote><p>Trích dẫn kiểm thử.</p></blockquote>"
        );
        RichTextBlock rich = (RichTextBlock) parsed.get(0);
        QuoteBlock quote = (QuoteBlock) parsed.get(1);

        MeasuredTextBlockSpec richSpec1 = precomputer.precomputeRichText(rich, 1080, 3.0f, 1.0f);
        MeasuredTextBlockSpec richSpec2 = precomputer.precomputeRichText(rich, 1080, 3.0f, 1.0f);
        assertSame(richSpec1, richSpec2);
        assertEquals(StyleId.DETAIL_LEAD, richSpec1.getStyleId());
        assertFalse(richSpec1.isQuote);
        assertTrue(richSpec1.backgroundRect.isEmpty());
        assertTrue(richSpec1.getTotalHeightPx() > 0);
        assertEquals(
                BlockPrecomputer.computeDetailBodyWidthPx(1080, 3.0f),
                richSpec1.textRect.width()
        );

        MeasuredTextBlockSpec quoteSpec1 = precomputer.precomputeQuote(quote, 1080, 3.0f, 1.0f);
        MeasuredTextBlockSpec quoteSpec2 = precomputer.precomputeQuote(quote, 1080, 3.0f, 1.0f);
        assertSame(quoteSpec1, quoteSpec2);
        assertNotNull(quoteSpec1.accentBarRect);
        assertFalse(quoteSpec1.accentBarRect.isEmpty());
        assertTrue(quoteSpec1.isQuote);
        assertEquals(
                BlockPrecomputer.computeDetailQuoteWidthPx(1080, 3.0f),
                quoteSpec1.textRect.width()
        );
    }

    private static PostResource createSamplePost() {
        PostResource post = new PostResource();
        post.id = 77;
        post.title = "Bản tin thử nghiệm kiến trúc mới";
        post.thumbnailUrl = "https://cdn.example.com/cover.jpg";
        post.contentHtml = "<p>Đoạn mở đầu.</p>"
                + "<blockquote>Trích dẫn quan trọng.</blockquote>"
                + "<img src=\"https://cdn.example.com/inline.jpg\"/>"
                + "<img src=\"https://cdn.example.com/cover.jpg\"/>";
        post.voteScore = 42;
        post.userVote = 0;
        post.commentsCount = 7;
        post.createdAt = "2026-11-24T10:00:00Z";
        post.sourceName = "VnExpress";
        post.sourceUrl = "https://vnexpress.net";
        post.isFollowingAuthor = false;

        UserResource author = new UserResource();
        author.id = 12;
        author.name = "Nguyễn Văn A";
        post.author = author;

        CategoryResource category = new CategoryResource();
        category.id = 3;
        category.name = "Công nghệ";
        post.category = category;
        return post;
    }
}
