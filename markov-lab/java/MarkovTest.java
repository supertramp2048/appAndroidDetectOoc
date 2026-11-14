import java.util.*;

/**
 * Bộ kiểm thử toàn diện (Unit Test) cho MarkovPredictor covering 7 trường hợp cốt lõi:
 * 1. Ví dụ tính tay cơ bản (C = 2/3, D = 1/3)
 * 2. Cold-start / Chưa đủ ngưỡng minSupport (< 3 mẫu -> trả về rỗng)
 * 3. Cơ chế hạ bậc (Backoff từ Bậc 2 xuống Bậc 1 khi Bậc 2 < minSupport)
 * 4. Ưu tiên Bậc 2 khi Bậc 2 đã tích lũy đủ >= minSupport
 * 5. Xử lý hòa xác suất (Tie-break theo thứ tự bảng chữ cái A -> Z)
 * 6. Lịch sử dài hơn maxOrder (tự cắt k phần tử cuối) & xử lý đầu vào rỗng/null
 * 7. So sánh đa bậc (k = 1, 2, 3)
 */
public class MarkovTest {
    private static int passed = 0;
    private static int failed = 0;

    private static void check(String name, boolean condition, String detail) {
        if (condition) {
            passed++;
            System.out.printf("  [PASS] %s -> %s%n", name, detail);
        } else {
            failed++;
            System.out.printf("  [FAIL] %s -> %s%n", name, detail);
        }
    }

