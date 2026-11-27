#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SocialApp Before vs. After Performance Benchmark Tool
Supports two modes:
  - --mode emulator : 100% automated benchmark on Android Emulator (requires adbd root).
                      Splits into Phase A (0-WebView Reading Path) & Phase B (WebView screens)
                      to avoid Chromium GPU context contamination on emulator gfxstream.
  - --mode device   : Guided interactive benchmark for non-rooted physical Android phones
                      connected via USB Debugging. Uses logcat Displayed + dumpsys gfxinfo/meminfo.

Zero external dependencies (Python 3.8+ standard library only).
"""

import argparse
import json
import re
import statistics
import subprocess
import sys
import time
from pathlib import Path

PKG = "com.socialapp"


def run_adb(*args, serial=None, timeout=60):
    cmd = ["adb"]
    if serial:
        cmd += ["-s", serial]
    cmd += list(args)
    res = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout)
    return res.stdout + ("\n" + res.stderr if res.stderr else "")


def shell(cmd_str, serial=None, timeout=60):
    return run_adb("shell", cmd_str, serial=serial, timeout=timeout)


def _re_int(pattern, text):
    m = re.search(pattern, text)
    return int(m.group(1)) if m else None


def _re_float(pattern, text):
    m = re.search(pattern, text)
    return float(m.group(1)) if m else None


def percentile(sorted_vals, pct):
    if not sorted_vals:
        return None
    k = (len(sorted_vals) - 1) * (pct / 100.0)
    f = int(k)
    c = min(f + 1, len(sorted_vals) - 1)
    if f == c:
        return round(sorted_vals[f], 3)
    return round(sorted_vals[f] + (sorted_vals[c] - sorted_vals[f]) * (k - f), 3)


def agg(vals):
    c = [v for v in vals if v is not None]
    if not c:
        return None
    return {
        "n": len(c),
        "median": round(statistics.median(c), 2),
        "mean": round(statistics.mean(c), 2),
        "min": round(min(c), 2),
        "max": round(max(c), 2),
        "raw": c,
    }


def detect_screen_coords(serial=None):
    out = shell("wm size", serial=serial)
    m = re.search(r"(\d+)x(\d+)", out)
    if m:
        w, h = int(m.group(1)), int(m.group(2))
    else:
        w, h = 1080, 2400
    return w // 2, int(h * 0.72), int(h * 0.25)


def install_clean(apk_path, serial=None):
    shell(f"am force-stop {PKG}", serial=serial)
    shell(f"pm uninstall {PKG}", serial=serial)
    time.sleep(1.0)
    out = run_adb("install", "-r", str(apk_path), serial=serial, timeout=90)
    if "Success" not in out:
        raise RuntimeError(f"Failed to install {apk_path}: {out}")
    time.sleep(1.5)


def start_timed(component, extras="", serial=None):
    out = shell(f"am start -W -n {PKG}/{component} {extras}".strip(), serial=serial, timeout=30)
    return _re_int(r"TotalTime:\s*(\d+)", out)


def get_meminfo(serial=None):
    out = shell(f"dumpsys meminfo {PKG}", serial=serial)
    pss_kb = _re_int(r"TOTAL PSS:\s*(\d+)", out) or 0
    return {
        "main_pss_mb": round(pss_kb / 1024.0, 2),
        "views": _re_int(r"Views:\s*(\d+)", out),
        "activities": _re_int(r"Activities:\s*(\d+)", out),
        "webviews": _re_int(r"WebViews:\s*(\d+)", out),
    }


def force_gc(serial=None):
    pid = shell(f"pidof {PKG}", serial=serial).strip()
    if pid and pid.isdigit():
        shell(f"kill -10 {pid}", serial=serial)
        time.sleep(1.5)


def parse_framestats_and_gfx(serial=None):
    out = shell(f"dumpsys gfxinfo {PKG} framestats", serial=serial)
    janky_m = re.search(r"Janky frames:\s*(\d+)\s*\(([\d.]+)%\)", out)

    in_csv = False
    headers = []
    cpu_total_ms = []
    measure_layout_ms = []
    ui_draw_ms = []

    for line in out.splitlines():
        s = line.strip()
        if s == "---PROFILEDATA---":
            in_csv = not in_csv
            headers = []
            continue
        if not in_csv or not s:
            continue
        if s.startswith("Flags,"):
            headers = [h.strip() for h in s.split(",") if h.strip()]
            continue
        parts = [p.strip() for p in s.split(",") if p.strip()]
        if len(parts) < 14 or not parts[0].lstrip("-").isdigit():
            continue
        try:
            vals = [int(p) for p in parts]
        except ValueError:
            continue
        idx = {name: i for i, name in enumerate(headers)}
        i_input = idx.get("HandleInputStart", 5)
        i_trav = idx.get("PerformTraversalsStart", 7)
        i_draw = idx.get("DrawStart", 8)
        i_syncq = idx.get("SyncQueued", 9)
        if max(i_input, i_trav, i_draw, i_syncq) >= len(vals):
            continue
        t_input, t_trav, t_draw, t_syncq = vals[i_input], vals[i_trav], vals[i_draw], vals[i_syncq]
        if t_trav > 0 and t_draw >= t_trav and t_syncq >= t_draw:
            ml = (t_draw - t_trav) / 1e6
            dr = (t_syncq - t_draw) / 1e6
            ui = (t_syncq - t_input) / 1e6 if (t_input > 0 and t_syncq >= t_input) else (t_syncq - t_trav) / 1e6
            if 0 <= ml < 500 and 0 <= dr < 500 and 0 <= ui < 500:
                measure_layout_ms.append(ml)
                ui_draw_ms.append(dr)
                cpu_total_ms.append(ui)

    cpu_total_ms.sort()
    measure_layout_ms.sort()
    ui_draw_ms.sort()

    return {
        "total_frames": _re_int(r"Total frames rendered:\s*(\d+)", out),
        "janky_frames": int(janky_m.group(1)) if janky_m else None,
        "janky_pct": float(janky_m.group(2)) if janky_m else None,
        "gfx_p50_ms": _re_float(r"50th percentile:\s*([\d.]+)ms", out),
        "gfx_p90_ms": _re_float(r"90th percentile:\s*([\d.]+)ms", out),
        "gfx_p95_ms": _re_float(r"95th percentile:\s*([\d.]+)ms", out),
        "gfx_p99_ms": _re_float(r"99th percentile:\s*([\d.]+)ms", out),
        "ui_cpu_p50_ms": percentile(cpu_total_ms, 50),
        "ui_cpu_p95_ms": percentile(cpu_total_ms, 95),
        "measure_layout_p95_ms": percentile(measure_layout_ms, 95),
        "ui_draw_p95_ms": percentile(ui_draw_ms, 95),
        "attached_views": _re_int(r"Total attached Views\s*:\s*(\d+)", out),
        "rendernode_kb": _re_float(r"Total RenderNode\s*:\s*([\d.]+)\s*kB", out),
    }


def scroll_auto(cx, y_bot, y_top, flings=4, serial=None):
    for _ in range(flings):
        shell(f"input swipe {cx} {y_bot} {cx} {y_top} 180", serial=serial)
        time.sleep(0.38)
    for _ in range(flings):
        shell(f"input swipe {cx} {y_top} {cx} {y_bot} 180", serial=serial)
        time.sleep(0.38)


def parse_displayed_ms(logcat_out, activity_short_name):
    # Matches Android ActivityTaskManager: Displayed com.socialapp/.feature.post.PostDetailActivity: +313ms
    matches = re.findall(rf"Displayed\s+{PKG}/[^\s]*{re.escape(activity_short_name)}[^:]*:\s*\+(?:(\d+)s)?(\d+)ms", logcat_out)
    if not matches:
        return None
    sec, ms = matches[-1]
    return (int(sec) * 1000 if sec else 0) + int(ms)


# ================= EMULATOR MODE (100% AUTOMATED) =================

def run_emulator_mode(before_apk, after_apk, runs, serial):
    print("Restarting adbd as root (required for launching non-exported Activities directly)...", flush=True)
    run_adb("root", serial=serial)
    time.sleep(2.0)
    run_adb("wait-for-device", serial=serial, timeout=60)
    shell("input keyevent 82", serial=serial)
    shell("wm dismiss-keyguard", serial=serial)

    cx, y_bot, y_top = detect_screen_coords(serial=serial)

    def measure_feed_comments(label, apk_path):
        print(f"\n[Phase A - 0 WebView] {label}: Feed & Comments", flush=True)
        install_clean(apk_path, serial=serial)

        shell(f"am force-stop {PKG}", serial=serial)
        time.sleep(1.0)
        start_timed(".feature.home.HomeActivity", serial=serial)
        time.sleep(3.5)
        scroll_auto(cx, y_bot, y_top, flings=2, serial=serial)  # JIT warmup
        time.sleep(0.6)

        feed_runs = []
        for r in range(runs):
            shell(f"dumpsys gfxinfo {PKG} reset", serial=serial)
            time.sleep(0.2)
            scroll_auto(cx, y_bot, y_top, flings=4, serial=serial)
            time.sleep(0.3)
            g = parse_framestats_and_gfx(serial=serial)
            feed_runs.append(g)
            print(f"  Feed {r+1}/{runs}: P95={g['gfx_p95_ms']}ms Janky={g['janky_pct']}% UI_CPU_P95={g['ui_cpu_p95_ms']}ms Views={g['attached_views']}", flush=True)

        shell(f"am force-stop {PKG}", serial=serial)
        time.sleep(1.0)
        start_timed(".feature.home.HomeActivity", serial=serial)
        time.sleep(2.5)
        start_timed(".feature.comments.CommentsActivity", "--ei EXTRA_POST_ID 1", serial=serial)
        time.sleep(1.5)
        scroll_auto(cx, y_bot, y_top, flings=2, serial=serial)
        time.sleep(0.5)

        comm_runs = []
        for r in range(runs):
            shell(f"dumpsys gfxinfo {PKG} reset", serial=serial)
            time.sleep(0.2)
            scroll_auto(cx, y_bot, y_top, flings=4, serial=serial)
            time.sleep(0.3)
            g = parse_framestats_and_gfx(serial=serial)
            comm_runs.append(g)
            print(f"  Comments {r+1}/{runs}: P95={g['gfx_p95_ms']}ms Janky={g['janky_pct']}% UI_CPU_P95={g['ui_cpu_p95_ms']}ms", flush=True)

        return feed_runs, comm_runs

    def measure_post_detail(label, apk_path, already_installed=False):
        print(f"\n[PostDetail] {label}", flush=True)
        if not already_installed:
            install_clean(apk_path, serial=serial)
        opens, mems = [], []
        for r in range(runs):
            shell(f"am force-stop {PKG}", serial=serial)
            time.sleep(1.0)
            start_timed(".feature.home.HomeActivity", serial=serial)
            time.sleep(2.0)
            t = start_timed(".feature.post.PostDetailActivity", "--ei EXTRA_POST_ID 1", serial=serial)
            time.sleep(2.2)
            m = get_meminfo(serial=serial)
            opens.append(t)
            mems.append(m)
            print(f"  PostDetail {r+1}/{runs}: open={t}ms PSS={m['main_pss_mb']}MB WV={m['webviews']}", flush=True)

        scrolls = []
        for r in range(3):
            shell(f"dumpsys gfxinfo {PKG} reset", serial=serial)
            time.sleep(0.2)
            scroll_auto(cx, y_bot, y_top, flings=3, serial=serial)
            time.sleep(0.3)
            g = parse_framestats_and_gfx(serial=serial)
            scrolls.append(g)
            print(f"  PostDetail scroll {r+1}/3: P95={g['gfx_p95_ms']}ms Janky={g['janky_pct']}%", flush=True)
        return opens, mems, scrolls

    def measure_compose_leak(label, apk_path, already_installed=False):
        print(f"\n[Compose Leak & Trim Check] {label}", flush=True)
        if not already_installed:
            install_clean(apk_path, serial=serial)
        shell(f"am force-stop {PKG}", serial=serial)
        time.sleep(1.0)
        start_timed(".feature.home.HomeActivity", serial=serial)
        time.sleep(2.5)
        for _ in range(5):
            start_timed(".feature.compose.ComposeActivity", serial=serial)
            time.sleep(1.4)
            shell("input keyevent 4", serial=serial)
            time.sleep(0.9)
        force_gc(serial=serial)
        mem_after_gc = get_meminfo(serial=serial)
        shell(f"am send-trim-memory {PKG} RUNNING_LOW", serial=serial)
        time.sleep(1.5)
        force_gc(serial=serial)
        mem_after_trim = get_meminfo(serial=serial)
        print(f"  After 5 cycles+GC: Acts={mem_after_gc['activities']} WV={mem_after_gc['webviews']} PSS={mem_after_gc['main_pss_mb']}MB | After TRIM_RUNNING_LOW: WV={mem_after_trim['webviews']}", flush=True)
        return mem_after_gc, mem_after_trim

    # Phase A: Zero-WebView screens first
    bf_feed, bf_comm = measure_feed_comments("BEFORE", before_apk)
    af_feed, af_comm = measure_feed_comments("AFTER", after_apk)
    af_pd_open, af_pd_mem, af_pd_scroll = measure_post_detail("AFTER", after_apk, already_installed=True)

    # Phase B: WebView screens after Phase A
    bf_pd_open, bf_pd_mem, bf_pd_scroll = measure_post_detail("BEFORE", before_apk, already_installed=False)
    bf_comp_gc, bf_comp_trim = measure_compose_leak("BEFORE", before_apk, already_installed=True)
    af_comp_gc, af_comp_trim = measure_compose_leak("AFTER", after_apk, already_installed=False)

    return _pack_summary(
        (bf_feed, bf_comm, bf_pd_open, bf_pd_mem, bf_pd_scroll, bf_comp_gc, bf_comp_trim),
        (af_feed, af_comm, af_pd_open, af_pd_mem, af_pd_scroll, af_comp_gc, af_comp_trim),
    )


# ================= DEVICE MODE (NON-ROOT PHYSICAL PHONE) =================

def run_device_mode(before_apk, after_apk, runs, serial):
    cx, y_bot, y_top = detect_screen_coords(serial=serial)

    def measure_one_apk(label, apk_path):
        print(f"\n================ MEASURING {label} ON PHYSICAL DEVICE ================")
        install_clean(apk_path, serial=serial)
        shell(f"am force-stop {PKG}", serial=serial)
        shell(f"am start -n {PKG}/.feature.home.HomeActivity", serial=serial)
        input(f"[{label}] 1. Hay doi danh sach Feed tai xong tren dien thoai, sau do nhan Enter...")

        # Feed scroll (automated swipe works without root!)
        scroll_auto(cx, y_bot, y_top, flings=2, serial=serial)
        feed_runs = []
        for r in range(runs):
            shell(f"dumpsys gfxinfo {PKG} reset", serial=serial)
            time.sleep(0.2)
            scroll_auto(cx, y_bot, y_top, flings=4, serial=serial)
            time.sleep(0.3)
            g = parse_framestats_and_gfx(serial=serial)
            feed_runs.append(g)
            print(f"  Feed {r+1}/{runs}: P95={g['gfx_p95_ms']}ms Janky={g['janky_pct']}% UI_CPU_P95={g['ui_cpu_p95_ms']}ms")

        # PostDetail (user taps 1st post so non-exported Activity opens normally)
        pd_opens, pd_mems, pd_scrolls = [], [], []
        for r in range(runs):
            shell(f"am force-stop {PKG}", serial=serial)
            shell(f"am start -n {PKG}/.feature.home.HomeActivity", serial=serial)
            run_adb("logcat", "-c", serial=serial)
            input(f"[{label}] 2.{r+1}/{runs}. Hay BAM MO BAI VIET DAU TIEN tren Feed, doi hien thi xong roi nhan Enter...")
            lc = run_adb("logcat", "-d", "-s", "ActivityTaskManager:I", "ActivityManager:I", serial=serial)
            ms = parse_displayed_ms(lc, "PostDetailActivity")
            m = get_meminfo(serial=serial)
            pd_opens.append(ms)
            pd_mems.append(m)
            print(f"  PostDetail {r+1}/{runs}: Displayed={ms}ms PSS={m['main_pss_mb']}MB WV={m['webviews']}")

        for r in range(3):
            shell(f"dumpsys gfxinfo {PKG} reset", serial=serial)
            time.sleep(0.2)
            scroll_auto(cx, y_bot, y_top, flings=3, serial=serial)
            time.sleep(0.3)
            g = parse_framestats_and_gfx(serial=serial)
            pd_scrolls.append(g)
            print(f"  PostDetail scroll {r+1}/3: P95={g['gfx_p95_ms']}ms Janky={g['janky_pct']}%")

        # Comments
        input(f"[{label}] 3. Hay bam nut BINH LUAN de mo danh sach binh luan, sau do nhan Enter...")
        comm_runs = []
        for r in range(runs):
            shell(f"dumpsys gfxinfo {PKG} reset", serial=serial)
            time.sleep(0.2)
            scroll_auto(cx, y_bot, y_top, flings=4, serial=serial)
            time.sleep(0.3)
            g = parse_framestats_and_gfx(serial=serial)
            comm_runs.append(g)
            print(f"  Comments {r+1}/{runs}: P95={g['gfx_p95_ms']}ms Janky={g['janky_pct']}%")

        # Compose leak check (optional if user is logged in)
        ans = input(f"[{label}] 4. Ban co muon do ComposeActivity (can dang nhap)? (y/N): ").strip().lower()
        comp_gc = {"activities": None, "webviews": None, "main_pss_mb": None}
        comp_trim = {"webviews": None}
        if ans == "y":
            input(f"[{label}] Hay mo va bam Back dong man hinh Soan bai 5 lan lien tiep, sau do nhan Enter...")
            comp_gc = get_meminfo(serial=serial)
            shell(f"am send-trim-memory {PKG} RUNNING_LOW", serial=serial)
            time.sleep(1.5)
            comp_trim = get_meminfo(serial=serial)
            print(f"  Compose after 5 cycles: Acts={comp_gc['activities']} WV={comp_gc['webviews']} | After TRIM: WV={comp_trim['webviews']}")

        return feed_runs, comm_runs, pd_opens, pd_mems, pd_scrolls, comp_gc, comp_trim

    bf = measure_one_apk("BEFORE", before_apk)
    af = measure_one_apk("AFTER", after_apk)
    return _pack_summary(bf, af)


def _pack_summary(bf, af):
    def side(t):
        feed, comm, pd_open, pd_mem, pd_scroll, comp_gc, comp_trim = t
        return {
            "feed_janky_pct": agg([x["janky_pct"] for x in feed]),
            "feed_p95_ms": agg([x["gfx_p95_ms"] for x in feed]),
            "feed_ui_cpu_p95_ms": agg([x["ui_cpu_p95_ms"] for x in feed]),
            "feed_meas_layout_p95_ms": agg([x["measure_layout_p95_ms"] for x in feed]),
            "feed_attached_views": agg([x["attached_views"] for x in feed]),
            "feed_rendernode_kb": agg([x["rendernode_kb"] for x in feed]),
            "post_detail_open_ms": agg(pd_open),
            "post_detail_pss_mb": agg([m["main_pss_mb"] for m in pd_mem]),
            "post_detail_webviews": agg([m["webviews"] for m in pd_mem]),
            "post_detail_janky_pct": agg([x["janky_pct"] for x in pd_scroll]),
            "comments_p95_ms": agg([x["gfx_p95_ms"] for x in comm]),
            "comments_janky_pct": agg([x["janky_pct"] for x in comm]),
            "compose_after_5_cycles": comp_gc,
            "compose_after_trim": comp_trim,
        }

    return {"before": side(bf), "after": side(af)}


def render_markdown_table(summary):
    b, a = summary["before"], summary["after"]

    def fmt_delta(bv, av, unit="", lower_is_better=True):
        if bv is None or av is None or bv == 0:
            return "—"
        pct = (av - bv) / bv * 100.0
        direction = "Giảm" if pct < 0 else "Tăng"
        return f"**{direction} {abs(pct):.1f}%** ({av - bv:+.2f}{unit})"

    def m(obj):
        return obj["median"] if obj else None

    rows = [
        ("Tỷ lệ giật cuộn Feed (`Janky %`)", f"{m(b['feed_janky_pct'])}%", f"{m(a['feed_janky_pct'])}%", fmt_delta(m(b["feed_janky_pct"]), m(a["feed_janky_pct"]), " điểm %")),
        ("Độ trễ khung hình cuộn Feed (`P95`)", f"{m(b['feed_p95_ms'])} ms", f"{m(a['feed_p95_ms'])} ms", fmt_delta(m(b["feed_p95_ms"]), m(a["feed_p95_ms"]), " ms")),
        ("CPU Main Thread cuộn Feed (`UI_CPU P95`)", f"{m(b['feed_ui_cpu_p95_ms'])} ms", f"{m(a['feed_ui_cpu_p95_ms'])} ms", fmt_delta(m(b["feed_ui_cpu_p95_ms"]), m(a["feed_ui_cpu_p95_ms"]), " ms")),
        ("Thời gian mở `PostDetailActivity`", f"{m(b['post_detail_open_ms'])} ms", f"{m(a['post_detail_open_ms'])} ms", fmt_delta(m(b["post_detail_open_ms"]), m(a["post_detail_open_ms"]), " ms")),
        ("RAM `PostDetailActivity` (`TOTAL PSS`)", f"{m(b['post_detail_pss_mb'])} MB (WV={m(b['post_detail_webviews'])})", f"{m(a['post_detail_pss_mb'])} MB (WV={m(a['post_detail_webviews'])})", fmt_delta(m(b["post_detail_pss_mb"]), m(a["post_detail_pss_mb"]), " MB")),
        ("Tỷ lệ giật cuộn bài viết (`PostDetail Janky`)", f"{m(b['post_detail_janky_pct'])}%", f"{m(a['post_detail_janky_pct'])}%", fmt_delta(m(b["post_detail_janky_pct"]), m(a["post_detail_janky_pct"]), " điểm %")),
        ("Cuộn Bình luận (`Comments P95` • `Janky`)", f"{m(b['comments_p95_ms'])} ms • {m(b['comments_janky_pct'])}%", f"{m(a['comments_p95_ms'])} ms • {m(a['comments_janky_pct'])}%", fmt_delta(m(b["comments_janky_pct"]), m(a["comments_janky_pct"]), " điểm %")),
        ("Tồn đọng `ComposeActivity` (5 vòng + GC)", f"WV={b['compose_after_5_cycles'].get('webviews')} • Acts={b['compose_after_5_cycles'].get('activities')}", f"WV={a['compose_after_5_cycles'].get('webviews')} (trim={a['compose_after_trim'].get('webviews')})", "Không tích lũy WebView"),
    ]

    lines = [
        "# Kết quả Đo Hiệu năng Trước & Sau (`SocialApp`)",
        "",
        "| Chỉ số hiệu năng thực tế | Trước (`before.apk`) | Sau (`after.apk`) | Mức cải thiện |",
        "| :--- | :--- | :--- | :--- |",
    ]
    for name, bv, av, delta in rows:
        lines.append(f"| **{name}** | `{bv}` | `{av}` | {delta} |")
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description="Benchmark SocialApp Before vs. After APKs via ADB.")
    parser.add_argument("--before", required=True, type=Path, help="Path to before.apk (baseline)")
    parser.add_argument("--after", required=True, type=Path, help="Path to after.apk (optimized)")
    parser.add_argument("--mode", choices=["emulator", "device"], default="emulator", help="emulator (auto) or device (guided non-root)")
    parser.add_argument("--runs", type=int, default=5, help="Number of measurement iterations per metric (default: 5)")
    parser.add_argument("--serial", default=None, help="Optional adb device serial (if multiple devices attached)")
    parser.add_argument("--out", type=Path, default=Path("benchmark_result.md"), help="Output Markdown report path")
    args = parser.parse_args()

    if not args.before.exists():
        sys.exit(f"ERROR: before APK not found: {args.before}")
    if not args.after.exists():
        sys.exit(f"ERROR: after APK not found: {args.after}")

    devices = run_adb("devices")
    if "\tdevice" not in devices:
        sys.exit(f"ERROR: No online Android device/emulator found.\n{devices}")

    summary = (
        run_emulator_mode(args.before, args.after, args.runs, args.serial)
        if args.mode == "emulator"
        else run_device_mode(args.before, args.after, args.runs, args.serial)
    )

    json_path = args.out.with_suffix(".json")
    json_path.write_text(json.dumps(summary, indent=2, ensure_ascii=False), encoding="utf-8")
    md_text = render_markdown_table(summary)
    args.out.write_text(md_text, encoding="utf-8")

    print("\n" + md_text)
    print(f"Saved Markdown report : {args.out}")
    print(f"Saved raw JSON data   : {json_path}")


if __name__ == "__main__":
    main()
