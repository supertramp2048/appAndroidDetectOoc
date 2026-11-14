from collections import defaultdict
import csv
from pathlib import Path

# Bảng kích thước trang (KB) — giữ đồng bộ với PrefetchManager của C
PAGE_SIZES_KB = {
    "Home": 180,
    "List": 240,
    "Search": 160,
    "Detail": 400,   # Kiểm tra tính tay ở 3G (1.5 Mbps, RTT 150 ms): 150 + (400 * 8) / 1.5 = 2283.33 ms
    "Reviews": 150,
    "Cart": 120,
    "Checkout": 100,
    "Profile": 90,
}

# 4 Profile mạng chuẩn (bw_mbps, rtt_ms) — giữ đồng bộ với C
NETWORK_PROFILES = {
    "WiFi": {"bw_mbps": 24.0, "rtt_ms": 25.0},
    "4G":   {"bw_mbps": 8.0,  "rtt_ms": 60.0},
    "3G":   {"bw_mbps": 1.5,  "rtt_ms": 150.0},
    "Poor": {"bw_mbps": 0.4,  "rtt_ms": 300.0},
}


class Markov:
    """
    Bộ dự đoán Markov bậc k có hạ bậc (All-k-th-Order Markov Predictor).
    Đồng bộ 100% logic đoán, học và xử lý hòa (tie-break) với MarkovPredictor.java.
    """

    def __init__(self, max_order=2, min_support=3):
        if max_order < 1:
            raise ValueError("max_order must be >= 1")
        self.max_order = max_order
        self.min_support = max(1, min_support)
        self.tables = {k: defaultdict(lambda: defaultdict(int)) for k in range(1, max_order + 1)}

    def learn(self, history, nxt):
        if not history or nxt is None:
            return
        max_k = min(self.max_order, len(history))
        for k in range(1, max_k + 1):
            key = tuple(history[-k:])
            self.tables[k][key][nxt] += 1

    def predict(self, history):
        if not history:
            return []
        max_k = min(self.max_order, len(history))
        for k in range(max_k, 0, -1):
            key = tuple(history[-k:])
            next_counts = self.tables[k].get(key)
            if not next_counts:
                continue
            total = sum(next_counts.values())
            if total < self.min_support:
                continue
            # Sắp xếp giảm dần theo số lần xuất hiện, nếu bằng nhau thì theo thứ tự từ điển của tên màn hình
            ordered = sorted(next_counts.items(), key=lambda kv: (-kv[1], str(kv[0])))
            return [(screen, cnt / total) for screen, cnt in ordered]
        return []


def load_sessions(csv_path):
    """
    Đọc file CSV gồm 4 cột (session_id, step, screen, dwell_ms).
    Trả về danh sách [(session_id, [(screen, dwell_ms), ...]), ...] đã sắp xếp theo session_id và step.
    """
    by_session = defaultdict(list)
    with Path(csv_path).open("r", encoding="utf-8") as f:
        reader = csv.reader(f)
        next(reader, None)  # bỏ dòng tiêu đề
        for row in reader:
            if not row or len(row) < 4:
                continue
            sid = int(row[0])
            step = int(row[1])
            screen = row[2]
            dwell_ms = int(float(row[3]))
            by_session[sid].append((step, screen, dwell_ms))

    sessions = []
    for sid in sorted(by_session.keys()):
        seq = [(screen, dwell_ms) for _, screen, dwell_ms in sorted(by_session[sid], key=lambda x: x[0])]
        sessions.append((sid, seq))
    return sessions


def calc_fetch_time_ms(screen, net_name):
    """Tính thời gian tải trang (ms): RTT + (Size_KB * 8) / BW_Mbps."""
    size_kb = PAGE_SIZES_KB.get(screen, 200)
    net = NETWORK_PROFILES[net_name]
    return net["rtt_ms"] + (size_kb * 8.0) / net["bw_mbps"]


