import csv
import random
import sys
from pathlib import Path

# Danh sách màn hình và ma trận chuyển trạng thái bậc 2 / bậc 1 mô phỏng hành vi người dùng
SCREENS = ["Home", "List", "Search", "Detail", "Reviews", "Cart", "Checkout", "Profile"]

# Chuyển tiếp bậc 1 cơ sở: screen -> [(next_screen, weight), ...]
TRANSITIONS_O1 = {
    "Home": [("List", 0.45), ("Search", 0.25), ("Profile", 0.15), ("Cart", 0.15)],
    "List": [("Detail", 0.65), ("Home", 0.15), ("Search", 0.12), ("Cart", 0.08)],
    "Search": [("Detail", 0.60), ("List", 0.25), ("Home", 0.15)],
    "Detail": [("Cart", 0.40), ("Reviews", 0.30), ("List", 0.20), ("Home", 0.10)],
    "Reviews": [("Detail", 0.55), ("Cart", 0.30), ("List", 0.15)],
    "Cart": [("Checkout", 0.60), ("Detail", 0.20), ("List", 0.12), ("Home", 0.08)],
    "Checkout": [("Home", 0.70), ("Profile", 0.20), ("List", 0.10)],
    "Profile": [("Home", 0.50), ("List", 0.30), ("Cart", 0.20)],
}

# Chuyển tiếp bậc 2 đặc trưng (phụ thuộc 2 màn hình gần nhất) để mô hình Markov bậc 2 phát huy tác dụng
TRANSITIONS_O2 = {
    ("Home", "List"): [("Detail", 0.75), ("Search", 0.15), ("Home", 0.10)],
    ("Search", "Detail"): [("Cart", 0.55), ("Reviews", 0.25), ("Search", 0.20)],
    ("List", "Detail"): [("Reviews", 0.45), ("Cart", 0.35), ("List", 0.20)],
    ("Detail", "Reviews"): [("Cart", 0.65), ("Detail", 0.25), ("List", 0.10)],
    ("Reviews", "Detail"): [("Cart", 0.70), ("List", 0.20), ("Home", 0.10)],
    ("Detail", "Cart"): [("Checkout", 0.75), ("List", 0.15), ("Home", 0.10)],
}

# Chuyển tiếp khi có concept drift (nếu drift_session >= 0)
TRANSITIONS_DRIFT_O2 = {
    ("Home", "List"): [("Search", 0.60), ("Detail", 0.30), ("Home", 0.10)],
    ("List", "Detail"): [("Cart", 0.70), ("List", 0.20), ("Reviews", 0.10)],
    ("Detail", "Cart"): [("Home", 0.55), ("Checkout", 0.30), ("List", 0.15)],
}


def sample_next(dist, rng):
    targets, weights = zip(*dist)
    return rng.choices(targets, weights=weights, k=1)[0]


def generate_sessions(n_sessions=1000, drift_at=-1, seed=42):
    rng = random.Random(seed)
    rows = []

    for sid in range(n_sessions):
        n_steps = rng.randint(6, 14)
        hist = ["Home"]
        for step in range(n_steps):
            cur = hist[-1]
            # Thời gian dừng (dwell_ms) phân bố chuẩn xoay quanh 4000 ms (giới hạn tối thiểu 800 ms)
            dwell_ms = max(800, int(round(rng.gauss(4000, 900))))
            rows.append((sid, step, cur, dwell_ms))

            # Chọn màn hình tiếp theo
            ctx2 = (hist[-2], hist[-1]) if len(hist) >= 2 else None
            if 0 <= drift_at <= sid and ctx2 in TRANSITIONS_DRIFT_O2:
                nxt = sample_next(TRANSITIONS_DRIFT_O2[ctx2], rng)
            elif ctx2 in TRANSITIONS_O2:
                nxt = sample_next(TRANSITIONS_O2[ctx2], rng)
            else:
                nxt = sample_next(TRANSITIONS_O1[cur], rng)
            hist.append(nxt)

    return rows


def main():
    # Cú pháp: python generate_logs.py <num_sessions> <drift_at> <output_csv>
    n_sessions = int(sys.argv[1]) if len(sys.argv) > 1 else 1000
    drift_at = int(sys.argv[2]) if len(sys.argv) > 2 else -1
    out_path = Path(sys.argv[3]) if len(sys.argv) > 3 else Path("sessions.csv")

    rows = generate_sessions(n_sessions=n_sessions, drift_at=drift_at)

    out_path.parent.mkdir(parents=True, exist_ok=True)
    with out_path.open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["session_id", "step", "screen", "dwell_ms"])
        for row in rows:
            w.writerow(row)

    avg_dwell = sum(r[3] for r in rows) / len(rows) if rows else 0.0
    print(
        f"Generated {n_sessions} sessions ({len(rows)} rows) -> {out_path} "
        f"[avg dwell_ms = {avg_dwell:.1f}]"
    )


if __name__ == "__main__":
    main()
