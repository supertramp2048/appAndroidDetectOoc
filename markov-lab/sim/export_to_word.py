import re
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyArrowPatch, FancyBboxPatch

try:
    from docx import Document
    from docx.enum.table import WD_TABLE_ALIGNMENT
    from docx.enum.text import WD_ALIGN_PARAGRAPH
    from docx.oxml import OxmlElement
    from docx.oxml.ns import qn
    from docx.shared import Cm, Pt, RGBColor
except ImportError:
    print("[!] Chưa cài đặt thư viện python-docx trong .venv.")
    print("    Hãy chạy lệnh: pip install python-docx")
    raise SystemExit(1)

# Dùng font hỗ trợ đầy đủ tiếng Việt trên Windows
plt.rcParams["font.family"] = "sans-serif"
plt.rcParams["font.sans-serif"] = ["Arial", "DejaVu Sans", "Tahoma"]
plt.rcParams["axes.unicode_minus"] = False


def _box(ax, x, y, w, h, text, fc="#FFFFFF", ec="#000000", lw=1.8, fs=12, bold=True, tc="#000000", rounding=0.22, ls="-"):
    patch = FancyBboxPatch(
        (x, y), w, h,
        boxstyle=f"round,pad=0.06,rounding_size={rounding}",
        facecolor=fc, edgecolor=ec, linewidth=lw, linestyle=ls, zorder=3
    )
    ax.add_patch(patch)
    ax.text(
        x + w / 2, y + h / 2, text,
        ha="center", va="center", fontsize=fs,
        fontweight="bold" if bold else "normal",
        color=tc, linespacing=1.35, zorder=4
    )


def _arrow(ax, p1, p2, color="#000000", lw=1.8, style="-|>", rad=0.0):
    arr = FancyArrowPatch(
        p1, p2,
        arrowstyle=style, mutation_scale=18,
        color=color, linewidth=lw,
        connectionstyle=f"arc3,rad={rad}", zorder=2
    )
    ax.add_patch(arr)


