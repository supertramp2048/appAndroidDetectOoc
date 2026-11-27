package com.socialapp.core.ui.blocks.parser;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.socialapp.core.ui.blocks.model.ContentBlock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded LRU cache storing parsed HTML AST blocks and extracted plain-text summaries
 * keyed by (postId, contentHtmlHash), eliminating redundant re-parsing when Vote/Follow updates.
 */
public class ArticleAstCache {

    public static final class ParsedArticle {
        @NonNull
        public final List<ContentBlock> bodyBlocks;
        @NonNull
        public final String plainTextSummary;

        public ParsedArticle(@NonNull List<ContentBlock> bodyBlocks, @NonNull String plainTextSummary) {
            this.bodyBlocks = bodyBlocks;
            this.plainTextSummary = plainTextSummary;
        }
    }

    private static final class ArticleKey {
        final int postId;
        final int htmlHash;

        ArticleKey(int postId, int htmlHash) {
            this.postId = postId;
            this.htmlHash = htmlHash;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ArticleKey)) return false;
            ArticleKey that = (ArticleKey) o;
            return postId == that.postId && htmlHash == that.htmlHash;
        }

        @Override
        public int hashCode() {
            return Objects.hash(postId, htmlHash);
        }
    }

    private final int maxArticles;
    private final Object lock = new Object();
    private final LinkedHashMap<ArticleKey, ParsedArticle> lruMap;

    public ArticleAstCache() {
        this(16);
    }

    public ArticleAstCache(int maxArticles) {
        this.maxArticles = Math.max(1, maxArticles);
        this.lruMap = new LinkedHashMap<ArticleKey, ParsedArticle>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<ArticleKey, ParsedArticle> eldest) {
                return size() > ArticleAstCache.this.maxArticles;
            }
        };
    }

    @Nullable
    public ParsedArticle getIfPresent(int postId, @Nullable String contentHtml) {
        int hash = contentHtml != null ? contentHtml.hashCode() : 0;
        synchronized (lock) {
            return lruMap.get(new ArticleKey(postId, hash));
        }
    }

    @NonNull
    public ParsedArticle getOrParse(int postId, @Nullable String contentHtml) {
        int hash = contentHtml != null ? contentHtml.hashCode() : 0;
        ArticleKey key = new ArticleKey(postId, hash);
        synchronized (lock) {
            ParsedArticle existing = lruMap.get(key);
            if (existing != null) {
                return existing;
            }
        }

        List<ContentBlock> parsed = HtmlBlockAstParser.parse(postId, contentHtml);
        String summary = HtmlBlockAstParser.extractPlainTextSummary(parsed, 300);
        ParsedArticle entry = new ParsedArticle(parsed, summary);

        synchronized (lock) {
            lruMap.put(key, entry);
        }
        return entry;
    }

    public void clear() {
        synchronized (lock) {
            lruMap.clear();
        }
    }

    public int size() {
        synchronized (lock) {
            return lruMap.size();
        }
    }
}
