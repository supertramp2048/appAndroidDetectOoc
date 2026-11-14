# TÀI LIỆU YÊU CẦU & ĐẶC TẢ KỸ THUẬT
# BỘ CÔNG CỤ DEVTOOL TÍCH HỢP CHẠY CÙNG ỨNG DỤNG
# (IN-PROCESS RUNTIME DEVTOOL & PERFORMANCE INSPECTOR)

> **Mã tài liệu:** `DEVTOOL-SPEC-01`  
> **Dự án:** `appAndroidDetectOoc` (SocialApp Android)  
> **Phạm vi đảm nhiệm:** Phân vai LONG (Khối Mạng giả & Tải trước kết hợp DevTool giám sát thực nghiệm)  
> **Mục tiêu:** Cung cấp bộ công cụ chuyên dụng (thuần Dev) chạy song hành cùng ứng dụng trong cùng tiến trình (In-Process Agent) để giám sát gói tin, bóp băng thông, đo đạc thời gian load xong giao diện (TTFD), theo dõi kịch bản hành vi người dùng và hạch toán số byte lãng phí phục vụ bài toán đánh đổi Pareto.

---

## 1. NGUYÊN TẮC THIẾT KẾ & TÍNH CHẤT CỦA TOOL

1. **Chạy cùng ứng dụng (In-Process Agent):**
   * Công cụ được tích hợp và khởi chạy ngay bên trong tiến trình của ứng dụng (`com.socialapp`), không phải là một app độc lập hay server bên ngoài.
   * Giám sát và đọc dữ liệu trực tiếp từ bộ nhớ runtime mà không làm gián đoạn trải nghiệm người dùng.
2. **Cơ chế đọc không xâm lấn (Non-Intrusive Instrumentation):**
   * Tự động thu thập dữ liệu thông qua các điểm cắm (Hooks): OkHttp Interceptor, `ActivityLifecycleCallbacks`, `ViewTreeObserver`, `Choreographer` và `PrefetchManager`.
   * Không can thiệp, không sửa đổi và không làm ô nhiễm mã nguồn nghiệp vụ của các Activity/Fragment hiện có.
3. **Giao diện thuần Dev (Pure Developer Console):**
   * Thiết kế theo phong cách bàn làm việc kỹ thuật cao (tương tự **Chrome DevTools Network & Performance tab, Flipper, Android Studio Profiler**).
   * Mật độ thông tin cao, sử dụng font chữ đơn khoảng (`ui-monospace`), giao diện nền trắng sáng sủa, tối giản, loại bỏ hoàn toàn các yếu tố bo tròn màu mè dành cho người dùng cuối.
4. **Bảo mật & Phân quyền ("Chỉ bật khi có quyền"):**
   * Tuyệt đối ẩn hoàn toàn đối với người dùng thông thường.
   * Chỉ kích hoạt khi thỏa mãn: Tài khoản có role `admin`/`moderator` (từ `SessionManager`) HOẶC người dùng mở khóa Developer Mode (chạm 7 lần vào phiên bản ứng dụng trong Cài đặt).

---

## 2. BỐN TRỤ CỘT THEO DÕI CỐT LÕI (CORE TELEMETRY PILLARS)

```text
┌────────────────────────────────────────────────────────────────────────────────┐
│                           BỘ MẮT ĐỌC DỮ LIỆU TỰ ĐỘNG                           │
├────────────────────┬────────────────────┬────────────────────┬─────────────────┤
│    1. GÓI TIN      │   2. THỜI GIAN     │    3. KỊCH BẢN     │   4. BỘ NHỚ     │
│   VÀ MẠNG ẢO       │   LOAD XONG UI     │      XẢY RA        │   VÀ LÃNG PHÍ   │
│ (Network Packets)  │   (TTID & TTFD)    │  (User Scenarios)  │ (Cache & Wasted)│
├────────────────────┼────────────────────┼────────────────────┼─────────────────┤
│ • Kích thước KB    │ • T_Data (Chờ mạng)│ • Chuỗi kịch bản   │ • Danh sách RAM │
│ • RTT & Transfer   │ • T_Render (Vẽ UI) │ • Dwell Time (dừng)│ • Đếm ngược TTL │
│ • BW hiệu dụng     │ • Total TTFD       │ • Xác suất Markov  │ • Wasted Bytes  │
│ • Bóp Wi-Fi/4G/3G/2G│ • % Tiết kiệm trễ │ • Chính sách P0-P5 │ • Tránh trùng lặp│
└────────────────────┴────────────────────┴────────────────────┴─────────────────┘
```

---

### TRỤ CỘT 1: GIÁM SÁT GÓI TIN & BÓP BĂNG THÔNG (PACKET & THROTTLING)
* **Thu thập dữ liệu gói tin:**
  * Endpoint / Tên gói tin màn hình (`home`, `post_detail`, `comments`, `profile`, `factcheck`).
  * Kích thước cơ sở ($KB$) và hệ số độ nặng ($0.5\times, 1.0\times, 2.0\times$).
  * Băng thông hiệu dụng được chia sẻ theo cơ chế Fluid Flow:
    $$BW_{\text{effective}} = \frac{BW_{\text{total}}}{N}$$
  * Phân rã thời gian truyền gói tin: $T_{\text{Queue}} + \text{RTT} + T_{\text{Transfer}}$.