def draw_fig_2_1(out_path: Path):
    """Hình 2.1: Sơ đồ thuật toán dự đoán Markov đa bậc có hạ bậc (Đen trắng chuẩn học thuật)."""
    fig, ax = plt.subplots(figsize=(11, 11.5))
    ax.set_xlim(0, 11)
    ax.set_ylim(0, 12.2)
    ax.axis("off")

    ax.text(
        5.5, 11.85, "THUẬT TOÁN DỰ ĐOÁN MARKOV ĐA BẬC CÓ HẠ BẬC (BACKOFF)",
        ha="center", va="center", fontsize=14, fontweight="bold", color="#000000"
    )

    # Trục chính (x trung tâm = 4.7)
    _box(ax, 1.9, 10.35, 5.6, 0.95, r"1. Đầu vào: Lịch sử màn hình H = ($\mathbf{S_1}$, ..., $\mathbf{S_t}$)",
         fc="#F2F2F2", ec="#000000", lw=2.0, fs=12.5)
    _box(ax, 1.9, 8.85, 5.6, 0.90, "2. Khởi tạo bậc tối đa:  k = min(maxOrder, |H|)",
         fc="#FFFFFF", ec="#000000", fs=12)

    # Khối điều kiện 1: k >= 1
    _box(ax, 3.1, 7.25, 3.2, 0.95, "k ≥ 1 ?", fc="#FFFFFF", ec="#000000", lw=2.0, fs=13.5, rounding=0.45)
    # Nhánh Sai (bên trái)
    _box(ax, 0.20, 7.15, 2.25, 1.15, "Trả về rỗng []\n(Không tải trước)",
         fc="#F2F2F2", ec="#000000", fs=11.5, ls="--")

    # Bước tra bảng bậc k
    _box(ax, 1.85, 5.45, 5.7, 1.10,
         r"3. Lấy k màn hình cuối làm ngữ cảnh $\mathbf{C_k}$" + "\n" + r"Tra tổng số lần gặp:  total = Σ count($\mathbf{C_k}$ → s)",
         fc="#FFFFFF", ec="#000000", fs=12)

    # Khối điều kiện 2: total >= minSupport (3)
    _box(ax, 2.5, 3.75, 4.4, 1.00, "total ≥ minSupport (3) ?",
         fc="#FFFFFF", ec="#000000", lw=2.0, fs=13, rounding=0.45)

    # Nhánh Backoff (bên phải)
    _box(ax, 8.25, 5.45, 2.50, 1.25, "Hạ bậc (Backoff)\nk = k − 1",
         fc="#F2F2F2", ec="#000000", lw=2.0, fs=12.5)

    # Bước tính xác suất & sắp xếp
    _box(ax, 1.65, 1.95, 6.1, 1.10,
         r"4. Tính xác suất:  P(s | $\mathbf{C_k}$) = count(s) / total" + "\n" + "Sắp xếp giảm dần (Nếu hòa: xếp theo tên A → Z)",
         fc="#FFFFFF", ec="#000000", fs=12)

    # Đầu ra
    _box(ax, 1.9, 0.45, 5.6, 0.95, "5. Đầu ra: Danh sách [(Màn hình, Xác suất)]",
         fc="#F2F2F2", ec="#000000", lw=2.0, fs=12.5)

    # Mũi tên luồng chính
    _arrow(ax, (4.7, 10.35), (4.7, 9.78))
    _arrow(ax, (4.7, 8.85), (4.7, 8.23))
    _arrow(ax, (4.7, 7.25), (4.7, 6.58))
    ax.text(4.9, 6.92, "Đúng", fontsize=11.5, fontweight="bold", color="#000000")

    _arrow(ax, (3.1, 7.72), (2.48, 7.72))
    ax.text(2.79, 7.95, "Sai", fontsize=11.5, fontweight="bold", color="#000000", ha="center")

    _arrow(ax, (4.7, 5.45), (4.7, 4.78))
    _arrow(ax, (4.7, 3.75), (4.7, 3.08))
    ax.text(4.9, 3.40, "Đủ (≥ 3)", fontsize=11.5, fontweight="bold", color="#000000")

    _arrow(ax, (4.7, 1.95), (4.7, 1.43))

    # Vòng lặp Hạ bậc (Backoff) bên phải
    ax.plot([6.9, 9.50], [4.25, 4.25], color="#000000", linewidth=1.8, zorder=2)
    _arrow(ax, (9.50, 4.25), (9.50, 5.42))
    ax.text(8.25, 3.92, "Chưa đủ (< 3)", fontsize=11.5, fontweight="bold", color="#000000", ha="center")

    ax.plot([9.50, 9.50], [6.70, 7.72], color="#000000", linewidth=1.8, zorder=2)
    _arrow(ax, (9.50, 7.72), (6.33, 7.72))

    fig.tight_layout(pad=0.3)
    fig.savefig(str(out_path), dpi=300, facecolor="white")
    plt.close(fig)