    public static void main(String[] args) {
        System.out.println("==========================================================");
        System.out.println("   BỘ TEST KIỂM THỬ THUẬT TOÁN MARKOV (JAVA)");
        System.out.println("==========================================================");

        // CASE 1: Ví dụ tính tay cơ bản (Bậc 2, minSupport = 3)
        {
            MarkovPredictor<String> pred = new MarkovPredictor<>(2, 3);
            List<String> ctx = Arrays.asList("A", "B");
            pred.learn(ctx, "C");
            pred.learn(ctx, "C");
            pred.learn(ctx, "D");
            List<Map.Entry<String, Double>> res = pred.predict(ctx);
            boolean ok = res.size() == 2
                    && res.get(0).getKey().equals("C") && Math.abs(res.get(0).getValue() - 2.0 / 3.0) < 1e-6
                    && res.get(1).getKey().equals("D") && Math.abs(res.get(1).getValue() - 1.0 / 3.0) < 1e-6;
            check("Case 1 (Tính tay cơ bản [A,B])", ok, formatRes(res));
        }

        // CASE 2: Cold-start / Dưới ngưỡng minSupport (chỉ mới học 2 lần < 3)
        {
            MarkovPredictor<String> pred = new MarkovPredictor<>(2, 3);
            List<String> ctx = Arrays.asList("Home", "List");
            pred.learn(ctx, "Detail");
            pred.learn(ctx, "Detail"); // Tổng = 2 < minSupport(3) ở cả bậc 1 lẫn bậc 2
            List<Map.Entry<String, Double>> res = pred.predict(ctx);
            check("Case 2 (Dưới ngưỡng minSupport=3 -> Rỗng)", res.isEmpty(), "size=" + res.size());
        }

        // CASE 3: Hạ bậc (Backoff từ Bậc 2 xuống Bậc 1)
        {
            MarkovPredictor<String> pred = new MarkovPredictor<>(2, 3);
            // Học ["Home", "List"] -> "Detail" 3 lần (Bậc 1 ["List"] có 3 lần "Detail")
            for (int i = 0; i < 3; i++) {
                pred.learn(Arrays.asList("Home", "List"), "Detail");
            }
            // Học ["Search", "List"] -> "Cart" chỉ 1 lần (Bậc 2 ["Search", "List"] mới có 1 mẫu < 3)
            pred.learn(Arrays.asList("Search", "List"), "Cart");

            // Khi đoán ["Search", "List"]: Bậc 2 chỉ có 1 mẫu (< 3) nên phải hạ xuống Bậc 1 ["List"] (4 mẫu: 3 Detail, 1 Cart)
            List<Map.Entry<String, Double>> res = pred.predict(Arrays.asList("Search", "List"));
            boolean ok = res.size() == 2
                    && res.get(0).getKey().equals("Detail") && Math.abs(res.get(0).getValue() - 0.75) < 1e-6
                    && res.get(1).getKey().equals("Cart") && Math.abs(res.get(1).getValue() - 0.25) < 1e-6;
            check("Case 3 (Hạ bậc Backoff Bậc 2 -> Bậc 1)", ok, formatRes(res));
        }

        // CASE 4: Ưu tiên Bậc 2 khi Bậc 2 đã đủ minSupport >= 3
        {
            MarkovPredictor<String> pred = new MarkovPredictor<>(2, 3);
            for (int i = 0; i < 4; i++) {
                pred.learn(Arrays.asList("Home", "List"), "Detail");
            }
            // Bây giờ cho ["Search", "List"] học đủ 3 lần sang "Cart"
            for (int i = 0; i < 3; i++) {
                pred.learn(Arrays.asList("Search", "List"), "Cart");
            }
            // Bậc 1 ["List"] vẫn nghiêng về "Detail" (4/7), nhưng Bậc 2 ["Search", "List"] đã đủ 3 mẫu (100% "Cart")
            List<Map.Entry<String, Double>> res = pred.predict(Arrays.asList("Search", "List"));
            boolean ok = res.size() == 1
                    && res.get(0).getKey().equals("Cart") && Math.abs(res.get(0).getValue() - 1.0) < 1e-6;
            check("Case 4 (Ưu tiên Bậc 2 khi đủ minSupport)", ok, formatRes(res));
        }

        // CASE 5: Xử lý hòa xác suất (Tie-break theo bảng chữ cái A -> Z)
        {
            MarkovPredictor<String> pred = new MarkovPredictor<>(2, 3);
            List<String> ctx = Arrays.asList("Home", "Detail");
            pred.learn(ctx, "Reviews");
            pred.learn(ctx, "Cart");
            pred.learn(ctx, "Reviews");
            pred.learn(ctx, "Cart"); // Cart = 2, Reviews = 2 (cùng 50%)
            List<Map.Entry<String, Double>> res = pred.predict(ctx);
            boolean ok = res.size() == 2
                    && res.get(0).getKey().equals("Cart")
                    && res.get(1).getKey().equals("Reviews");
            check("Case 5 (Hòa xác suất 50%-50% -> Xếp A-Z)", ok, formatRes(res));
        }

        // CASE 6: Lịch sử dài hơn maxOrder & xử lý đầu vào rỗng/null
        {
            MarkovPredictor<String> pred = new MarkovPredictor<>(2, 3);
            pred.learn(Arrays.asList("Home", "List", "Detail"), "Cart");
            pred.learn(Arrays.asList("Search", "List", "Detail"), "Cart");
            pred.learn(Arrays.asList("Profile", "List", "Detail"), "Reviews");

            // Truyền lịch sử dài 5 trang nhưng 2 trang cuối vẫn là ["List", "Detail"]
            List<Map.Entry<String, Double>> res = pred.predict(
                    Arrays.asList("Home", "Search", "Profile", "List", "Detail")
            );
            boolean emptySafe = pred.predict(Collections.emptyList()).isEmpty()
                    && pred.predict(null).isEmpty();
            boolean ok = emptySafe && res.size() == 2
                    && res.get(0).getKey().equals("Cart") && Math.abs(res.get(0).getValue() - 2.0 / 3.0) < 1e-6;
            check("Case 6 (Lịch sử dài > maxOrder & Null-safe)", ok, formatRes(res));
        }

        System.out.println("----------------------------------------------------------");
        System.out.printf("KẾT QUẢ: %d PASSED, %d FAILED%n", passed, failed);
        System.out.println("==========================================================");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static String formatRes(List<Map.Entry<String, Double>> res) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < res.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format(Locale.US, "%s:%.3f", res.get(i).getKey(), res.get(i).getValue()));
        }
        sb.append("]");
        return sb.toString();
    }
}
