package com.socialapp.core.ui.blocks.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.socialapp.core.ui.blocks.model.CodeBlock;
import com.socialapp.core.ui.blocks.model.ContentBlock;
import com.socialapp.core.ui.blocks.model.InlineImageBlock;
import com.socialapp.core.ui.blocks.model.InlineSpanSpec;
import com.socialapp.core.ui.blocks.model.QuoteBlock;
import com.socialapp.core.ui.blocks.model.RichTextBlock;
import com.socialapp.core.ui.blocks.model.TableBlock;

import org.junit.Test;

import java.util.List;

public class HtmlBlockAstParserTest {

    @Test
    public void testHeadingsLeadParagraphsAndInlineSpans() {
        String html = "<h1>Tiêu đề chính</h1>"
                + "<p>Đoạn mở đầu bài viết Editorial</p>"
                + "<h2>Mục hai</h2><h3>Mục ba</h3><h4>Mục bốn</h4>"
                + "<p>Văn bản <b>đậm</b>, <em>nghiêng</em>, <u>gạch chân</u>, <code>inline()</code> và "
                + "<a href=\"https://example.com/news\">liên\n kết</a>.</p>";

        List<ContentBlock> blocks = HtmlBlockAstParser.parse(101, html);
        assertEquals(6, blocks.size());

        RichTextBlock h1 = (RichTextBlock) blocks.get(0);
        assertEquals(RichTextBlock.TextStyle.HEADING_H1, h1.getTextStyle());
        assertEquals("Tiêu đề chính", h1.getStyledText().getText());

        RichTextBlock lead = (RichTextBlock) blocks.get(1);
        assertEquals(RichTextBlock.TextStyle.LEAD, lead.getTextStyle());
        assertEquals("Đoạn mở đầu bài viết Editorial", lead.getStyledText().getText());

        assertEquals(RichTextBlock.TextStyle.HEADING_H2, ((RichTextBlock) blocks.get(2)).getTextStyle());
        assertEquals(RichTextBlock.TextStyle.HEADING_H3, ((RichTextBlock) blocks.get(3)).getTextStyle());
        assertEquals(RichTextBlock.TextStyle.HEADING_H4, ((RichTextBlock) blocks.get(4)).getTextStyle());

        RichTextBlock body = (RichTextBlock) blocks.get(5);
        assertEquals(RichTextBlock.TextStyle.PARAGRAPH, body.getTextStyle());
        String text = body.getStyledText().getText();
        assertEquals("Văn bản đậm, nghiêng, gạch chân, inline() và liên kết.", text);

        List<InlineSpanSpec> spans = body.getStyledText().getSpans();
        assertTrue(hasSpan(spans, text, InlineSpanSpec.SpanType.BOLD, "đậm", null));
        assertTrue(hasSpan(spans, text, InlineSpanSpec.SpanType.ITALIC, "nghiêng", null));
        assertTrue(hasSpan(spans, text, InlineSpanSpec.SpanType.UNDERLINE, "gạch chân", null));
        assertTrue(hasSpan(spans, text, InlineSpanSpec.SpanType.INLINE_CODE, "inline()", null));
        assertTrue(hasSpan(spans, text, InlineSpanSpec.SpanType.LINK, "liên kết", "https://example.com/news"));
    }