def draw_fig_3_1(out_path: Path):
    """Hình 3.1: Sơ đồ kiến trúc tổng thể 3 phân hệ của hệ thống (Đen trắng chuẩn học thuật)."""
    fig, ax = plt.subplots(figsize=(13.5, 7.8))
    ax.set_xlim(0, 13.5)
    ax.set_ylim(0, 8.0)
    ax.axis("off")

    ax.text(
        6.75, 7.65, "KIẾN TRÚC TỔNG THỂ 3 PHÂN HỆ CỦA HỆ THỐNG (A – B – C)",
        ha="center", va="center", fontsize=15, fontweight="bold", color="#000000"
    )

    # Khung nền 3 phân hệ: viền nét đứt màu đen, nền trắng sạch
    groups = [
        (0.25, 0.35, 3.85, 6.85, "PHÂN HỆ A\nGiao diện, Repository & Replay"),
        (4.80, 0.35, 4.00, 6.85, "PHÂN HỆ C\nGiả lập mạng, Cache & Chính sách"),
        (9.50, 0.35, 3.75, 6.85, "PHÂN HỆ B\nDự đoán Markov & Mô phỏng"),
    ]
    for gx, gy, gw, gh, title in groups:
        bg = FancyBboxPatch(
            (gx, gy), gw, gh,
            boxstyle="round,pad=0.05,rounding_size=0.22",
            facecolor="#FAFAFA", edgecolor="#000000", linewidth=1.8, linestyle="--", zorder=1
        )
        ax.add_patch(bg)
        ax.text(gx + gw / 2, gy + gh - 0.50, title, ha="center", va="center",
                fontsize=12.5, fontweight="bold", color="#000000", linespacing=1.25, zorder=2)

    # Các khối trong Phân hệ A
    _box(ax, 0.55, 5.15, 3.25, 0.95, "Robot tự động\nReplayEngine & BatchRunner", fc="#FFFFFF", fs=11)
    _box(ax, 0.55, 3.75, 3.25, 0.95, "Các màn hình SocialApp\n(9 ScreenId & Navigator)", fc="#FFFFFF", fs=11)
    _box(ax, 0.55, 2.25, 3.25, 1.00, "Cửa nạp dữ liệu trung tâm\nRepository.load(screen)", fc="#EAEAEA", lw=2.1, fs=11.5)
    _box(ax, 0.55, 0.70, 3.25, 1.00, "Giám sát & Ghi log CSV\nOverlay View & MetricsLogger", fc="#FFFFFF", fs=11)

    # Các khối trong Phân hệ C
    _box(ax, 5.10, 5.15, 3.40, 0.95, "Bộ nhớ đệm RAM Cache\nPrefetchCache & InFlightTask", fc="#EAEAEA", lw=2.0, fs=11)
    _box(ax, 5.10, 3.65, 3.40, 1.00, "Điều phối tải trước\nPrefetchEngine & Manager", fc="#EAEAEA", lw=2.1, fs=11.5)
    _box(ax, 5.10, 2.15, 3.40, 1.00, "6 Chiến lược (P0 – P5)\nNgưỡng θ & Pareto Utility", fc="#FFFFFF", fs=11)
    _box(ax, 5.10, 0.70, 3.40, 0.95, "Giả lập 4 hồ sơ mạng\nWiFi / LTE / 3G / EDGE", fc="#FFFFFF", fs=11)

    # Các khối trong Phân hệ B
    _box(ax, 9.80, 4.65, 3.15, 1.45, "Bộ đoán lõi\nMarkovPredictor<T>\n(Bậc 1, Bậc 2 + Backoff\nminSupport = 3)", fc="#EAEAEA", lw=2.1, fs=11.5)
    _box(ax, 9.80, 2.65, 3.15, 1.20, "Ma trận & Tensor\nMarkovTransitionMatrix\nMarkovTransitionTensor", fc="#FFFFFF", fs=11)
    _box(ax, 9.80, 0.70, 3.15, 1.25, "Mô phỏng & Kiểm tra chéo\nmarkov-lab (Python ↔ Java)\nLearning Curve & Pareto", fc="#FFFFFF", fs=10.8)

    # Mũi tên nội bộ Phân hệ A
    _arrow(ax, (2.17, 5.15), (2.17, 4.73))
    _arrow(ax, (2.17, 3.75), (2.17, 3.28))
    _arrow(ax, (2.17, 2.25), (2.17, 1.73))

    # Mũi tên nội bộ Phân hệ C
    _arrow(ax, (6.80, 4.65), (6.80, 5.12))
    _arrow(ax, (6.80, 3.15), (6.80, 3.62))
    _arrow(ax, (6.80, 1.65), (6.80, 2.12), style="<|-|>")

    # Mũi tên nội bộ Phân hệ B
    _arrow(ax, (11.37, 3.85), (11.37, 4.62), style="<|-|>")
    _arrow(ax, (11.37, 1.95), (11.37, 2.62), style="<|-|>")

    # Mũi tên liên phân hệ A <-> C
    _arrow(ax, (3.80, 2.95), (5.07, 5.45), lw=2.0)
    ax.text(4.35, 4.75, "1. Kiểm tra\nHit / Miss", fontsize=9.8, fontweight="bold", ha="center", color="#000000",
            bbox=dict(boxstyle="round,pad=0.15", fc="white", ec="none", alpha=0.95))

    _arrow(ax, (3.80, 2.65), (5.07, 4.05), lw=2.0)
    ax.text(4.42, 3.20, "2. Báo chuyển\nmàn hình", fontsize=9.8, fontweight="bold", ha="center", color="#000000",
            bbox=dict(boxstyle="round,pad=0.15", fc="white", ec="none", alpha=0.95))

    _arrow(ax, (5.10, 3.85), (3.83, 1.35), lw=1.7)
    ax.text(4.42, 1.95, "5. Cập nhật\nOverlay", fontsize=9.5, fontweight="bold", ha="center", color="#000000",
            bbox=dict(boxstyle="round,pad=0.15", fc="white", ec="none", alpha=0.95))

    # Mũi tên liên phân hệ C <-> B
    _arrow(ax, (8.50, 4.35), (9.77, 5.25), lw=2.0)
    ax.text(9.14, 5.25, "3. learn()\n& predict()", fontsize=9.8, fontweight="bold", ha="center", color="#000000",
            bbox=dict(boxstyle="round,pad=0.15", fc="white", ec="none", alpha=0.95))

    _arrow(ax, (9.77, 4.85), (8.50, 2.85), lw=2.0)
    ax.text(9.14, 3.65, "4. Xác suất\ndự đoán", fontsize=9.8, fontweight="bold", ha="center", color="#000000",
            bbox=dict(boxstyle="round,pad=0.15", fc="white", ec="none", alpha=0.95))

    fig.tight_layout(pad=0.3)
    fig.savefig(str(out_path), dpi=300, facecolor="white")
    plt.close(fig)


