package com.socialapp.core.ui.blocks.parser;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.socialapp.core.ui.blocks.model.CodeBlock;
import com.socialapp.core.ui.blocks.model.ContentBlock;
import com.socialapp.core.ui.blocks.model.InlineImageBlock;
import com.socialapp.core.ui.blocks.model.InlineSpanSpec;
import com.socialapp.core.ui.blocks.model.QuoteBlock;
import com.socialapp.core.ui.blocks.model.RichTextBlock;
import com.socialapp.core.ui.blocks.model.StyledTextSpec;
import com.socialapp.core.ui.blocks.model.TableBlock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Streaming Tag-Stack HTML Parser (pure Java, ZERO regex tag parsing).
 * Converts post.contentHtml into an immutable list of atomic ContentBlocks matching
 * editor.html and PostDetailActivity's NYT Editorial CSS rules.
 */
public final class HtmlBlockAstParser {

    private static final Set<String> VOID_TAGS = new HashSet<>();
    private static final Set<String> SKIP_BODY_TAGS = new HashSet<>();
    private static final Set<String> BLOCK_CONTAINER_TAGS = new HashSet<>();

    static {
        Collections.addAll(VOID_TAGS, "br", "img", "hr", "input", "meta", "link", "source", "wbr", "col");
        Collections.addAll(SKIP_BODY_TAGS, "script", "style", "head", "noscript");
        Collections.addAll(BLOCK_CONTAINER_TAGS,
                "p", "div", "section", "article", "header", "footer", "main", "aside", "figure",
                "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "ul", "ol", "li", "pre", "table", "figcaption");
    }

    private HtmlBlockAstParser() {
    }

    @NonNull
    public static List<ContentBlock> parse(int postId, @Nullable String contentHtml) {
        if (contentHtml == null || contentHtml.trim().isEmpty()) {
            return Collections.emptyList();
        }
        ParserSession session = new ParserSession(postId, contentHtml);
        return session.parseAll();
    }

    /**
     * Extracts plain text summary from already-parsed body blocks without Html.fromHtml() on Main Thread.
     */
    @NonNull
    public static String extractPlainTextSummary(@NonNull List<ContentBlock> blocks, int maxChars) {
        if (blocks.isEmpty() || maxChars <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : blocks) {
            String piece = null;
            if (block instanceof RichTextBlock) {
                piece = ((RichTextBlock) block).getStyledText().getText();
            } else if (block instanceof QuoteBlock) {
                piece = ((QuoteBlock) block).getStyledText().getText();
            } else if (block instanceof CodeBlock) {
                piece = ((CodeBlock) block).getCodeText();
            }
            if (piece != null) {
                String trimmed = piece.trim();
                if (!trimmed.isEmpty()) {
                    if (sb.length() > 0) {
                        sb.append(' ');
                    }
                    sb.append(trimmed);
                    if (sb.length() > maxChars) {
                        break;
                    }
                }
            }
        }
        String result = sb.toString().trim();
        if (result.length() > maxChars) {
            return result.substring(0, maxChars) + "...";
        }
        return result;
    }

    private static final class ListContext {
        final boolean ordered;
        int nextIndex = 1;

        ListContext(boolean ordered) {
            this.ordered = ordered;
        }
    }

    private static final class OpenInline {
        final String tagName;
        final InlineSpanSpec.SpanType type;
        final int startOffset;
        @Nullable
        final String url;

        OpenInline(String tagName, InlineSpanSpec.SpanType type, int startOffset, @Nullable String url) {
            this.tagName = tagName;
            this.type = type;
            this.startOffset = startOffset;
            this.url = url;
        }
    }

    private static final class StyledBuffer {
        final StringBuilder text = new StringBuilder();
        final List<InlineSpanSpec> spans = new ArrayList<>();
        final Deque<OpenInline> openInlines = new ArrayDeque<>();
        boolean pendingSpace = false;

        boolean isEmpty() {
            return text.length() == 0;
        }

        void appendText(@NonNull String decoded) {
            int len = decoded.length();
            for (int i = 0; i < len; i++) {
                char c = decoded.charAt(i);
                if (Character.isWhitespace(c)) {
                    if (text.length() > 0 && text.charAt(text.length() - 1) != '\n') {
                        pendingSpace = true;
                    }
                } else {
                    if (pendingSpace) {
                        text.append(' ');
                        pendingSpace = false;
                    }
                    text.append(c);
                }
            }
        }

        void appendRawLiteral(@NonNull String literal) {
            pendingSpace = false;
            text.append(literal);
        }

        void appendLineBreak() {
            pendingSpace = false;
            if (text.length() > 0 && text.charAt(text.length() - 1) != '\n') {
                text.append('\n');
            }
        }

        void appendParagraphBreakInsideQuote() {
            pendingSpace = false;
            if (text.length() == 0) return;
            if (!text.toString().endsWith("\n\n")) {
                if (text.charAt(text.length() - 1) == '\n') {
                    text.append('\n');
                } else {
                    text.append("\n\n");
                }
            }
        }

        void pushInline(@NonNull String tagName, @NonNull InlineSpanSpec.SpanType type, @Nullable String url) {
            int effectiveStart = text.length() + (pendingSpace ? 1 : 0);
            openInlines.push(new OpenInline(tagName, type, effectiveStart, url));
        }

        void popInline(@NonNull String tagName) {
            if (openInlines.isEmpty()) return;
            Deque<OpenInline> toReopen = new ArrayDeque<>();
            OpenInline matched = null;
            while (!openInlines.isEmpty()) {
                OpenInline top = openInlines.pop();
                int end = text.length();
                if (end > top.startOffset) {
                    spans.add(new InlineSpanSpec(top.type, top.startOffset, end, top.url));
                }
                if (top.tagName.equals(tagName)) {
                    matched = top;
                    break;
                }
                toReopen.push(top);
            }
            if (matched == null) {
                // Closing tag wasn't opened; restore stack cleanly
                while (!toReopen.isEmpty()) {
                    openInlines.push(toReopen.pop());
                }
            } else {
                // Reopen nested spans that weren't closed before the outer tag
                int reopenOffset = text.length() + (pendingSpace ? 1 : 0);
                while (!toReopen.isEmpty()) {
                    OpenInline inner = toReopen.pop();
                    openInlines.push(new OpenInline(inner.tagName, inner.type, reopenOffset, inner.url));
                }
            }
        }

        @NonNull
        StyledTextSpec buildAndReset() {
            int end = text.length();
            while (!openInlines.isEmpty()) {
                OpenInline top = openInlines.pop();
                if (end > top.startOffset) {
                    spans.add(new InlineSpanSpec(top.type, top.startOffset, end, top.url));
                }
            }
            StyledTextSpec spec = new StyledTextSpec(text.toString(), spans);
            text.setLength(0);
            spans.clear();
            pendingSpace = false;
            return spec;
        }
    }

    private static final class ParserSession {
        private final int postId;
        private final String html;
        private final int length;
        private int pos = 0;

        private final List<ContentBlock> blocks = new ArrayList<>();
        private final StyledBuffer buffer = new StyledBuffer();
        private final Deque<ListContext> listStack = new ArrayDeque<>();

        private int blockSeq = 0;
        private boolean leadAssigned = false;

        // Active block contexts
        @Nullable
        private RichTextBlock.TextStyle pendingHeadingStyle = null;
        private boolean pendingLeadClass = false;
        private boolean inBlockquote = false;
        private boolean inListItem = false;
        private boolean inFigcaption = false;
        private boolean justEmittedImage = false;

        // <pre> raw collector
        private boolean inPre = false;
        private final StringBuilder preBuffer = new StringBuilder();
        @Nullable
        private String preLanguage = null;

        // <table> collector
        private boolean inTable = false;
        private boolean inTableCell = false;
        private boolean currentCellHeader = false;
        private final List<TableBlock.TableRow> tableRows = new ArrayList<>();
        private List<TableBlock.TableCell> currentRowCells = new ArrayList<>();
        private final StringBuilder cellBuffer = new StringBuilder();

        ParserSession(int postId, @NonNull String html) {
            this.postId = postId;
            this.html = html;
            this.length = html.length();
        }

        @NonNull
        List<ContentBlock> parseAll() {
            while (pos < length) {
                int lt = html.indexOf('<', pos);
                if (lt < 0) {
                    handleText(html.substring(pos));
                    pos = length;
                    break;
                }
                if (lt > pos) {
                    handleText(html.substring(pos, lt));
                }
                pos = lt;
                parseTagOrComment();
            }

            // Flush any dangling open state at EOF (graceful recovery on malformed HTML)
            if (inPre) {
                emitCodeBlock();
            }
            if (inTable) {
                finishTable();
            }
            flushBuffer();
            return Collections.unmodifiableList(blocks);
        }

        private void parseTagOrComment() {
            if (html.startsWith("<!--", pos)) {
                int close = html.indexOf("-->", pos + 4);
                pos = close >= 0 ? close + 3 : length;
                return;
            }
            if (html.startsWith("<!", pos) || html.startsWith("<?", pos)) {
                int gt = html.indexOf('>', pos + 2);
                pos = gt >= 0 ? gt + 1 : length;
                return;
            }

            int tagEnd = findTagEndOutsideQuotes(pos + 1);
            if (tagEnd < 0) {
                // Dangling '<' without '>' — treat remaining text literally so no text is lost
                handleText(html.substring(pos));
                pos = length;
                return;
            }

            String rawInside = html.substring(pos + 1, tagEnd).trim();
            pos = tagEnd + 1;
            if (rawInside.isEmpty()) {
                return;
            }

            boolean isClosing = rawInside.charAt(0) == '/';
            if (isClosing) {
                String closeName = extractTagName(rawInside, 1);
                if (!closeName.isEmpty()) {
                    handleClosingTag(closeName);
                }
                return;
            }

            boolean selfClosing = rawInside.endsWith("/");
            if (selfClosing) {
                rawInside = rawInside.substring(0, rawInside.length() - 1).trim();
            }
            String tagName = extractTagName(rawInside, 0);
            if (tagName.isEmpty()) {
                return;
            }

            if (SKIP_BODY_TAGS.contains(tagName)) {
                if (!selfClosing) {
                    skipUntilClosingTag(tagName);
                }
                return;
            }

            Map<String, String> attrs = parseAttributes(rawInside, tagName.length());
            handleOpeningTag(tagName, attrs, selfClosing || VOID_TAGS.contains(tagName));
        }

        private int findTagEndOutsideQuotes(int start) {
            char quote = 0;
            for (int i = start; i < length; i++) {
                char c = html.charAt(i);
                if (quote != 0) {
                    if (c == quote) {
                        quote = 0;
                    }
                } else {
                    if (c == '"' || c == '\'') {
                        quote = c;
                    } else if (c == '>') {
                        return i;
                    }
                }
            }
            return -1;
        }

        private void skipUntilClosingTag(@NonNull String tagName) {
            String lowerHtml = html.toLowerCase(Locale.US);
            String needle = "</" + tagName;
            int idx = lowerHtml.indexOf(needle, pos);
            if (idx < 0) {
                pos = length;
                return;
            }
            int gt = html.indexOf('>', idx + needle.length());
            pos = gt >= 0 ? gt + 1 : length;
        }

        private void handleText(@NonNull String rawText) {
            if (rawText.isEmpty()) return;
            String decoded = HtmlEntityDecoder.decode(rawText);

            if (inPre) {
                preBuffer.append(decoded);
                return;
            }
            if (inTable) {
                if (inTableCell) {
                    cellBuffer.append(decoded.replace('\r', ' ').replace('\n', ' '));
                }
                return;
            }
            if (!decoded.trim().isEmpty()) {
                // Only reset justEmittedImage if non-whitespace text arrives outside caption context
                // (actual attach-or-clear decision happens when block flushes)
            }
            buffer.appendText(decoded);
        }

        private void handleOpeningTag(@NonNull String tag, @NonNull Map<String, String> attrs, boolean selfClosing) {
            if (inPre) {
                if ("br".equals(tag)) {
                    preBuffer.append('\n');
                }
                return;
            }

            if (inTable) {
                if ("tr".equals(tag)) {
                    flushTableCell();
                    flushTableRow();
                    currentRowCells = new ArrayList<>();
                } else if ("th".equals(tag) || "td".equals(tag)) {
                    flushTableCell();
                    inTableCell = true;
                    currentCellHeader = "th".equals(tag);
                } else if ("br".equals(tag) && inTableCell) {
                    cellBuffer.append('\n');
                } else if ("p".equals(tag) && inTableCell && cellBuffer.length() > 0
                        && cellBuffer.charAt(cellBuffer.length() - 1) != '\n') {
                    cellBuffer.append('\n');
                }
                return;
            }

            switch (tag) {
                case "br":
                    buffer.appendLineBreak();
                    return;

                case "hr":
                    flushBuffer();
                    justEmittedImage = false;
                    return;

                case "img":
                    flushBuffer();
                    emitImageBlock(attrs);
                    return;

                case "h1":
                    flushBuffer();
                    justEmittedImage = false;
                    pendingHeadingStyle = RichTextBlock.TextStyle.HEADING_H1;
                    return;
                case "h2":
                    flushBuffer();
                    justEmittedImage = false;
                    pendingHeadingStyle = RichTextBlock.TextStyle.HEADING_H2;
                    return;
                case "h3":
                    flushBuffer();
                    justEmittedImage = false;
                    pendingHeadingStyle = RichTextBlock.TextStyle.HEADING_H3;
                    return;
                case "h4":
                case "h5":
                case "h6":
                    flushBuffer();
                    justEmittedImage = false;
                    pendingHeadingStyle = RichTextBlock.TextStyle.HEADING_H4;
                    return;

                case "blockquote":
                    flushBuffer();
                    justEmittedImage = false;
                    inBlockquote = true;
                    return;

                case "pre":
                    flushBuffer();
                    justEmittedImage = false;
                    inPre = true;
                    preBuffer.setLength(0);
                    preLanguage = extractCodeLanguage(attrs.get("class"));
                    return;

                case "table":
                    flushBuffer();
                    justEmittedImage = false;
                    inTable = true;
                    tableRows.clear();
                    currentRowCells = new ArrayList<>();
                    cellBuffer.setLength(0);
                    inTableCell = false;
                    return;

                case "ul":
                    flushBuffer();
                    justEmittedImage = false;
                    listStack.push(new ListContext(false));
                    return;

                case "ol":
                    flushBuffer();
                    justEmittedImage = false;
                    listStack.push(new ListContext(true));
                    return;

                case "li":
                    flushBuffer();
                    justEmittedImage = false;
                    inListItem = true;
                    if (!listStack.isEmpty()) {
                        ListContext ctx = listStack.peek();
                        if (ctx.ordered) {
                            buffer.appendRawLiteral((ctx.nextIndex++) + ". ");
                        } else {
                            buffer.appendRawLiteral("• ");
                        }
                    } else {
                        buffer.appendRawLiteral("• ");
                    }
                    return;

                case "figcaption":
                    flushBuffer();
                    inFigcaption = true;
                    return;

                case "p":
                case "div":
                case "section":
                case "article":
                case "figure":
                    if (inBlockquote) {
                        buffer.appendParagraphBreakInsideQuote();
                    } else {
                        flushBuffer();
                    }
                    String cls = attrs.get("class");
                    if (cls != null && cls.toLowerCase(Locale.US).contains("lead")) {
                        pendingLeadClass = true;
                    }
                    return;

                // Inline formatting tags
                case "b":
                case "strong":
                    buffer.pushInline(tag, InlineSpanSpec.SpanType.BOLD, null);
                    return;
                case "i":
                case "em":
                    buffer.pushInline(tag, InlineSpanSpec.SpanType.ITALIC, null);
                    return;
                case "u":
                    buffer.pushInline(tag, InlineSpanSpec.SpanType.UNDERLINE, null);
                    return;
                case "code":
                    buffer.pushInline(tag, InlineSpanSpec.SpanType.INLINE_CODE, null);
                    return;
                case "a":
                    String href = attrs.get("href");
                    if (href != null && !href.trim().isEmpty()) {
                        buffer.pushInline(tag, InlineSpanSpec.SpanType.LINK, href.trim());
                    }
                    return;

                default:
                    // Graceful fallback for unknown tags: keep inline/text content without losing characters
                    if (!selfClosing && BLOCK_CONTAINER_TAGS.contains(tag)) {
                        flushBuffer();
                    }
            }
        }

        private void handleClosingTag(@NonNull String tag) {
            if (inPre) {
                if ("pre".equals(tag)) {
                    emitCodeBlock();
                }
                return;
            }

            if (inTable) {
                if ("th".equals(tag) || "td".equals(tag)) {
                    flushTableCell();
                } else if ("tr".equals(tag)) {
                    flushTableCell();
                    flushTableRow();
                } else if ("table".equals(tag)) {
                    finishTable();
                }
                return;
            }

            switch (tag) {
                case "h1":
                case "h2":
                case "h3":
                case "h4":
                case "h5":
                case "h6":
                    flushBuffer();
                    pendingHeadingStyle = null;
                    return;

                case "blockquote":
                    flushBuffer();
                    inBlockquote = false;
                    return;

                case "li":
                    flushBuffer();
                    inListItem = false;
                    return;

                case "ul":
                case "ol":
                    flushBuffer();
                    if (!listStack.isEmpty()) {
                        listStack.pop();
                    }
                    return;

                case "figcaption":
                    flushBuffer();
                    inFigcaption = false;
                    return;

                case "p":
                case "div":
                case "section":
                case "article":
                case "figure":
                    if (!inBlockquote) {
                        flushBuffer();
                        pendingLeadClass = false;
                    }
                    return;

                case "b":
                case "strong":
                case "i":
                case "em":
                case "u":
                case "code":
                case "a":
                    buffer.popInline(tag);
                    return;

                default:
                    if (BLOCK_CONTAINER_TAGS.contains(tag) && !inBlockquote) {
                        flushBuffer();
                    }
            }
        }

        private void flushBuffer() {
            if (buffer.isEmpty()) {
                buffer.buildAndReset();
                return;
            }
            StyledTextSpec spec = buffer.buildAndReset();
            if (spec.isEmpty()) {
                return;
            }

            // Attach <figcaption> or standalone short <em> caption immediately after <img>
            if ((inFigcaption || isStandaloneEmCaption(spec)) && justEmittedImage && !blocks.isEmpty()) {
                int lastIdx = blocks.size() - 1;
                ContentBlock last = blocks.get(lastIdx);
                if (last instanceof InlineImageBlock) {
                    InlineImageBlock img = (InlineImageBlock) last;
                    if (img.getCaption() == null) {
                        blocks.set(lastIdx, img.withCaption(spec.getText().trim()));
                        justEmittedImage = false;
                        return;
                    }
                }
            }

            justEmittedImage = false;

            if (inBlockquote) {
                blocks.add(new QuoteBlock(nextBlockId("quote"), spec));
                return;
            }

            RichTextBlock.TextStyle style;
            if (pendingHeadingStyle != null) {
                style = pendingHeadingStyle;
            } else if (inListItem) {
                style = RichTextBlock.TextStyle.LIST_ITEM;
            } else if (pendingLeadClass || !leadAssigned) {
                style = RichTextBlock.TextStyle.LEAD;
                leadAssigned = true;
            } else {
                style = RichTextBlock.TextStyle.PARAGRAPH;
            }

            int listLevel = inListItem ? Math.max(0, listStack.size() - 1) : 0;
            blocks.add(new RichTextBlock(nextBlockId("rt"), style, spec, listLevel));
        }

        private boolean isStandaloneEmCaption(@NonNull StyledTextSpec spec) {
            if (pendingHeadingStyle != null || inBlockquote || inListItem) {
                return false;
            }
            String text = spec.getText().trim();
            if (text.isEmpty() || text.length() > 250) {
                return false;
            }
            List<InlineSpanSpec> spans = spec.getSpans();
            if (spans.size() != 1) {
                return false;
            }
            InlineSpanSpec span = spans.get(0);
            return span.type == InlineSpanSpec.SpanType.ITALIC
                    && span.start == 0
                    && span.end >= text.length();
        }

        private void emitImageBlock(@NonNull Map<String, String> attrs) {
            String src = attrs.get("src");
            if (src == null || src.trim().isEmpty()) {
                return;
            }
            String alt = attrs.get("alt");
            int w = parsePositiveInt(attrs.get("width"));
            int h = parsePositiveInt(attrs.get("height"));
            blocks.add(new InlineImageBlock(nextBlockId("img"), src.trim(), alt, null, w, h));
            justEmittedImage = true;
        }

        private void emitCodeBlock() {
            inPre = false;
            String raw = preBuffer.toString();
            preBuffer.setLength(0);
            // Strip a single leading/trailing blank line created by <pre>\n...\n</pre>
            if (raw.startsWith("\r\n")) {
                raw = raw.substring(2);
            } else if (raw.startsWith("\n")) {
                raw = raw.substring(1);
            }
            if (raw.endsWith("\r\n")) {
                raw = raw.substring(0, raw.length() - 2);
            } else if (raw.endsWith("\n")) {
                raw = raw.substring(0, raw.length() - 1);
            }
            if (!raw.trim().isEmpty()) {
                blocks.add(new CodeBlock(nextBlockId("code"), raw, preLanguage));
            }
            preLanguage = null;
        }

        private void flushTableCell() {
            if (!inTableCell) {
                return;
            }
            inTableCell = false;
            String raw = cellBuffer.toString();
            cellBuffer.setLength(0);
            StringBuilder out = new StringBuilder(raw.length());
            int lineStart = 0;
            for (int i = 0; i <= raw.length(); i++) {
                if (i == raw.length() || raw.charAt(i) == '\n') {
                    String segment = collapseInlineWhitespace(raw.substring(lineStart, i));
                    if (!segment.isEmpty()) {
                        if (out.length() > 0) {
                            out.append('\n');
                        }
                        out.append(segment);
                    }
                    lineStart = i + 1;
                }
            }
            currentRowCells.add(new TableBlock.TableCell(out.toString(), currentCellHeader));
            currentCellHeader = false;
        }

        private void flushTableRow() {
            if (!currentRowCells.isEmpty()) {
                tableRows.add(new TableBlock.TableRow(currentRowCells));
                currentRowCells = new ArrayList<>();
            }
        }

        private void finishTable() {
            flushTableCell();
            flushTableRow();
            inTable = false;
            if (!tableRows.isEmpty()) {
                blocks.add(new TableBlock(nextBlockId("table"), tableRows));
            }
            tableRows.clear();
            currentRowCells = new ArrayList<>();
            cellBuffer.setLength(0);
        }

        private String nextBlockId(@NonNull String kind) {
            return "detail_" + postId + "_" + kind + "_" + (blockSeq++);
        }

        @NonNull
        private static String extractTagName(@NonNull String raw, int startIdx) {
            int i = startIdx;
            int len = raw.length();
            while (i < len) {
                char c = raw.charAt(i);
                if (Character.isWhitespace(c) || c == '/' || c == '>') {
                    break;
                }
                i++;
            }
            if (i <= startIdx) {
                return "";
            }
            return raw.substring(startIdx, i).toLowerCase(Locale.US);
        }

        @NonNull
        private static Map<String, String> parseAttributes(@NonNull String raw, int startIdx) {
            Map<String, String> attrs = new HashMap<>();
            int i = startIdx;
            int len = raw.length();
            while (i < len) {
                while (i < len && Character.isWhitespace(raw.charAt(i))) {
                    i++;
                }
                if (i >= len) break;

                int nameStart = i;
                while (i < len) {
                    char c = raw.charAt(i);
                    if (Character.isWhitespace(c) || c == '=') {
                        break;
                    }
                    i++;
                }
                String attrName = raw.substring(nameStart, i).toLowerCase(Locale.US);
                while (i < len && Character.isWhitespace(raw.charAt(i))) {
                    i++;
                }

                String attrVal = "";
                if (i < len && raw.charAt(i) == '=') {
                    i++;
                    while (i < len && Character.isWhitespace(raw.charAt(i))) {
                        i++;
                    }
                    if (i < len) {
                        char quote = raw.charAt(i);
                        if (quote == '"' || quote == '\'') {
                            i++;
                            int valStart = i;
                            int close = raw.indexOf(quote, valStart);
                            if (close >= 0) {
                                attrVal = raw.substring(valStart, close);
                                i = close + 1;
                            } else {
                                attrVal = raw.substring(valStart);
                                i = len;
                            }
                        } else {
                            int valStart = i;
                            while (i < len && !Character.isWhitespace(raw.charAt(i))) {
                                i++;
                            }
                            attrVal = raw.substring(valStart, i);
                        }
                    }
                }
                if (!attrName.isEmpty()) {
                    attrs.put(attrName, HtmlEntityDecoder.decode(attrVal));
                }
            }
            return attrs;
        }

        @Nullable
        private static String extractCodeLanguage(@Nullable String classAttr) {
            if (classAttr == null || classAttr.isEmpty()) return null;
            int idx = classAttr.indexOf("language-");
            if (idx >= 0) {
                int start = idx + "language-".length();
                int end = start;
                while (end < classAttr.length() && !Character.isWhitespace(classAttr.charAt(end))) {
                    end++;
                }
                if (end > start) {
                    return classAttr.substring(start, end);
                }
            }
            return null;
        }

        private static int parsePositiveInt(@Nullable String val) {
            if (val == null || val.trim().isEmpty()) return 0;
            try {
                String clean = val.trim();
                if (clean.endsWith("px")) {
                    clean = clean.substring(0, clean.length() - 2).trim();
                }
                return Math.max(0, Integer.parseInt(clean));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }

        @NonNull
        private static String collapseInlineWhitespace(@NonNull String input) {
            StringBuilder out = new StringBuilder(input.length());
            boolean pendingSpace = false;
            for (int i = 0; i < input.length(); i++) {
                char c = input.charAt(i);
                if (Character.isWhitespace(c)) {
                    if (out.length() > 0) {
                        pendingSpace = true;
                    }
                } else {
                    if (pendingSpace) {
                        out.append(' ');
                        pendingSpace = false;
                    }
                    out.append(c);
                }
            }
            return out.toString();
        }
    }
}