    @Test
    public void testListsAndLineBreaks() {
        String html = "<ul><li>Mục <strong>thứ nhất</strong><br/>Dòng phụ</li><li>Mục hai</li></ul>"
                + "<ol><li>Bước một</li><li>Bước hai</li></ol>";

        List<ContentBlock> blocks = HtmlBlockAstParser.parse(102, html);
        assertEquals(4, blocks.size());

        RichTextBlock ul1 = (RichTextBlock) blocks.get(0);
        assertEquals(RichTextBlock.TextStyle.LIST_ITEM, ul1.getTextStyle());
        assertEquals("• Mục thứ nhất\nDòng phụ", ul1.getStyledText().getText());
        assertTrue(hasSpan(ul1.getStyledText().getSpans(), ul1.getStyledText().getText(),
                InlineSpanSpec.SpanType.BOLD, "thứ nhất", null));

        RichTextBlock ul2 = (RichTextBlock) blocks.get(1);
        assertEquals("• Mục hai", ul2.getStyledText().getText());

        RichTextBlock ol1 = (RichTextBlock) blocks.get(2);
        RichTextBlock ol2 = (RichTextBlock) blocks.get(3);
        assertEquals("1. Bước một", ol1.getStyledText().getText());
        assertEquals("2. Bước hai", ol2.getStyledText().getText());
    }

    @Test
    public void testBlockquoteWithNestedParagraphs() {
        String html = "<blockquote><p>Trích dẫn đoạn một.</p>"
                + "<p>Đoạn hai <b>nhấn mạnh</b>.</p></blockquote>";

        List<ContentBlock> blocks = HtmlBlockAstParser.parse(103, html);
        assertEquals(1, blocks.size());
        assertTrue(blocks.get(0) instanceof QuoteBlock);

        QuoteBlock quote = (QuoteBlock) blocks.get(0);
        String text = quote.getStyledText().getText();
        assertEquals("Trích dẫn đoạn một.\n\nĐoạn hai nhấn mạnh.", text);
        assertTrue(hasSpan(quote.getStyledText().getSpans(), text,
                InlineSpanSpec.SpanType.BOLD, "nhấn mạnh", null));
    }

    @Test
    public void testImagesWithFigcaptionAndItalicCaption() {
        String html = "<figure><img src=\"https://cdn.example.com/a.jpg\" width=\"800\" height=\"400\" alt=\"Alt A\"/>"
                + "<figcaption>Chú thích hình A</figcaption></figure>"
                + "<img src=\"https://cdn.example.com/b.jpg\">"
                + "<p><em>Chú thích nghiêng B</em></p>"
                + "<p>Đoạn tiếp theo</p>";

        List<ContentBlock> blocks = HtmlBlockAstParser.parse(104, html);
        assertEquals(3, blocks.size());

        InlineImageBlock imgA = (InlineImageBlock) blocks.get(0);
        assertEquals("https://cdn.example.com/a.jpg", imgA.getImageUrl());
        assertEquals("Alt A", imgA.getAltText());
        assertEquals("Chú thích hình A", imgA.getCaption());
        assertEquals(2.0f, imgA.getAspectRatio(), 0.001f);

        InlineImageBlock imgB = (InlineImageBlock) blocks.get(1);
        assertEquals("https://cdn.example.com/b.jpg", imgB.getImageUrl());
        assertEquals("Chú thích nghiêng B", imgB.getCaption());
        assertEquals(InlineImageBlock.DEFAULT_ASPECT_RATIO, imgB.getAspectRatio(), 0.001f);

        RichTextBlock next = (RichTextBlock) blocks.get(2);
        assertEquals(RichTextBlock.TextStyle.LEAD, next.getTextStyle());
        assertEquals("Đoạn tiếp theo", next.getStyledText().getText());
    }

    @Test
    public void testPreCodeAndTableParsing() {
        String html = "<pre class=\"language-java\">int x = a &lt; b ? 1 : 0;\nSystem.out.println(&quot;ok&quot;);</pre>"
                + "<table>"
                + "<tr><th>Cột 1</th><th>Cột 2</th></tr>"
                + "<tr><td><p>Dòng 1A</p><p>Dòng 1B</p></td><td>Giá trị 2</td></tr>"
                + "</table>";

        List<ContentBlock> blocks = HtmlBlockAstParser.parse(105, html);
        assertEquals(2, blocks.size());

        CodeBlock code = (CodeBlock) blocks.get(0);
        assertEquals("java", code.getLanguage());
        assertEquals("int x = a < b ? 1 : 0;\nSystem.out.println(\"ok\");", code.getCodeText());

        TableBlock table = (TableBlock) blocks.get(1);
        assertEquals(2, table.getRows().size());
        assertTrue(table.getRows().get(0).cells.get(0).header);
        assertEquals("Cột 1", table.getRows().get(0).cells.get(0).text);
        assertEquals("Dòng 1A\nDòng 1B", table.getRows().get(1).cells.get(0).text);
        assertEquals("Giá trị 2", table.getRows().get(1).cells.get(1).text);
    }

