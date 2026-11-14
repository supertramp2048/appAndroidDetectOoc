import java.util.*;

/**
 * Bộ dự đoán chuỗi Markov bậc k có hạ bậc (All-k-th-Order Markov Predictor with Backoff).
 * Không phụ thuộc thư viện Android để dùng chung giữa môi trường thử nghiệm và ứng dụng.
 */
public class MarkovPredictor<T> {
    private final int maxOrder;
    private final int minSupport;
    private final Map<Integer, Map<List<T>, Map<T, Integer>>> tables;

    public MarkovPredictor(int maxOrder) {
        this(maxOrder, 3);
    }

    public MarkovPredictor(int maxOrder, int minSupport) {
        if (maxOrder < 1) {
            throw new IllegalArgumentException("maxOrder must be >= 1");
        }
        this.maxOrder = maxOrder;
        this.minSupport = Math.max(1, minSupport);
        this.tables = new HashMap<>();
        for (int k = 1; k <= maxOrder; k++) {
            this.tables.put(k, new HashMap<>());
        }
    }

    /**
     * Cập nhật bảng tần suất chuyển trạng thái cho mọi bậc từ 1 đến min(maxOrder, history.size()).
     */
    public void learn(List<T> history, T next) {
        if (history == null || history.isEmpty() || next == null) {
            return;
        }
        int maxK = Math.min(maxOrder, history.size());
        for (int k = 1; k <= maxK; k++) {
            List<T> key = new ArrayList<>(history.subList(history.size() - k, history.size()));
            Map<T, Integer> nextCounts = tables.get(k).computeIfAbsent(key, ignored -> new HashMap<>());
            nextCounts.put(next, nextCounts.getOrDefault(next, 0) + 1);
        }
    }

    /**
     * Dự đoán phân phối xác suất trạng thái kế tiếp.
     * Ưu tiên bậc cao nhất có tổng số mẫu quan sát >= minSupport, nếu không đủ thì hạ bậc (backoff).
     */
    public List<Map.Entry<T, Double>> predict(List<T> history) {
        if (history == null || history.isEmpty()) {
            return Collections.emptyList();
        }
        int maxK = Math.min(maxOrder, history.size());
        for (int k = maxK; k >= 1; k--) {
            List<T> key = history.subList(history.size() - k, history.size());
            Map<T, Integer> nextCounts = tables.get(k).get(key);
            if (nextCounts == null || nextCounts.isEmpty()) {
                continue;
            }
            int total = 0;
            for (int c : nextCounts.values()) {
                total += c;
            }
            if (total < minSupport) {
                continue;
            }

            List<Map.Entry<T, Integer>> countEntries = new ArrayList<>(nextCounts.entrySet());
            countEntries.sort((a, b) -> {
                int cmp = Integer.compare(b.getValue(), a.getValue());
                if (cmp != 0) {
                    return cmp;
                }
                return String.valueOf(a.getKey()).compareTo(String.valueOf(b.getKey()));
            });

            List<Map.Entry<T, Double>> result = new ArrayList<>(countEntries.size());
            for (Map.Entry<T, Integer> e : countEntries) {
                result.add(new AbstractMap.SimpleImmutableEntry<>(e.getKey(), e.getValue() / (double) total));
            }
            return result;
        }
        return Collections.emptyList();
    }

    /**
     * Ví dụ tính tay (Bước 2):
     * Sau ngữ cảnh [A, B], màn hình C xuất hiện 2 lần và D xuất hiện 1 lần (tổng support = 3 >= minSupport).
     * Kết quả kỳ vọng: C ≈ 0.667 và D ≈ 0.333.
     */
    public static void main(String[] args) {
        MarkovPredictor<String> predictor = new MarkovPredictor<>(2, 3);
        List<String> context = Arrays.asList("A", "B");

        predictor.learn(context, "C");
        predictor.learn(context, "C");
        predictor.learn(context, "D");

        List<Map.Entry<String, Double>> predictions = predictor.predict(context);
        System.out.println("Hand-calculated test for context [A, B]:");
        for (Map.Entry<String, Double> entry : predictions) {
            System.out.printf("%s -> %.3f%n", entry.getKey(), entry.getValue());
        }
    }
}