* **Điều khiển bóp mạng (Throttling Controls):**
  * Hỗ trợ chuyển đổi nhanh 4 profile: **Wi-Fi (50 Mbps)**, **LTE 4G (20 Mbps)**, **3G (2 Mbps)**, **EDGE 2G (160 Kbps)**.
  * Hỗ trợ công tắc tắt/bật giả lập tức thì ($0\text{ ms}$ bypass).

---

### TRỤ CỘT 2: ĐO LƯỜNG THỜI GIAN APP LOAD XONG GIAO DIỆN (UI LOAD COMPLETION / TTFD)
* **Định nghĩa mốc thời gian:**
  * Điểm bắt đầu ($T_{\text{Start}}$): Thời điểm người dùng chạm nút điều hướng / phát lệnh chuyển màn hình.
  * Điểm kết thúc ($T_{\text{Finish}}$): Thời điểm frame giao diện hoàn chỉnh được `Choreographer` vẽ xong lên màn hình điện thoại (Time to Fully Drawn - TTFD).
* **Công thức phân rã:**
  $$\text{Total UI Load Time} = T_{\text{Data}} + T_{\text{Render}}$$
  * $T_{\text{Data}}$: Thời gian nạp dữ liệu.
    * Nếu `FULL_HIT`: $T_{\text{Data}} = 0\text{ ms}$ (dữ liệu có sẵn trong RAM).
    * Nếu `LATE_HIT`: $T_{\text{Data}} = T_{\text{Remaining}}$ (chỉ chờ phần còn lại của gói tin đang bay dở).
    * Nếu `MISS`: $T_{\text{Data}} = \text{RTT} + T_{\text{Transfer}}$ (chờ trọn vẹn từ mạng giả).
  * $T_{\text{Render}}$: Thời gian vòng đời Activity/Fragment + XML View Inflation + DiffUtil Binding + Choreographer Draw ($\approx 15 - 30\text{ ms}$).
* **Chỉ số viễn trắc:**
  * Tỉ lệ phần trăm thời gian tiết kiệm được so với tải mạng thuần:
    $$\% \text{ Tiết kiệm} = \frac{T_{\text{Total (MISS)}} - T_{\text{Total (HIT)}}}{T_{\text{Total (MISS)}}} \times 100\%$$

---

### TRỤ CỘT 3: THEO DÕI CÁCH KỊCH BẢN XẢY RA (SCENARIOS & USER JOURNEYS)
* **Vết kịch bản người dùng (Scenario Trace):**
  * Ghi lại chuỗi hành trình: $S_0 \xrightarrow{\Delta t_1} S_1 \xrightarrow{\Delta t_2} S_2 \dots$ (nhận diện cả người dùng bấm thật lẫn Replay Engine tự động chạy).
  * Đo **Dwell Time** (thời gian người dùng dừng lại ở từng màn hình): Đây là khoảng thời gian rảnh rỗi để hệ thống kích hoạt gói tin tải trước ngầm.
* **Kiểm chứng thuật toán Markov & Chính sách:**
  * Hiển thị bảng phân phối xác suất dự đoán màn hình kế tiếp từ bộ đoán Markov bậc 1 và bậc 2.
  * Bộ chọn chuyển đổi 1 trong 6 chính sách Prefetch ($P_0 \to P_5$):
    * $P_0$: Baseline (Không tải trước).
    * $P_1$: Tải toàn bộ ứng viên $p > 0$.
    * $P_2$: Top-1 (Chỉ tải màn hình có xác suất cao nhất).
    * $P_3$: Markov bậc 1 theo ngưỡng $\theta = 0.4$.
    * $P_4$: Markov bậc 2 (dựa trên chuỗi 2 bước gần nhất).
    * $P_5$: Network Cost Adaptive (Kinh tế mạng thích ứng).

---

### TRỤ CỘT 4: BỘ NHỚ ĐỆM & HẠCH TOÁN LÃNG PHÍ (CACHE & WASTED BYTES)
* **Quản lý bộ nhớ đệm:**
  * Giám sát các gói tin đang được lưu trong cache RAM cùng thời gian sống còn lại (TTL countdown).
* **Hạch toán lãng phí (`Wasted Bytes`):**
  * Khi một gói tin được tải trước nhưng hết hạn TTL mà người dùng không chuyển đến màn hình đó $\rightarrow$ Tool tự động cộng dồn dung lượng gói tin vào tổng `Wasted Bytes`.
  * Cơ chế khử trùng lặp (Late Hit deduplication): Không bao giờ gửi 2 gói tin cùng một nội dung đồng thời.
