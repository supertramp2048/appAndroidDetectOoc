package com.socialapp;

import com.socialapp.core.prefetch.policy.markov.MarkovPredictor;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
    }

    @Test
    public void testMarkovHandCalculatedOrder2() {
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
    public void testMarkovBackoffAndMinSupport() {
        MarkovPredictor<String> pred = new MarkovPredictor<>(2, 3);
        for (int i = 0; i < 3; i++) {
            pred.learn(Arrays.asList("FEED_NEWEST", "POST_DETAIL"), "COMMENTS");
        }
        pred.learn(Arrays.asList("SEARCH", "POST_DETAIL"), "FACT_CHECK");

        // ["SEARCH", "POST_DETAIL"] mới có 1 mẫu (< 3) -> tự động hạ bậc về ["POST_DETAIL"] (4 mẫu)
        List<Map.Entry<String, Double>> res = pred.predict(Arrays.asList("SEARCH", "POST_DETAIL"));
        assertEquals(2, res.size());
        assertEquals("COMMENTS", res.get(0).getKey());
        assertEquals(0.75, res.get(0).getValue(), 1e-6);
        assertEquals("FACT_CHECK", res.get(1).getKey());
        assertEquals(0.25, res.get(1).getValue(), 1e-6);
    }
}