def draw_fig_3_2(out_path: Path):
    """Hình 3.2: Sơ đồ xử lý 3 trạng thái truy xuất (Đen trắng chuẩn học thuật)."""
    fig, ax = plt.subplots(figsize=(12.5, 8.6))
    ax.set_xlim(0, 12.5)
    ax.set_ylim(0, 8.8)
    ax.axis("off")

    ax.text(
        6.25, 8.45, "LUỒNG XỬ LÝ 3 TRẠNG THÁI TRUY XUẤT DỮ LIỆU TRONG REPOSITORY.LOAD()",
        ha="center", va="center", fontsize=14, fontweight="bold", color="#000000"
    )

    # Bước mở đầu trên cùng
    _box(ax, 2.10, 7.15, 8.3, 0.90,
         r"Người dùng / ReplayEngine mở màn hình $\mathbf{S_{t+1}}$  →  Gọi Repository.load($\mathbf{S_{t+1}}$)",
         fc="#F2F2F2", lw=2.0, fs=12.5)

    # Cột trái: 3 bước kiểm tra nối tiếp
    _box(ax, 0.55, 5.35, 4.25, 1.05, "Kiểm tra 1:\nCó sẵn trong RAM Cache\n(PrefetchCache)?",
         fc="#FFFFFF", lw=1.9, fs=11.8, rounding=0.35)

    _box(ax, 0.55, 3.60, 4.25, 1.05, "Kiểm tra 2:\nĐang tải ngầm dở dang\n(InFlightTask)?",
         fc="#FFFFFF", lw=1.9, fs=11.8, rounding=0.35)

    _box(ax, 0.55, 1.85, 4.25, 1.05, "Trường hợp 3:\nChưa được tải trước\n(Không có trong Cache / InFlight)",
         fc="#FFFFFF", lw=1.8, fs=11.5, rounding=0.22)

    # Cột phải: 3 thẻ kết quả to rõ (đen trắng, phân biệt bằng nét viền)
    _box(ax, 6.20, 5.35, 5.80, 1.05,
         "FULL_HIT (Trúng hoàn toàn)\nĐộ trễ chờ = 0 ms  |  Đánh dấu đã dùng (markUsed)",
         fc="#F2F2F2", lw=2.2, fs=12)

    _box(ax, 6.20, 3.60, 5.80, 1.05,
         r"LATE_HIT (Bắt nối luồng đang bay)" + "\n" + r"Chờ nốt phần còn lại ($\mathbf{T_{fetch}}$ − dwell)  |  Không tải lại từ đầu",
         fc="#FFFFFF", lw=2.0, fs=11.5)

    _box(ax, 6.20, 1.85, 5.80, 1.05,
         "MISS (Trượt tải trước)\nTải mới toàn phần qua mạng (RTT + Size / BW)",
         fc="#FFFFFF", lw=1.8, fs=12, ls="--")

    # Bước chốt chung dưới cùng
    _box(ax, 0.55, 0.35, 11.45, 0.95,
         "Bước cuối (Áp dụng cho cả 3 trạng thái):\nGhi MetricsLogger (CSV)  →  Chốt wastedBytes  →  MarkovPredictor.learn() & Tải ngầm màn hình tiếp theo",
         fc="#F2F2F2", lw=2.0, fs=11.8)

    # Mũi tên từ trên xuống Kiểm tra 1
    _arrow(ax, (2.67, 7.15), (2.67, 6.43), lw=2.0)

    # Kiểm tra 1 -> FULL_HIT (Đúng) và -> Kiểm tra 2 (Không)
    _arrow(ax, (4.80, 5.87), (6.17, 5.87), lw=2.0)
    ax.text(5.48, 6.08, "ĐÚNG", fontsize=11.5, fontweight="bold", color="#000000", ha="center")

    _arrow(ax, (2.67, 5.35), (2.67, 4.68), lw=2.0)
    ax.text(2.90, 5.00, "Không", fontsize=11, fontweight="bold", color="#000000")

    # Kiểm tra 2 -> LATE_HIT (Đúng) và -> Trường hợp 3 (Không)
    _arrow(ax, (4.80, 4.12), (6.17, 4.12), lw=2.0)
    ax.text(5.48, 4.33, "ĐÚNG", fontsize=11.5, fontweight="bold", color="#000000", ha="center")

    _arrow(ax, (2.67, 3.60), (2.67, 2.93), lw=2.0)
    ax.text(2.90, 3.25, "Không", fontsize=11, fontweight="bold", color="#000000")

    # Trường hợp 3 -> MISS
    _arrow(ax, (4.80, 2.37), (6.17, 2.37), lw=2.0)
    ax.text(5.48, 2.58, "Tải mới", fontsize=11.5, fontweight="bold", color="#000000", ha="center")

    # Gom 3 nhánh kết quả xuống Bước cuối
    _arrow(ax, (9.10, 1.85), (9.10, 1.33), lw=2.0)
    ax.plot([12.0, 12.28, 12.28, 12.0], [5.87, 5.87, 0.82, 0.82], color="#000000", linewidth=1.8, zorder=2)
    ax.plot([12.0, 12.28], [4.12, 4.12], color="#000000", linewidth=1.8, zorder=2)
    _arrow(ax, (12.28, 0.82), (12.02, 0.82), lw=2.0)

    fig.tight_layout(pad=0.3)
    fig.savefig(str(out_path), dpi=300, facecolor="white")
    plt.close(fig)