* **Xuất dữ liệu thực nghiệm:**
  * Cung cấp nút **Xuất CSV** ghi nhận toàn bộ log viễn trắc của phiên để Kiên & Huy vẽ đồ thị đánh đổi Pareto (Độ trễ vs Byte lãng phí).

---

## 3. THIẾT KẾ GIAO DIỆN BỘ TOOL (THUẦN DEV, NỀN TRẮNG CHUYÊN NGHIỆP)

Bộ công cụ gồm 2 chế độ hiển thị linh hoạt:

### 3.1. Huy hiệu Mini Capsule Nổi (Floating HUD Capsule)
* Một thanh con nhộng nhỏ gọn, nền trắng viền xám mảnh bo tròn nằm ở góc màn hình:
  ```text
  [ ● 4G | UI: 24ms | FULL_HIT ]
  ```
* Luôn cập nhật tức thì theo thời gian thực mỗi khi có chuyển màn hình.
* Chạm vào để mở **Bảng điều khiển DevTools Console**.

### 3.2. Bảng điều khiển DevTools Console (Fixed Data Grid & Master-Detail)
* **Thanh công cụ Toolbar trên cùng:**
  * Nút Record / Pause (chấm đỏ nhấp nháy), nút Clear Log (⊘).
  * Hộp lọc văn bản (Filter by route, hit, size).
  * Các nút lọc nhanh: `All`, `FULL_HIT`, `LATE_HIT`, `MISS`.
  * Các dropdown cấu hình: Profile mạng, Hệ số độ nặng, Chính sách Prefetch.
* **Nửa trên: Bảng dữ liệu viễn trắc (Telemetry Data Grid):**
  * Bố cục dạng bảng cố định với các cột kỹ thuật: `Time`, `Route/Target`, `Hit Type`, `Size (KB)`, `Network (ms)`, `UI Render (ms)`, `TTFD Total (ms)`, `Timing Waterfall`.
* **Nửa dưới: Ngăn kiểm tra chi tiết (Inspector Drawer) gồm 5 Tab:**
  1. `1. Timing & Render Breakdown`: Bóc tách chi tiết từng mili-giây của giai đoạn nạp dữ liệu và giai đoạn vẽ UI.
  2. `2. Packet Headers & Network Sim`: Xem thông số gói tin, băng thông chia sẻ, Request ID.
  3. `3. Scenario & Markov Transitions`: Xem chuỗi kịch bản người dùng, Dwell Time và xác suất Markov.
  4. `4. Cache & Wasted Accounting`: Danh sách gói tin trong RAM, đếm ngược TTL và tổng byte lãng phí.
  5. `5. Raw Payload`: Xem trực tiếp định dạng JSON phản hồi.

---

## 4. QUY HOẠCH GÓI MÃ NGUỒN (PACKAGE STRUCTURE)

Toàn bộ code của bộ DevTool được tổ chức độc lập trong gói:
`com.socialapp.core.devtool`

```text
com.socialapp.core.devtool
├── DevToolManager.java             // Quản lý trung tâm: quyền truy cập, ring buffer, bộ thu thập
├── DevToolLogEntry.java            // Cấu trúc dữ liệu của 1 bản ghi viễn trắc gói tin & UI load
├── DevToolHitType.java             // Enum: FULL_HIT, LATE_HIT, MISS
├── DevToolTimingBreakdown.java     // Phân rã thời gian: T_Data (RTT, Transfer) và T_Render (Inflation, Draw)
├── ui
│   ├── DevToolFloatingCapsule.java // View con nhộng nổi trên màn hình
│   ├── DevToolConsoleActivity.java // Bàn làm việc DevTools Console toàn diện
│   └── DevToolTableAdapter.java    // Adapter hiển thị lưới bảng dữ liệu mật độ cao
└── hook
    ├── DevToolNetworkHook.java     // Cắm vào MockNetworkEngine để bắt gói tin
    └── DevToolUiRenderHook.java    // Cắm vào ActivityLifecycle & Choreographer để bấm giờ UI
```

---

## 5. KẾ HOẠCH TRIỂN KHAI THEO PHÊ DUYỆT TỪNG PHẦN

* **Phần 1:** Triển khai Lõi Quản trị Quyền & Thu thập Log (`DevToolManager`, `DevToolLogEntry`, `DevToolHitType`).
* **Phần 2:** Triển khai Bộ bấm giờ vẽ giao diện (`DevToolUiRenderHook` đo TTFD từ Tap $\to$ Choreographer Draw).
* **Phần 3:** Triển khai Giao diện Mini Capsule Nổi (`DevToolFloatingCapsule`).
* **Phần 4:** Triển khai Bàn làm việc DevTools Console hoàn chỉnh (`DevToolConsoleActivity` & Inspector Drawer 5 tab).
* **Phần 5:** Đấu nối vào màn hình Cài đặt (`SettingsActivity`) và mở khóa Developer Mode khi gõ 7 lần.

---
*Tài liệu này là cam kết kỹ thuật chính thức làm cơ sở nghiệm thu cho bộ công cụ DevTool trong đề tài.*