def evaluate_policy(sessions, net_name, policy_name, max_order=2, top_m=1, threshold=0.0):
    """
    Đánh giá một chính sách prefetch trên tập phiên và profile mạng:
    - P0: Không prefetch (latency = full fetch time, waste = 0)
    - Markov: Dự đoán top-m màn hình có xác suất >= threshold để prefetch trong thời gian dwell_ms
    - Oracle: Biết trước chính xác màn hình kế tiếp để prefetch trong thời gian dwell_ms
    """
    pred = Markov(max_order=max_order, min_support=3)
    total_latency_ms = 0.0
    total_steps = 0
    prefetched_kb = 0.0
    wasted_kb = 0.0
    hits = 0

    for _, seq in sessions:
        hist = []
        for i in range(len(seq) - 1):
            cur_screen, dwell_ms = seq[i]
            nxt_screen, _ = seq[i + 1]
            hist.append(cur_screen)

            candidates = pred.predict(hist)
            if candidates and candidates[0][0] == nxt_screen:
                hits += 1

            # Chọn danh sách màn hình sẽ prefetch
            if policy_name == "P0":
                chosen = []
            elif policy_name == "Oracle":
                chosen = [nxt_screen]
            else:
                chosen = [scr for scr, prob in candidates[:top_m] if prob >= threshold]

            # Mô phỏng tải trước trong khoảng thời gian người dùng dừng ở màn hình hiện tại (dwell_ms)
            rem_budget_ms = float(dwell_ms)
            saved_for_next_ms = 0.0

            for scr in chosen:
                fetch_ms = calc_fetch_time_ms(scr, net_name)
                size_kb = PAGE_SIZES_KB.get(scr, 200)
                if rem_budget_ms <= 0:
                    break
                frac = min(1.0, rem_budget_ms / fetch_ms)
                downloaded_kb = size_kb * frac
                prefetched_kb += downloaded_kb
                rem_budget_ms -= fetch_ms * frac

                if scr == nxt_screen:
                    saved_for_next_ms = fetch_ms * frac
                else:
                    wasted_kb += downloaded_kb

            # Độ trễ người dùng cảm nhận khi chuyển sang nxt_screen
            full_next_ms = calc_fetch_time_ms(nxt_screen, net_name)
            user_latency_ms = max(0.0, full_next_ms - saved_for_next_ms)
            total_latency_ms += user_latency_ms
            total_steps += 1

            pred.learn(hist, nxt_screen)

    avg_latency_ms = total_latency_ms / total_steps if total_steps else 0.0
    waste_ratio = (wasted_kb / prefetched_kb) if prefetched_kb > 0 else 0.0
    avg_wasted_kb = wasted_kb / total_steps if total_steps else 0.0
    acc = hits / total_steps if total_steps else 0.0
    return {
        "network": net_name,
        "policy": policy_name,
        "order": max_order,
        "top_m": top_m,
        "threshold": threshold,
        "avg_latency_ms": round(avg_latency_ms, 2),
        "avg_wasted_kb": round(avg_wasted_kb, 2),
        "waste_ratio": round(waste_ratio, 4),
        "top1_acc": round(acc, 4),
    }


def compute_learning_curve(sessions, max_order=2, bucket_size=50):
    pred = Markov(max_order=max_order, min_support=3)
    curve = []
    hits_bucket, n_bucket = 0, 0
    cum_hits, cum_n = 0, 0

    for idx, (_, seq) in enumerate(sessions, start=1):
        hist = []
        for i in range(len(seq) - 1):
            hist.append(seq[i][0])
            nxt = seq[i + 1][0]
            p = pred.predict(hist)
            hit = int(bool(p) and p[0][0] == nxt)
            hits_bucket += hit
            n_bucket += 1
            cum_hits += hit
            cum_n += 1
            pred.learn(hist, nxt)

        if idx % bucket_size == 0 or idx == len(sessions):
            curve.append(
                {
                    "sessions_seen": idx,
                    "window_acc": hits_bucket / n_bucket if n_bucket else 0.0,
                    "cumulative_acc": cum_hits / cum_n if cum_n else 0.0,
                }
            )
            hits_bucket, n_bucket = 0, 0
    return curve


