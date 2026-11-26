package com.socialapp.core.ui.blocks.layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class BlockLayoutCacheTest {

    private static MeasuredBlockSpec stubSpec(String blockId, int contentHash, int widthPx, float fontScale, StyleId styleId, int heightPx) {
        return new MeasuredBlockSpec(blockId, contentHash, widthPx, fontScale, styleId, heightPx) {};
    }

    @Test
    public void testFiveFieldKeyIsolation_FeedVsDetailWidthFontScaleContentHash() {
        BlockLayoutCache cache = new BlockLayoutCache(32);

        BlockCacheKey feedKey = new BlockCacheKey("post_42", 1001, 1080, 1.0f, StyleId.FEED_TITLE);
        BlockCacheKey detailKey = new BlockCacheKey("post_42", 1001, 1080, 1.0f, StyleId.DETAIL_TITLE);
        BlockCacheKey rotatedKey = new BlockCacheKey("post_42", 1001, 2200, 1.0f, StyleId.FEED_TITLE);
        BlockCacheKey scaledFontKey = new BlockCacheKey("post_42", 1001, 1080, 1.3f, StyleId.FEED_TITLE);
        BlockCacheKey editedContentKey = new BlockCacheKey("post_42", 2002, 1080, 1.0f, StyleId.FEED_TITLE);

        MeasuredBlockSpec feedSpec = stubSpec("post_42", 1001, 1080, 1.0f, StyleId.FEED_TITLE, 240);
        MeasuredBlockSpec detailSpec = stubSpec("post_42", 1001, 1080, 1.0f, StyleId.DETAIL_TITLE, 520);
        MeasuredBlockSpec rotatedSpec = stubSpec("post_42", 1001, 2200, 1.0f, StyleId.FEED_TITLE, 180);
        MeasuredBlockSpec scaledSpec = stubSpec("post_42", 1001, 1080, 1.3f, StyleId.FEED_TITLE, 310);
        MeasuredBlockSpec editedSpec = stubSpec("post_42", 2002, 1080, 1.0f, StyleId.FEED_TITLE, 260);

        cache.put(feedKey, feedSpec);
        cache.put(detailKey, detailSpec);
        cache.put(rotatedKey, rotatedSpec);
        cache.put(scaledFontKey, scaledSpec);
        cache.put(editedContentKey, editedSpec);

        assertEquals(5, cache.size());
        assertSame(feedSpec, cache.get(new BlockCacheKey("post_42", 1001, 1080, 1.0f, StyleId.FEED_TITLE)));
        assertSame(detailSpec, cache.get(detailKey));
        assertSame(rotatedSpec, cache.get(rotatedKey));
        assertSame(scaledSpec, cache.get(scaledFontKey));
        assertSame(editedSpec, cache.get(editedContentKey));
        assertNotEquals(feedKey, detailKey);
    }

    @Test
    public void testLruEvictionRespectsAccessOrder() {
        BlockLayoutCache cache = new BlockLayoutCache(3);

        BlockCacheKey k1 = new BlockCacheKey("b1", 1, 1080, 1.0f, StyleId.FEED_TITLE);
        BlockCacheKey k2 = new BlockCacheKey("b2", 1, 1080, 1.0f, StyleId.FEED_TITLE);
        BlockCacheKey k3 = new BlockCacheKey("b3", 1, 1080, 1.0f, StyleId.FEED_TITLE);
        BlockCacheKey k4 = new BlockCacheKey("b4", 1, 1080, 1.0f, StyleId.FEED_TITLE);

        cache.put(k1, stubSpec("b1", 1, 1080, 1.0f, StyleId.FEED_TITLE, 100));
        cache.put(k2, stubSpec("b2", 1, 1080, 1.0f, StyleId.FEED_TITLE, 100));
        cache.put(k3, stubSpec("b3", 1, 1080, 1.0f, StyleId.FEED_TITLE, 100));

        // Access k1 so k2 becomes the least-recently-used entry
        assertNotNull(cache.get(k1));

        cache.put(k4, stubSpec("b4", 1, 1080, 1.0f, StyleId.FEED_TITLE, 100));

        assertEquals(3, cache.size());
        assertNotNull(cache.get(k1));
        assertNull(cache.get(k2));
        assertNotNull(cache.get(k3));
        assertNotNull(cache.get(k4));
    }

    @Test
    public void testTrimToFractionAndHitMissStats() {
        BlockLayoutCache cache = new BlockLayoutCache(10);
        for (int i = 0; i < 6; i++) {
            BlockCacheKey k = new BlockCacheKey("item_" + i, i, 1080, 1.0f, StyleId.COMMENT_BODY);
            cache.put(k, stubSpec("item_" + i, i, 1080, 1.0f, StyleId.COMMENT_BODY, 120));
        }

        assertEquals(6, cache.size());
        int removed = cache.trimToFraction(0.5f);
        assertEquals(3, removed);
        assertEquals(3, cache.size());

        // Oldest 0,1,2 removed; newest 3,4,5 retained
        assertNull(cache.get(new BlockCacheKey("item_0", 0, 1080, 1.0f, StyleId.COMMENT_BODY)));
        assertNotNull(cache.get(new BlockCacheKey("item_5", 5, 1080, 1.0f, StyleId.COMMENT_BODY)));
        assertEquals(1, cache.getHitCount());
        assertEquals(1, cache.getMissCount());

        cache.clear();
        assertEquals(0, cache.size());
    }
}
