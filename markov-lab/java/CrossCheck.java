import java.io.*;
import java.util.*;

public class CrossCheck {
    public static void main(String[] a) throws Exception {     // dùng: java CrossCheck ../sim/sessions.csv 2
        Map<Integer, List<String[]>> bySession = new TreeMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(a[0]))) {
            br.readLine();                                      // bỏ dòng tiêu đề
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split(",");
                bySession.computeIfAbsent(Integer.parseInt(p[0]), k -> new ArrayList<>()).add(p);
            }
        }
        MarkovPredictor<String> pred = new MarkovPredictor<>(Integer.parseInt(a[1]), 3);
        int hits = 0, n = 0;
        for (List<String[]> seq : bySession.values()) {
            seq.sort(Comparator.comparingInt(r -> Integer.parseInt(r[1])));
            List<String> hist = new ArrayList<>();
            for (int i = 0; i < seq.size() - 1; i++) {
                hist.add(seq.get(i)[2]);
                String nxt = seq.get(i + 1)[2];
                List<Map.Entry<String, Double>> p = pred.predict(hist);   // đoán trước
                if (!p.isEmpty() && p.get(0).getKey().equals(nxt)) hits++;
                n++;
                pred.learn(hist, nxt);                                    // học sau
            }
        }
        System.out.printf("Java  top1 = %.4f (%d/%d)%n", hits / (double) n, hits, n);
    }
}
