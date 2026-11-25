import csv
from pathlib import Path
import sys

POLICY_NAMES = {
    "P0": "P0 - Không tải trước (Đối chứng)",
    "P1": "P1 - Tải tất cả",
    "P2": "P2 - Top-1 phổ biến",
    "P3": "P3 - Markov bậc 1",
    "P4": "P4 - Markov bậc 2 (Hạ bậc)",
    "P5": "P5 - Thích ứng mạng",
}

POLICY_ORDER = {"P0": 0, "P1": 1, "P2": 2, "P3": 3, "P4": 4, "P5": 5}


def format_csv(raw_csv: Path, out_csv: Path):
    rows = []
    with raw_csv.open("r", encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        for r in reader:
            if not r.get("policy"):
                continue
            rows.append({
                "timestamp": r["timestamp"].strip(),
                "policy": r["policy"].strip(),
                "network": r["network"].strip(),
                "total_steps": int(r["total_steps"]),
                "full_hits": int(r["full_hits"]),
                "late_hits": int(r["late_hits"]),
                "misses": int(r["misses"]),
                "hit_rate_pct": float(r["hit_rate_pct"]),
                "avg_latency_ms": float(r["avg_latency_ms"]),
                "total_wasted_kb": float(r["total_wasted_kb"]),
            })

    rows.sort(key=lambda x: (x["network"], POLICY_ORDER.get(x["policy"], 99)))
    p0_latency = {r["network"]: r["avg_latency_ms"] for r in rows if r["policy"] == "P0"}

    # Xuất 1 file CSV duy nhất chuẩn UTF-8 BOM để mở trực tiếp trên Excel không lỗi dấu tiếng Việt
    with out_csv.open("w", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f)
        w.writerow([
            "Mã chính sách",
            "Tên chính sách",
            "Hồ sơ mạng",
            "Tổng số bước",
            "Full Hit (0 ms)",
            "Late Hit",
            "Miss",
            "Tỷ lệ Trúng (%)",
            "Độ trễ TB (ms)",
            "Giảm trễ so với P0 (%)",
            "Tổng lãng phí (KB)",
            "Lãng phí TB (KB/bước)",
            "Thời điểm đo",
        ])
        for r in rows:
            base = p0_latency.get(r["network"])
            if r["policy"] == "P0":
                red_str = "0.0% (Mốc)"
            elif base and base > 0:
                red = (base - r["avg_latency_ms"]) / base * 100.0
                red_str = f"-{red:.1f}%" if red >= 0 else f"+{abs(red):.1f}%"
            else:
                red_str = "-"

            avg_waste = r["total_wasted_kb"] / r["total_steps"] if r["total_steps"] > 0 else 0.0
            w.writerow([
                r["policy"],
                POLICY_NAMES.get(r["policy"], r["policy"]),
                r["network"],
                r["total_steps"],
                r["full_hits"],
                r["late_hits"],
                r["misses"],
                f"{r['hit_rate_pct']:.1f}",
                int(round(r["avg_latency_ms"])),
                red_str,
                int(round(r["total_wasted_kb"])),
                f"{avg_waste:.1f}",
                r["timestamp"],
            ])

    print(f"[+] Da xuat file CSV chuan Excel -> {out_csv}")


if __name__ == "__main__":
    raw = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("prefetch_comparison.csv")
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else raw.parent / "ket_qua_so_sanh.csv"
    if raw.exists():
        format_csv(raw, out)
    else:
        print(f"[!] Khong tim thay file: {raw}")