def set_cell_shading(cell, fill_hex):
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), fill_hex)
    tc_pr.append(shd)


def clean_math_text(text):
    """Chuyển ký hiệu LaTeX trong Markdown sang ký hiệu toán học Unicode chuẩn trong Word."""
    replacements = {
        r"\text{minSupport}": "minSupport",
        r"\text{maxOrder}": "maxOrder",
        r"\text{Support}": "Support",
        r"\text{Chosen}": "Chosen",
        r"\text{Size}": "Size",
        r"\text{Profile}": "Profile",
        r"\text{fetch}": "fetch",
        r"\text{saved}": "saved",
        r"\text{user}": "user",
        r"\text{total}": "total",
        r"\text{eff}": "eff",
        r"\text{nav}": "nav",
        r"\text{ready}": "ready",
        r"\text{rem}": "rem",
        r"\text{tx}": "tx",
        r"\text{th}": "th",
        r"\text{KB}": "KB",
        r"\text{Mbps}": "Mbps",
        r"\text{Kbps}": "Kbps",
        r"\text{RTT}": "RTT",
        r"\text{BW}": "BW",
        r"\text{ms}": "ms",
        r"\text{lex}": "lex",
        r"\text{--}": "–",
        r"\mathcal{S}": "S",
        r"\mathcal{N}": "N",
        r"\mathbf{M}": "M",
        r"\hat{P}": "P̂",
        r"\theta_1": "θ_1",
        r"\theta_2": "θ_2",
        r"\theta": "θ",
        r"\lambda": "λ",
        r"\gamma": "γ",
        r"\mu": "μ",
        r"\sigma": "σ",
        r"\emptyset": "∅",
        r"\subseteq": "⊆",
        r"\in": "∈",
        r"\notin": "∉",
        r"\ge": "≥",
        r"\le": "≤",
        r"\neq": "≠",
        r"\approx": "≈",
        r"\times": "×",
        r"\cdot": "·",
        r"\to": "→",
        r"\rightarrow": "→",
        r"\succ": "≻",
        r"\iff": "⇔",
        r"\lor": "∨",
        r"\land": "∧",
        r"\forall": "∀",
        r"\sum": "∑",
        r"\max": "max",
        r"\min": "min",
        r"\arg\max": "argmax",
        r"\dots": "...",
        r"\mid": "|",
        r"\;": " ",
        r"\%": "%",
        r"_{t+1}": "_{t+1}",
        r"_{t-1}": "_{t-1}",
        r"_{t-k+1}": "_{t-k+1}",
        r"^{(1)}": "^{(1)}",
        r"^{(2)}": "^{(2)}",
        r"^{(k)}": "^{(k)}",
        r"^{(k^*)}": "^{(k*)}",
        r"k^*": "k^{*}",
    }
    out = text
    for k, v in replacements.items():
        out = out.replace(k, v)
    out = re.sub(r"\$\$(.*?)\$\$", r"\1", out)
    out = re.sub(r"\$(.*?)\$", r"\1", out)
    out = out.replace(r"\left", "").replace(r"\right", "")
    return out