def main():
    base_dir = Path(__file__).resolve().parent
    csv_path = base_dir / "sessions.csv"
    out_dir = base_dir.parent / "out"
    out_dir.mkdir(parents=True, exist_ok=True)

    if not csv_path.exists():
        print(f"[!] Không tìm thấy {csv_path}. Hãy chạy generate_logs.py trước.")
        return

    sessions = load_sessions(csv_path)

    # 1. Kiểm tra các điểm tính tay (Hand-calculated sanity checks)
    detail_3g_ms = calc_fetch_time_ms("Detail", "3G")
    print("=== KIỂM TRA ĐIỂM TÍNH TAY ===")
    print(f"- Độ trễ P0 ở mạng 3G cho màn hình Detail: {detail_3g_ms:.2f} ms (kỳ vọng ~2283 ms)")

    policies = [
        ("P0",       2, 0, 0.0),
        ("Markov-1", 1, 1, 0.30),
        ("Markov-2", 2, 1, 0.30),
        ("Markov-2-Top2", 2, 2, 0.25),
        ("Oracle",   2, 1, 0.0),
    ]

    results = []
    for net_name in NETWORK_PROFILES:
        for p_name, order, top_m, th in policies:
            res = evaluate_policy(sessions, net_name, p_name, max_order=order, top_m=top_m, threshold=th)
            results.append(res)

    print("\n=== KẾT QUẢ MÔ PHỎNG TRÊN MẠNG 3G ===")
    for r in results:
        if r["network"] == "3G":
            print(
                f"  {r['policy']:14s} | avg_latency = {r['avg_latency_ms']:7.2f} ms | "
                f"wasted_kb/step = {r['avg_wasted_kb']:6.2f} KB | waste_ratio = {r['waste_ratio']:.4f}"
            )

    # Xuất bảng kết quả ra out/sim_results.csv
    results_csv = out_dir / "sim_results.csv"
    with results_csv.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(results[0].keys()))
        writer.writeheader()
        writer.writerows(results)
    print(f"\n[+] Đã lưu bảng kết quả mô phỏng -> {results_csv}")

    # 2. Tính đường cong học (Learning Curve) & biên Pareto (Pareto Frontier)
    curve_o1 = compute_learning_curve(sessions, max_order=1, bucket_size=50)
    curve_o2 = compute_learning_curve(sessions, max_order=2, bucket_size=50)

    thresholds = [0.15, 0.20, 0.25, 0.30, 0.40, 0.50, 0.60, 0.70]
    pareto_points = [evaluate_policy(sessions, "3G", "P0", 2, 0, 0.0)]
    for th in thresholds:
        pareto_points.append(evaluate_policy(sessions, "3G", f"M2(th={th:.2f})", 2, 1, th))
    for th in [0.15, 0.25, 0.35]:
        pareto_points.append(evaluate_policy(sessions, "3G", f"M2-Top2(th={th:.2f})", 2, 2, th))
    pareto_points.append(evaluate_policy(sessions, "3G", "Oracle", 2, 1, 0.0))

    # Vẽ biểu đồ nếu đã cài matplotlib trong .venv
    try:
        import matplotlib.pyplot as plt

        # Biểu đồ 1: Đường cong học (Learning Curve)
        plt.figure(figsize=(8, 4.8))
        plt.plot(
            [c["sessions_seen"] for c in curve_o1],
            [c["cumulative_acc"] for c in curve_o1],
            marker="o",
            label="Markov bậc 1 (k=1)",
        )
        plt.plot(
            [c["sessions_seen"] for c in curve_o2],
            [c["cumulative_acc"] for c in curve_o2],
            marker="s",
            label="Markov bậc 2 (k=2)",
        )
        plt.xlabel("Số phiên đã học (Sessions)")
        plt.ylabel("Độ chính xác Top-1 tích lũy")
        plt.title("Đường cong học của bộ dự đoán Markov (Learning Curve)")
        plt.grid(True, linestyle="--", alpha=0.5)
        plt.legend()
        plt.tight_layout()
        lc_path = out_dir / "learning_curve.png"
        plt.savefig(lc_path, dpi=150)
        plt.close()

        # Biểu đồ 2: Biên Pareto (Độ trễ vs. Dung lượng lãng phí trên mạng 3G)
        plt.figure(figsize=(8, 4.8))
        for pt in pareto_points:
            plt.scatter(pt["avg_wasted_kb"], pt["avg_latency_ms"], s=55)
            plt.annotate(
                pt["policy"],
                (pt["avg_wasted_kb"], pt["avg_latency_ms"]),
                textcoords="offset points",
                xytext=(5, 4),
                fontsize=8,
            )
        plt.xlabel("Băng thông lãng phí trung bình mỗi bước (KB)")
        plt.ylabel("Độ trễ trung bình người dùng chờ (ms)")
        plt.title("Biên Pareto trên mạng 3G: Độ trễ vs. Lãng phí Prefetch")
        plt.grid(True, linestyle="--", alpha=0.5)
        plt.tight_layout()
        pareto_path = out_dir / "pareto_3g.png"
        plt.savefig(pareto_path, dpi=150)
        plt.close()

        print(f"[+] Đã xuất biểu đồ đường cong học -> {lc_path}")
        print(f"[+] Đã xuất biểu đồ biên Pareto    -> {pareto_path}")
    except ImportError:
        print("[!] Chưa cài đặt matplotlib trong .venv nên bỏ qua bước xuất ảnh PNG.")


if __name__ == "__main__":
    main()