    @Test
    public void testDeepNestingEntitiesUnknownTagsAndMalformedInputPerformance() {
        String html = "<script>alert('xss')</script>"
                + "<div><div>Khối lồng &amp; &nbsp;&mdash; &#39;A&#39;</div>Khối ngoài</div>"
                + "<p><custom-widget>Giữ chữ</custom-widget> <b><i>Đậm Nghiêng</b></i></p>"
                + "<p>Văn bản chưa đóng thẻ<b>Cuối bài";

        List<ContentBlock> blocks = HtmlBlockAstParser.parse(106, html);
        assertTrue(blocks.size() >= 3);
        RichTextBlock b0 = (RichTextBlock) blocks.get(0);
        assertTrue(b0.getStyledText().getText().contains("Khối lồng & — 'A'"));

        RichTextBlock bLast = (RichTextBlock) blocks.get(blocks.size() - 1);
        assertTrue(bLast.getStyledText().getText().contains("Cuối bài"));

        // Stress test linear O(N) streaming scan with deeply nested unclosed markup (~120KB)
        StringBuilder hugeMalformed = new StringBuilder(130_000);
        for (int i = 0; i < 1500; i++) {
            hugeMalformed.append("<div><p><b><i>Đoạn số ").append(i)
                    .append(" <a href=\"https://example.com/").append(i).append("\">chi tiết")
                    .append("</span></bogus>");
        }
        long startNs = System.nanoTime();
        List<ContentBlock> hugeBlocks = HtmlBlockAstParser.parse(107, hugeMalformed.toString());
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
        assertEquals(1500, hugeBlocks.size());
        assertTrue("Expected fast linear parse without backtracking, took " + elapsedMs + "ms", elapsedMs < 1000L);
    }

    @Test
    public void testSummaryExtractionAndArticleAstCacheLru() {
        ArticleAstCache cache = new ArticleAstCache(2);
        String html1 = "<p>Đoạn một rất ngắn</p><p>Đoạn hai bổ sung thông tin</p>";
        String html2 = "<p>Bài hai</p>";
        String html3 = "<p>Bài ba</p>";

        assertNull(cache.getIfPresent(1, html1));
        ArticleAstCache.ParsedArticle a1 = cache.getOrParse(1, html1);
        assertEquals("Đoạn một rất ngắn Đoạn hai bổ sung thông tin", a1.plainTextSummary);
        assertSame(a1, cache.getIfPresent(1, html1));

        cache.getOrParse(2, html2);
        // Access article 1 again so article 2 becomes least recently used
        assertSame(a1, cache.getIfPresent(1, html1));
        cache.getOrParse(3, html3);

        assertNotNull(cache.getIfPresent(1, html1));
        assertNull(cache.getIfPresent(2, html2));
        assertNotNull(cache.getIfPresent(3, html3));

        cache.clear();
        assertNull(cache.getIfPresent(1, html1));
        assertEquals(0, cache.size());
    }

    private static boolean hasSpan(
            List<InlineSpanSpec> spans,
            String fullText,
            InlineSpanSpec.SpanType type,
            String expectedSubstr,
            String expectedUrl) {
        for (InlineSpanSpec span : spans) {
            if (span.type == type) {
                String actual = fullText.substring(span.start, span.end);
                if (expectedSubstr.equals(actual)) {
                    if (expectedUrl == null || expectedUrl.equals(span.url)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