def _add_math_sub_sup_runs(paragraph, text, font_name, base_size, bold=False, italic=False):
    """Tách chỉ số dưới (_{...} hoặc _t/_k/_i/_j/_1/_2) và chỉ số trên (^{...}) thành subscript/superscript thật của Word."""
    pattern = re.compile(r"(_\{[^}]+\}|\^\{[^}]+\}|_[tkij12](?![A-Za-z0-9_]))")
    pos = 0
    for m in pattern.finditer(text):
        if m.start() > pos:
            r = paragraph.add_run(text[pos:m.start()])
            r.bold = bold
            r.italic = italic
            r.font.name = font_name
            r.font.size = Pt(base_size)
        tok = m.group(0)
        if tok.startswith("_{"):
            val = tok[2:-1]
            r = paragraph.add_run(val)
            r.font.subscript = True
        elif tok.startswith("^{"):
            val = tok[2:-1]
            r = paragraph.add_run(val)
            r.font.superscript = True
        else:
            val = tok[1:]
            r = paragraph.add_run(val)
            r.font.subscript = True
        r.bold = bold
        r.italic = italic
        r.font.name = font_name
        r.font.size = Pt(base_size)
        pos = m.end()
    if pos < len(text):
        r = paragraph.add_run(text[pos:])
        r.bold = bold
        r.italic = italic
        r.font.name = font_name
        r.font.size = Pt(base_size)


def add_formatted_runs(paragraph, text, base_size=13, force_italic=False):
    text = clean_math_text(text).replace("<br>", "\n")
    tokens = re.split(r"(\*\*.*?\*\*|`.*?`)", text)
    for tok in tokens:
        if not tok:
            continue
        if tok.startswith("**") and tok.endswith("**"):
            _add_math_sub_sup_runs(paragraph, tok[2:-2], "Times New Roman", base_size, bold=True, italic=force_italic)
        elif tok.startswith("`") and tok.endswith("`"):
            run = paragraph.add_run(tok[1:-1])
            run.italic = force_italic
            run.font.name = "Consolas"
            run.font.size = Pt(base_size - 1)
        else:
            _add_math_sub_sup_runs(paragraph, tok, "Times New Roman", base_size, bold=False, italic=force_italic)


def insert_figure(doc, img_path: Path, caption_text: str, width_cm: float = 15.2):
    if img_path.exists():
        p_img = doc.add_paragraph()
        p_img.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p_img.paragraph_format.space_before = Pt(6)
        p_img.paragraph_format.space_after = Pt(4)
        p_img.add_run().add_picture(str(img_path), width=Cm(width_cm))
    cap = doc.add_paragraph()
    cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
    cap.paragraph_format.space_after = Pt(10)
    add_formatted_runs(cap, caption_text, base_size=12, force_italic=True)


