# TÀI LIỆU BÀN GIAO & ĐẶC TẢ KỸ THUẬT: KHỐI MẠNG GIẢ & TẢI TRƯỚC (LONG)
> **Đề tài:** Tối ưu hóa hiệu năng ứng dụng thông qua cơ chế Tải trước dữ liệu (Prefetching) dựa trên mô hình Markov và đánh giá đánh đổi Pareto (Độ trễ vs Dữ liệu lãng phí).
> **Phân công đảm nhiệm:** **LONG (Task C1 - C5)**  
> **Tài liệu DevTool tích hợp:** Xem chi tiết tại [DEVTOOL_REQUIREMENTS.md](file:///c:/Users/ADMIN/Desktop/Bai%20tap%20lon/appAndroidDetectOoc/DEVTOOL_REQUIREMENTS.md) (Bộ công cụ DevTool chạy cùng app để giám sát gói tin, thời gian load xong UI và kịch bản thực nghiệm).

---

## 1. TỔNG QUAN HỆ THỐNG & PHÂN CÔNG ĐỀ TÀI

### 1.1. Các khối thành phần trong hệ thống
| Khối | Nhiệm vụ |
| :--- | :--- |
| **App (đã có)** | Các màn hình giao diện (`com.socialapp.feature.*`), gọi dữ liệu tập trung qua `Repository.load(screen)`. |
| **Nguồn dữ liệu giả** | Chờ theo công thức $RTT + \frac{\text{Kích thước} \times \text{Hệ số độ nặng}}{\text{Băng thông}}$, có chia sẻ băng thông giữa các request đồng thời. |
| **Bộ dự đoán Markov** | Đếm chuyển dịch màn hình, trả về xác suất màn hình kế tiếp (bậc 1, bậc 2). |
| **Chính sách prefetch** | Quyết định tải trước màn hình nào ($P_0$ đến $P_5$). |
| **PrefetchManager & Cache** | Phát request nền, lưu kết quả, gộp request đang bay, xử lý Late Hit. |
| **MetricsLogger** | Ghi độ trễ, loại hit (`FULL_HIT`, `LATE_HIT`, `MISS`), byte lãng phí ra CSV. |
| **Bộ sinh log & Replay Engine** | Tạo phiên người dùng giả và tự điều hướng theo kịch bản log. |
| **Phân tích** | Thống kê và vẽ biểu đồ biên Pareto (Latency vs Wasted Bytes). |

---

### 1.2. Phân công 4 thành viên
1. **Duy Thành (Khảo sát lý thuyết):**
   * Đọc 5 nguồn: Jiang & Kleinrock 1998, Higgins MobiSys 2012 IMP, Malavolta ICSE 2019, Chrome Speculation Rules, Glide / Paging 3.
   * Viết chương tổng quan, so sánh, đóng góp và giới hạn của đề tài.
2. **Huy (App & Replay Engine):**
   * Gom mọi chỗ lấy dữ liệu về `Repository.load(screen)`.
   * Ghi metrics ra CSV, script kéo file về máy.
   * Replay engine tự động click màn hình theo log hành vi.
   * Thanh HUD demo (dự đoán tiếp theo, trạng thái tải trước, đúng/sai).
   * Chạy thí nghiệm trên máy ảo.
3. **Kiên (Dữ liệu & Bộ đoán Markov):**
   * Bộ sinh log hành vi người dùng (thời gian dừng, mẫu chuyển dịch).
   * Bộ đoán Markov bậc 1 và bậc 2 (Python $\rightarrow$ Java).
   * Mô phỏng offline tính hit-rate và đường cong học.
   * Thống kê và vẽ biểu đồ Pareto.
4. **LONG (Mạng giả & Tải trước - TRÁCH NHIỆM CHÍNH CỦA CHÚNG TA):**
   * **C1:** Mạng giả với 4 profile (Wi-Fi, LTE, 3G, EDGE), công thức $RTT + \frac{\text{Size} \times \text{Weight}}{\text{Bandwidth}}$, hệ số độ nặng $\times 0.5 / \times 1.0 / \times 2.0$.
   * **C2:** Cơ chế chia sẻ băng thông khi có nhiều request đồng thời (tải thật + tải trước).
   * **C3:** Cache lưu trữ kết quả tải trước có TTL, cờ `isUsed`, đo `Wasted Bytes`.
   * **C4:** `PrefetchManager` điều phối tải nền, xử lý triệt để bài toán **Late Hit**.
   * **C5:** Bộ 6 chính sách prefetch ($P_0$ đến $P_5$), cấu hình chuyển đổi 1 dòng.

---

## 2. LUỒNG HOẠT ĐỘNG CHI TIẾT

### 2.1. Luồng khi người dùng chuyển màn hình
```text
Người dùng bấm vào màn hình Y
        │
        ▼
Repository.load(Y)
        │
        ├─ Cache đã có Y?          ──► FULL HIT: Trả dữ liệu ngay (độ trễ ≈ 0)
        ├─ Y đang được prefetch?   ──► LATE HIT: Chờ phần còn lại của request đang bay, không gửi trùng
        └─ Chưa có                 ──► MISS: Gọi Mạng giả (tính toán trễ theo RTT & BW)
        │
        ▼
Hiển thị màn hình Y  ──► MetricsLogger ghi: độ trễ, loại hit
        │
        ▼
Bộ dự đoán Markov nhận lịch sử [..., X, Y]
        │  trả về danh sách ứng viên: Z1 (60%), Z2 (25%), Z3 (10%)
        ▼
Chính sách Prefetch (C5) quyết định danh sách tải
        │  (Ví dụ P5: p × thời_gian_tải > λ × byte)
        ▼
PrefetchManager (C4) phát request nền ──► Lưu vào PrefetchCache (C3)
        │
        ▼
Người dùng bấm tiếp:
   - Đoán đúng ──► Lần sau đạt FULL HIT hoặc LATE HIT
   - Đoán sai  ──► Dữ liệu trong Cache hết hạn mà không dùng ──► Ghi nhận Wasted Bytes
```

### 2.2. Luồng thí nghiệm đo đạc
```text
Bộ sinh log (Kiên) ──► File phiên giả
        │
        ├──► Mô phỏng offline: Hit rate, đường cong học
        │
        └──► Replay engine (Huy) chạy trong App Android
                 │  Với mỗi tổ hợp: Chính sách (P0-P5) × Mạng (4 profiles) × Độ nặng (x0.5/x1/x2)
                 ▼
             CSV kết quả ──► Thống kê & Vẽ biểu đồ Pareto (Độ trễ vs Byte lãng phí)
```

---

## 3. ĐẶC TẢ KỸ THUẬT CHI TIẾT CỦA LONG (JAVA CODEBASE TRONG `appAndroidDetectOoc`)

### 3.1. Package đề xuất trong `com.socialapp.core`:
* `com.socialapp.core.network.sim`:
  * `NetworkProfile.java` (Enum: `WIFI`, `LTE_4G`, `NETWORK_3G`, `EDGE_2G`)
  * `PayloadWeight.java` (Enum: `LIGHT_0_5`, `NORMAL_1_0`, `HEAVY_2_0`)
  * `MockNetworkEngine.java` (Mô phỏng trễ toán học tất định và chia sẻ băng thông)
* `com.socialapp.core.prefetch`:
  * `PrefetchCache.java` (Quản lý TTL, cờ `isUsed`, tính `wastedBytes`)
  * `PrefetchManager.java` (Quản lý In-flight requests, xử lý `FULL_HIT`, `LATE_HIT`, `MISS`)
  * `IPrefetchPolicy.java` (Interface chính sách)
  * `policy.NoPrefetchPolicy` ($P_0$)
  * `policy.PrefetchAllPolicy` ($P_1$)
  * `policy.MostFrequentPolicy` ($P_2$)
  * `policy.MarkovOrder1ThresholdPolicy` ($P_3$)
  * `policy.MarkovOrder2Policy` ($P_4$)
  * `policy.NetworkCostAdaptivePolicy` ($P_5$)

### 3.2. Bảng tham số Mạng giả (Task C1 & C2)
| Profile | RTT (ms) | Băng thông (KB/s) | Ghi chú |
| :--- | :---: | :---: | :--- |
| **Wi-Fi** | $15$ | $6,250$ ($50\text{ Mbps}$) | Tốc độ cao, trễ cực thấp |
| **LTE (4G)** | $50$ | $2,500$ ($20\text{ Mbps}$) | Mạng di động tiêu chuẩn |
| **3G** | $180$ | $250$ ($2\text{ Mbps}$) | Mạng di động chậm |
| **EDGE (2G)**| $400$ | $20$ ($160\text{ Kbps}$) | Mạng cực yếu |

* **Công thức trễ lý thuyết:**
  $$\text{Latency} = \text{RTT} + \frac{\text{BaseSize} \times \text{WeightFactor}}{\text{EffectiveBandwidth}}$$
* **Tranh chấp băng thông (Task C2):** Khi có $N$ request đồng thời đang tải (Foreground + Prefetch Background), băng thông khả dụng được chia sẻ theo tỷ lệ:
  $$\text{EffectiveBandwidth}_i = \frac{\text{TotalBandwidth}}{N}$$

### 3.3. Cơ chế Late Hit (Task C4)
* Dùng `Map<String, CompletableFuture<ScreenData>> inFlightRequests`.
* Khi `Repository.load(Y)` được gọi:
  1. Kiểm tra `PrefetchCache.get(Y)`: Nếu có và chưa hết hạn $\rightarrow$ **FULL HIT** (trả về ngay, latency $\approx 0\text{ ms}$).
  2. Kiểm tra `inFlightRequests.get(Y)`: Nếu đang tồn tại Future đang chạy $\rightarrow$ **LATE HIT** (gắn callback `inFlight.thenAccept(...)` để nhận kết quả khi request nền hoàn tất, chỉ chịu độ trễ còn lại).
  3. Nếu không có $\rightarrow$ **MISS** (phát request mới qua `MockNetworkEngine`).

---
*Tài liệu này được đồng bộ trực tiếp từ phiên làm việc để đảm bảo toàn bộ mã nguồn của dự án Android kế thừa và thực thi chính xác 100% theo đặc tả.*
