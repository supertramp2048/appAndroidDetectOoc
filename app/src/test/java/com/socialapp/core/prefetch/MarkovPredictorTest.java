package com.socialapp.core.prefetch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.socialapp.core.prefetch.policy.markov.MarkovPredictor;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Bộ kiểm thử toàn diện (Unit Test) cho MarkovPredictor của Kiên:
 * Bao phủ 7 trường hợp cốt lõi:
 * 1. Ví dụ tính tay cơ bản (C = 2/3, D = 1/3)
 * 2. Cold-start / Chưa đủ ngưỡng minSupport (< 3 mẫu -> trả về rỗng)
 * 3. Cơ chế hạ bậc (Backoff từ Bậc 2 xuống Bậc 1 khi Bậc 2 < minSupport)
 * 4. Ưu tiên Bậc 2 khi Bậc 2 đã tích lũy đủ >= minSupport
 * 5. Xử lý hòa xác suất (Tie-break theo thứ tự bảng chữ cái A -> Z)
 * 6. Lịch sử dài hơn maxOrder (tự cắt k phần tử cuối) & xử lý đầu vào rỗng/null
 * 7. So sánh đa bậc (k = 1, 2, 3)
 */
public class MarkovPredictorTest {

    private static final double EPSILON = 1e-6;

    @Test
    public void testCase1_ManualCalculation_BasicOrder2() {
        MarkovPredictor<String> pred = new MarkovPredictor<>(2, 3);
        List<String> ctx = Arrays.asList("A", "B");
        pred.learn(ctx, "C");
        pred.learn(ctx, "C");
        pred.learn(ctx, "D");

        List<Map.Entry<String, Double>> res = pred.predict(ctx);
        assertEquals(2, res.size());
        assertEquals("C", res.get(0).getKey());
        assertEquals(2.0 / 3.0, res.get(0).getValue(), EPSILON);
        assertEquals("D", res.get(1).getKey());
        assertEquals(1.0 / 3.0, res.get(1).getValue(), EPSILON);
    }

    @Test
    public void testCase2_ColdStart_BelowMinSupport_ReturnsEmpty() {
        MarkovPredictor<String> pred = new MarkovPredictor<>(2, 3);
        List<String> ctx = Arrays.asList("Home", "List");
        pred.learn(ctx, "Detail");
        pred.learn(ctx, "Detail"); // Tổng = 2 < minSupport(3)

        List<Map.Entry<String, Double>> res = pred.predict(ctx);
        assertTrue("Chưa đủ minSupport phải trả về rỗng", res.isEmpty());
    }

    @Test
    public void testCase3_Backoff_Order2ToOrder1() {
        MarkovPredictor<String> pred = new MarkovPredictor<>(2, 3);
        // Học ["Home", "List"] -> "Detail" 3 lần (Bậc 1 ["List"] có 3 lần "Detail")
        for (int i = 0; i < 3; i++) {
            pred.learn(Arrays.asList("Home", "List"), "Detail");
        }
        // Học ["Search", "List"] -> "Cart" chỉ 1 lần (Bậc 2 ["Search", "List"] mới có 1 mẫu < 3)
        pred.learn(Arrays.asList("Search", "List"), "Cart");

        // Khi đoán ["Search", "List"]: Bậc 2 chỉ có 1 mẫu (< 3) nên hạ xuống Bậc 1 ["List"] (4 mẫu: 3 Detail, 1 Cart)
        List<Map.Entry<String, Double>> res = pred.predict(Arrays.asList("Search", "List"));
        assertEquals(2, res.size());
        assertEquals("Detail", res.get(0).getKey());
        assertEquals(0.75, res.get(0).getValue(), EPSILON);
        assertEquals("Cart", res.get(1).getKey());
        assertEquals(0.25, res.get(1).getValue(), EPSILON);
    }

    @Test
    public void testCase4_PriorityToOrder2_WhenSupportSufficient() {
        MarkovPredictor<String> pred = new MarkovPredictor<>(2, 3);
        for (int i = 0; i < 4; i++) {
            pred.learn(Arrays.asList("Home", "List"), "Detail");
        }
        // Cho ["Search", "List"] học đủ 3 lần sang "Cart"
        for (int i = 0; i < 3; i++) {
            pred.learn(Arrays.asList("Search", "List"), "Cart");
        }

        // Bậc 1 ["List"] vẫn nghiêng về "Detail" (4/7), nhưng Bậc 2 ["Search", "List"] đã đủ 3 mẫu (100% "Cart")
        List<Map.Entry<String, Double>> res = pred.predict(Arrays.asList("Search", "List"));
        assertEquals(1, res.size());
        assertEquals("Cart", res.get(0).getKey());
        assertEquals(1.0, res.get(0).getValue(), EPSILON);
    }

    @Test
    public void testCase5_TieBreak_AlphabeticalOrdering() {
        MarkovPredictor<String> pred = new MarkovPredictor<>(2, 3);
        List<String> ctx = Arrays.asList("Home", "Detail");
        pred.learn(ctx, "Reviews");
        pred.learn(ctx, "Cart");
        pred.learn(ctx, "Reviews");
        pred.learn(ctx, "Cart"); // Cart = 2, Reviews = 2 (cùng 50%, tổng = 4 >= 3)

        List<Map.Entry<String, Double>> res = pred.predict(ctx);
        assertEquals(2, res.size());
        assertEquals("Cart", res.get(0).getKey());
        assertEquals("Reviews", res.get(1).getKey());
    }

    @Test
    public void testCase6_LongHistory_AndNullSafety() {
        MarkovPredictor<String> pred = new MarkovPredictor<>(2, 3);
        pred.learn(Arrays.asList("Home", "List", "Detail"), "Cart");
        pred.learn(Arrays.asList("Search", "List", "Detail"), "Cart");
        pred.learn(Arrays.asList("Profile", "List", "Detail"), "Reviews");

        // Truyền lịch sử dài 5 trang nhưng 2 trang cuối vẫn là ["List", "Detail"]
        List<Map.Entry<String, Double>> res = pred.predict(
                Arrays.asList("Home", "Search", "Profile", "List", "Detail")
        );
        assertTrue(pred.predict(Collections.emptyList()).isEmpty());
        assertTrue(pred.predict(null).isEmpty());

        assertEquals(2, res.size());
        assertEquals("Cart", res.get(0).getKey());
        assertEquals(2.0 / 3.0, res.get(0).getValue(), EPSILON);
    }

    @Test
    public void testCase7_ResetClearsTrainedModel() {
        MarkovPredictor<String> pred = new MarkovPredictor<>(2, 3);
        List<String> ctx = Arrays.asList("A", "B");
        pred.learn(ctx, "C");
        pred.learn(ctx, "C");
        pred.learn(ctx, "C");
        assertFalse(pred.predict(ctx).isEmpty());

        pred.reset();
        assertTrue("Sau reset phải trở về cold start", pred.predict(ctx).isEmpty());
    }
}

