package com.socialapp.core.prefetch.policy.markov;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bộ dự đoán chuỗi Markov bậc k có hạ bậc (All-k-th-Order Markov Predictor with Backoff).
 * Tác giả: Thành viên Kiên (Tasks B1 - B5).
 * Không phụ thuộc thư viện Android để dùng chung giữa môi trường thử nghiệm và ứng dụng.
 *
 * @param <T> Kiểu định danh trạng thái (ví dụ: String cho tên màn hình).
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

    public int getMaxOrder() {
        return maxOrder;
    }

    public int getMinSupport() {
        return minSupport;
    }

    /** Returns the raw count for the exact context, without backoff or minSupport filtering. */
    public synchronized int getTransitionCount(List<T> context, T next) {
        Map<List<T>, Map<T, Integer>> table = tables.get(context.size());
        Map<T, Integer> nextCounts = table == null ? null : table.get(context);
        return nextCounts == null ? 0 : nextCounts.getOrDefault(next, 0);
    }

    /** Copies every order, including the lower-order tables used for backoff. */
    public synchronized Map<Integer, Map<List<T>, Map<T, Integer>>> snapshotCounts() {
        Map<Integer, Map<List<T>, Map<T, Integer>>> snapshot = new HashMap<>();
        for (Map.Entry<Integer, Map<List<T>, Map<T, Integer>>> order : tables.entrySet()) {
            Map<List<T>, Map<T, Integer>> rows = new HashMap<>();
            for (Map.Entry<List<T>, Map<T, Integer>> row : order.getValue().entrySet()) {
                rows.put(Collections.unmodifiableList(new ArrayList<>(row.getKey())),
                        Collections.unmodifiableMap(new HashMap<>(row.getValue())));
            }
            snapshot.put(order.getKey(), Collections.unmodifiableMap(rows));
        }
        return Collections.unmodifiableMap(snapshot);
    }

    /** Validates and replaces all counts before publishing any of the new rows. */
    public synchronized void replaceCounts(Map<Integer, Map<List<T>, Map<T, Integer>>> counts) {
        if (!tables.keySet().equals(counts.keySet())) throw new IllegalArgumentException("Unexpected Markov orders");
        Map<Integer, Map<List<T>, Map<T, Integer>>> restored = new HashMap<>();
        for (int order = 1; order <= maxOrder; order++) {
            Map<List<T>, Map<T, Integer>> rows = new HashMap<>();
            Map<List<T>, Map<T, Integer>> input = counts.get(order);
            if (input == null) throw new IllegalArgumentException("Missing Markov table");
            for (Map.Entry<List<T>, Map<T, Integer>> row : input.entrySet()) {
                List<T> context = row.getKey();
                Map<T, Integer> next = row.getValue();
                if (context == null || context.size() != order || context.stream().anyMatch(Objects::isNull)
                        || next == null || next.isEmpty() || next.keySet().stream().anyMatch(Objects::isNull)) {
                    throw new IllegalArgumentException("Invalid Markov row");
                }
                long total = 0;
                for (Integer count : next.values()) {
                    if (count == null || count <= 0) throw new IllegalArgumentException("Invalid Markov count");
                    total += count;
                }
                if (total > Integer.MAX_VALUE) throw new IllegalArgumentException("Markov row total exceeds integer range");
                rows.put(new ArrayList<>(context), new HashMap<>(next));
            }
            restored.put(order, rows);
        }
        tables.clear();
        tables.putAll(restored);
    }

    /**
     * Cập nhật bảng tần suất chuyển trạng thái cho mọi bậc từ 1 đến min(maxOrder, history.size()).
     */
    public synchronized void learn(List<T> history, T next) {
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
     * Tie-breaking: Khi 2 màn hình cùng tần suất, sắp xếp theo thứ tự từ điển A-Z.
     */
    public synchronized List<Map.Entry<T, Double>> predict(List<T> history) {
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
     * Xóa sạch dữ liệu đã học (dùng khi bắt đầu phiên mới).
     */
    public synchronized void reset() {
        for (Map<List<T>, Map<T, Integer>> table : tables.values()) {
            table.clear();
        }
    }
}