def build_word_report():
    sim_dir = Path(__file__).resolve().parent
    lab_dir = sim_dir.parent
    out_dir = lab_dir / "out"
    out_dir.mkdir(parents=True, exist_ok=True)

    # 1. Tự động vẽ 3 sơ đồ Hình 2.1, Hình 3.1, Hình 3.2 (chữ to, rõ nét 300 DPI)
    fig_2_1 = out_dir / "fig_2_1_markov_backoff.png"
    fig_3_1 = out_dir / "fig_3_1_architecture.png"
    fig_3_2 = out_dir / "fig_3_2_sequence.png"
    draw_fig_2_1(fig_2_1)
    draw_fig_3_1(fig_3_1)
    draw_fig_3_2(fig_3_2)
    print(f"[+] Đã vẽ 3 sơ đồ chất lượng cao (300 DPI) vào -> {out_dir}")

    md_path = lab_dir / "BAO_CAO_TOAN_NHOM_ABC.md"
    out_docx = lab_dir / "BAO_CAO_DO_AN_MARKOV_PREFETCH.docx"
    lc_img = out_dir / "learning_curve.png"
    pareto_img = out_dir / "pareto_3g.png"

    doc = Document()

    # Khổ A4, lề chuẩn báo cáo đại học (Trái 3cm, Phải 2cm, Trên 2.5cm, Dưới 2.5cm)
    for section in doc.sections:
        section.page_width = Cm(21.0)
        section.page_height = Cm(29.7)
        section.top_margin = Cm(2.5)
        section.bottom_margin = Cm(2.5)
        section.left_margin = Cm(3.0)
        section.right_margin = Cm(2.0)

    normal_style = doc.styles["Normal"]
    normal_style.font.name = "Times New Roman"
    normal_style.font.size = Pt(13)
    normal_style.paragraph_format.line_spacing = 1.35
    normal_style.paragraph_format.space_after = Pt(6)

    # Trang bìa chuẩn
    title_p = doc.add_paragraph()
    title_p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    title_p.paragraph_format.space_before = Pt(36)
    title_p.paragraph_format.space_after = Pt(18)
    r1 = title_p.add_run("BÁO CÁO ĐỒ ÁN MÔN HỌC\n\n")
    r1.bold = True
    r1.font.name = "Times New Roman"
    r1.font.size = Pt(18)

    r2 = title_p.add_run(
        "ĐỀ TÀI:\n"
        "NGHIÊN CỨU VÀ XÂY DỰNG HỆ THỐNG DỰ ĐOÁN HÀNH VI ĐIỀU HƯỚNG NGƯỜI DÙNG\n"
        "BẰNG MÔ HÌNH CHUỖI MARKOV ĐA BẬC KẾT HỢP TẢI TRƯỚC THÍCH ỨNG MẠNG\n"
        "TRÊN ỨNG DỤNG DI ĐỘNG ANDROID (SOCIALAPP)\n"
    )
    r2.bold = True
    r2.font.name = "Times New Roman"
    r2.font.size = Pt(15)

    meta_p = doc.add_paragraph()
    meta_p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    meta_p.paragraph_format.space_before = Pt(48)
    r3 = meta_p.add_run(
        "NHÓM THỰC HIỆN:\n"
        "- Thành viên A: Phân hệ Giao diện, Repository, ReplayEngine & Overlay (Tasks A1–A5)\n"
        "- Thành viên B: Phân hệ Dự đoán Markov Đa bậc, Kiểm tra chéo & Mô phỏng (Tasks B1–B5)\n"
        "- Thành viên C: Phân hệ Giả lập mạng, PrefetchCache & Chính sách P0–P5 (Tasks C1–C5)\n"
    )
    r3.font.name = "Times New Roman"
    r3.font.size = Pt(13)

    doc.add_page_break()

    lines = md_path.read_text(encoding="utf-8").splitlines()
    in_code_block = False
    code_lang = ""
    code_lines = []
    table_rows = []

    def flush_table():
        nonlocal table_rows
        if not table_rows:
            return
        valid_rows = [
            r for r in table_rows
            if not all(set(c.strip()) <= {"-", ":"} for c in r)
        ]
        if not valid_rows:
            table_rows = []
            return
        n_cols = max(len(r) for r in valid_rows)
        tbl = doc.add_table(rows=len(valid_rows), cols=n_cols)
        tbl.style = "Table Grid"
        tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
        for r_idx, row_data in enumerate(valid_rows):
            for c_idx in range(n_cols):
                cell_text = row_data[c_idx].strip() if c_idx < len(row_data) else ""
                cell = tbl.cell(r_idx, c_idx)
                p = cell.paragraphs[0]
                p.paragraph_format.space_after = Pt(2)
                p.paragraph_format.line_spacing = 1.15
                add_formatted_runs(p, cell_text, base_size=11)
                if r_idx == 0:
                    set_cell_shading(cell, "E6E6E6")
                    for run in p.runs:
                        run.bold = True
        doc.add_paragraph()
        table_rows = []

    for line in lines:
        stripped = line.strip()

        if stripped.startswith("```"):
            flush_table()
            if not in_code_block:
                in_code_block = True
                code_lang = stripped[3:].strip()
                code_lines = []
            else:
                in_code_block = False
                if code_lang != "mermaid" and code_lines:
                    p = doc.add_paragraph()
                    p.paragraph_format.left_indent = Cm(0.8)
                    r = p.add_run("\n".join(code_lines))
                    r.font.name = "Consolas"
                    r.font.size = Pt(10.5)
            continue

        if in_code_block:
            code_lines.append(line)
            continue

        if stripped.startswith("|") and stripped.endswith("|"):
            cols = [c.strip() for c in stripped.strip("|").split("|")]
            table_rows.append(cols)
            continue
        else:
            flush_table()

        if not stripped or stripped == "---":
            continue

        if stripped.startswith("# BÁO CÁO ĐỒ ÁN MÔN HỌC") or stripped.startswith("**ĐỀ TÀI:**"):
            continue

        # Chèn tự động Hình 2.1, Hình 3.1, Hình 3.2 đúng ngay trên chú thích
        if stripped.startswith("*Hình 2.1:"):
            insert_figure(doc, fig_2_1, stripped.strip("*"), width_cm=13.5)
            continue
        if stripped.startswith("*Hình 3.1:"):
            insert_figure(doc, fig_3_1, stripped.strip("*"), width_cm=15.8)
            continue
        if stripped.startswith("*Hình 3.2:"):
            insert_figure(doc, fig_3_2, stripped.strip("*"), width_cm=15.5)
            continue

        # Chèn tự động Hình 4.1 và Hình 4.2
        if "learning_curve.png" in stripped and "pareto_3g.png" in stripped and stripped.startswith("*("):
            insert_figure(doc, lc_img, "Hình 4.1: Biểu đồ đường cong học (Learning Curve) của Markov bậc 1 và bậc 2.", width_cm=14.5)
            insert_figure(doc, pareto_img, "Hình 4.2: Biểu đồ biên Pareto trên mạng 3G (Độ trễ vs. Băng thông lãng phí).", width_cm=14.5)
            continue

        # Chú thích bảng biểu (*Bảng ...*)
        if stripped.startswith("*Bảng ") and stripped.endswith("*"):
            p = doc.add_paragraph()
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p.paragraph_format.space_before = Pt(6)
            p.paragraph_format.space_after = Pt(4)
            add_formatted_runs(p, stripped.strip("*"), base_size=12, force_italic=True)
            continue

        # Tiêu đề Chương / Mục
        if stripped.startswith("## "):
            h_text = clean_math_text(stripped[3:].strip())
            if h_text.startswith("CHƯƠNG ") or h_text.startswith("TÀI LIỆU") or h_text.startswith("PHỤ LỤC"):
                doc.add_page_break()
            h = doc.add_heading(level=1)
            r = h.add_run(h_text)
            r.bold = True
            r.font.name = "Times New Roman"
            r.font.size = Pt(15)
            r.font.color.rgb = RGBColor(0, 0, 0)
        elif stripped.startswith("### "):
            h = doc.add_heading(level=2)
            r = h.add_run(clean_math_text(stripped[4:].strip()))
            r.bold = True
            r.font.name = "Times New Roman"
            r.font.size = Pt(14)
            r.font.color.rgb = RGBColor(0, 0, 0)
        elif stripped.startswith("#### "):
            h = doc.add_heading(level=3)
            r = h.add_run(clean_math_text(stripped[5:].strip()))
            r.bold = True
            r.font.name = "Times New Roman"
            r.font.size = Pt(13)
            r.font.color.rgb = RGBColor(0, 0, 0)
        elif stripped.startswith("$$") and stripped.endswith("$$"):
            p = doc.add_paragraph()
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            add_formatted_runs(p, stripped, base_size=13, force_italic=True)
        elif stripped.startswith("- ") or stripped.startswith("• "):
            p = doc.add_paragraph(style="List Bullet")
            add_formatted_runs(p, stripped[2:].strip(), base_size=13)
        else:
            p = doc.add_paragraph()
            p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
            add_formatted_runs(p, stripped, base_size=13)

    flush_table()
    doc.save(str(out_docx))
    print(f"[+] Đã xuất thành công quyển báo cáo Word chuẩn (đủ 5 hình vẽ) -> {out_docx}")


if __name__ == "__main__":
    build_word_report()
