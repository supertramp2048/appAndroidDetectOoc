package com.socialapp.core.prefetch.policy.markov;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit Test chuẩn JUnit4 cho MarkovPredictor (Tasks B1-B5) chạy trực tiếp trong Android Studio.
 */
public class MarkovPredictorTest {

    @Test
    public void testHandCalculatedOrder2() {
        MarkovPredictor<String> pred = new MarkovPredictor<>(2, 3);
        List<String> ctx = Arrays.asList("A", "B");
        pred.learn(ctx, "C");
        pred.learn(ctx, "C");
        pred.learn(ctx, "D");

        List<Map.Entry<String, Double>> res = pred.predict(ctx);
        assertEquals(2, res.size());
        assertEquals("C", res.get(0).getKey());
        assertEquals(2.0 / 3.0, res.get(0).getValue(), 1e-6);
        assertEquals("D", res.get(1).getKey());
        assertEquals(1.0 / 3.0, res.get(1).getValue(), 1e-6);
    }

    @Test
    public void testColdStartBelowMinSupportReturnsEmpty() {
        MarkovPredictor<String> pred = new MarkovPredictor<>(2, 3);
        List<String> ctx = Arrays.asList("FEED_NEWEST", "POST_DETAIL");
        pred.learn(ctx, "COMMENTS");
        pred.learn(ctx, "COMMENTS"); // tổng = 2 < minSupport(3)

        assertTrue(pred.predict(ctx).isEmpty());
    }

    @Test
    public void testBackoffFromOrder2ToOrder1() {
        MarkovPredictor<String> pred = new MarkovPredictor<>(2, 3);
        for (int i = 0; i < 3; i++) {
            pred.learn(Arrays.asList("FEED_NEWEST", "POST_DETAIL"), "COMMENTS");
        }
        pred.learn(Arrays.asList("SEARCH", "POST_DETAIL"), "FACT_CHECK");

        // ["SEARCH", "POST_DETAIL"] mới có 1 mẫu (< 3) -> hạ bậc về ["POST_DETAIL"] (4 mẫu: 3 COMMENTS, 1 FACT_CHECK)
        List<Map.Entry<String, Double>> res = pred.predict(Arrays.asList("SEARCH", "POST_DETAIL"));
        assertEquals(2, res.size());
        assertEquals("COMMENTS", res.get(0).getKey());
        assertEquals(0.75, res.get(0).getValue(), 1e-6);
        assertEquals("FACT_CHECK", res.get(1).getKey());
        assertEquals(0.25, res.get(1).getValue(), 1e-6);
    }

    @Test
    public void testOrder2OverridesOrder1WhenSupportReached() {
        MarkovPredictor<String> pred = new MarkovPredictor<>(2, 3);
        for (int i = 0; i < 4; i++) {
            pred.learn(Arrays.asList("FEED_NEWEST", "POST_DETAIL"), "COMMENTS");
        }
        for (int i = 0; i < 3; i++) {
            pred.learn(Arrays.asList("SEARCH", "POST_DETAIL"), "FACT_CHECK");
        }

        List<Map.Entry<String, Double>> res = pred.predict(Arrays.asList("SEARCH", "POST_DETAIL"));
        assertEquals(1, res.size());
        assertEquals("FACT_CHECK", res.get(0).getKey());
        assertEquals(1.0, res.get(0).getValue(), 1e-6);
    }

    @Test
    public void testTieBreakingAlphabetical() {
        MarkovPredictor<String> pred = new MarkovPredictor<>(2, 3);
        List<String> ctx = Arrays.asList("FEED_NEWEST", "POST_DETAIL");
        pred.learn(ctx, "PROFILE");
        pred.learn(ctx, "COMMENTS");
        pred.learn(ctx, "PROFILE");
        pred.learn(ctx, "COMMENTS");

        List<Map.Entry<String, Double>> res = pred.predict(ctx);
        assertEquals(2, res.size());
        assertEquals("COMMENTS", res.get(0).getKey());
        assertEquals("PROFILE", res.get(1).getKey());
    }

    @Test
    public void testLongHistoryAndNullSafety() {
        MarkovPredictor<String> pred = new MarkovPredictor<>(2, 3);
        pred.learn(Arrays.asList("FEED_NEWEST", "POST_DETAIL", "COMMENTS"), "PROFILE");
        pred.learn(Arrays.asList("FEED_HOT", "POST_DETAIL", "COMMENTS"), "PROFILE");
        pred.learn(Arrays.asList("SEARCH", "POST_DETAIL", "COMMENTS"), "FEED_NEWEST");

        List<Map.Entry<String, Double>> res = pred.predict(
                Arrays.asList("FEED_NEWEST", "SEARCH", "POST_DETAIL", "COMMENTS")
        );
        assertEquals(2, res.size());
        assertEquals("PROFILE", res.get(0).getKey());
        assertEquals(2.0 / 3.0, res.get(0).getValue(), 1e-6);
        assertTrue(pred.predict(Collections.emptyList()).isEmpty());
        assertTrue(pred.predict(null).isEmpty());
    }
}
