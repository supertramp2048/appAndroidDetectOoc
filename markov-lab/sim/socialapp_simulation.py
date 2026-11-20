import csv
import json
from pathlib import Path
from offline_simulation import Markov

# Bảng dung lượng màn hình (KB) khớp 100% với ScreenId.java trong SocialApp Android
SOCIALAPP_PAGE_SIZES_KB = {
    "FEED_NEWEST": 120,
    "FEED_HOT": 120,
    "FEED_FOLLOWING": 100,
    "SEARCH": 60,
    "POST_DETAIL": 180,
    "COMMENTS": 90,
    "PROFILE": 110,
    "FACT_CHECK": 150,
    "SETTINGS": 40,
}

# 4 Profile mạng khớp với PrefetchEngine.java trong SocialApp Android (bandwidth_kbps = KB/s)
SOCIALAPP_NETWORKS = {
    "WIFI": {"rtt_ms": 20.0,  "bw_kbps": 30.0 * 1024},
    "LTE":  {"rtt_ms": 60.0,  "bw_kbps": 15.0 * 1024},
    "3G":   {"rtt_ms": 150.0, "bw_kbps": 2.0 * 1024},
    "EDGE": {"rtt_ms": 400.0, "bw_kbps": 200.0},
}


def calc_socialapp_delay_ms(screen, net_name):
    size_kb = SOCIALAPP_PAGE_SIZES_KB.get(screen, 100)
    net = SOCIALAPP_NETWORKS[net_name]
    return net["rtt_ms"] + (size_kb / net["bw_kbps"]) * 1000.0


def load_replay_json(json_path):
    data = json.loads(Path(json_path).read_text(encoding="utf-8"))
    sessions = []
    for s in data.get("sessions", []):
        sid = int(s["session_id"])
        seq = [(step["screen"], int(step["dwell_ms"])) for step in s.get("steps", [])]
        sessions.append((sid, seq))
    return sessions


def evaluate_on_socialapp(sessions, net_name, policy_name, max_order=2, top_m=1, threshold=0.25):
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

            if policy_name == "P0":
                chosen = []
            elif policy_name == "Oracle":
                chosen = [nxt_screen]
            else:
                chosen = [scr for scr, prob in candidates[:top_m] if prob >= threshold]

            rem_budget_ms = float(dwell_ms)
            saved_for_next_ms = 0.0
            for scr in chosen:
                fetch_ms = calc_socialapp_delay_ms(scr, net_name)
                size_kb = SOCIALAPP_PAGE_SIZES_KB.get(scr, 100)
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

            full_next_ms = calc_socialapp_delay_ms(nxt_screen, net_name)
            total_latency_ms += max(0.0, full_next_ms - saved_for_next_ms)
            total_steps += 1
            pred.learn(hist, nxt_screen)

    return {
        "network": net_name,
        "policy": policy_name,
        "order": max_order,
        "top_m": top_m,
        "threshold": threshold,
        "avg_latency_ms": round(total_latency_ms / total_steps, 2) if total_steps else 0.0,
        "avg_wasted_kb": round(wasted_kb / total_steps, 2) if total_steps else 0.0,
        "waste_ratio": round(wasted_kb / prefetched_kb, 4) if prefetched_kb > 0 else 0.0,
        "top1_acc": round(hits / total_steps, 4) if total_steps else 0.0,
    }


def main():
    sim_dir = Path(__file__).resolve().parent
    project_root = sim_dir.parent.parent
    replay_json = project_root / "app" / "src" / "main" / "assets" / "mock_replay_100_sessions.json"
    out_dir = sim_dir.parent / "out"
    out_dir.mkdir(parents=True, exist_ok=True)

    if not replay_json.exists():
        print(f"[!] Không tìm thấy {replay_json}")
        return

    sessions = load_replay_json(replay_json)

    # Xuất thêm file CSV tương ứng vào app/src/main/assets/sessions_sample.csv (50 phiên đầu)
    assets_csv = replay_json.parent / "sessions_sample.csv"
    with assets_csv.open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["session_id", "step", "screen", "dwell_ms"])
        for sid, seq in sessions[:50]:
            for step_idx, (scr, dwell) in enumerate(seq):
                w.writerow([sid, step_idx, scr, dwell])
    print(f"[+] Đã đồng bộ 50 phiên mẫu vào -> {assets_csv}")

    policies = [
        ("P0",            2, 0, 0.00),
        ("P3-Markov1",    1, 1, 0.30),
        ("P4-Markov2",    2, 1, 0.25),
        ("P4-Markov2-T2", 2, 2, 0.25),
        ("Oracle",        2, 1, 0.00),
    ]

    results = []
    for net in SOCIALAPP_NETWORKS:
        for p_name, order, top_m, th in policies:
            results.append(evaluate_on_socialapp(sessions, net, p_name, order, top_m, th))

    out_csv = out_dir / "socialapp_sim_results.csv"
    with out_csv.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(results[0].keys()))
        writer.writeheader()
        writer.writerows(results)

    print(f"[+] Đã xuất kết quả mô phỏng trên kịch bản SocialApp -> {out_csv}")
    for r in results:
        if r["network"] in ("3G", "EDGE"):
            print(
                f"  [{r['network']:4s}] {r['policy']:14s} | latency = {r['avg_latency_ms']:6.2f} ms | "
                f"wasted = {r['avg_wasted_kb']:5.2f} KB | top1_acc = {r['top1_acc']:.4f}"
            )


if __name__ == "__main__":
    main()
