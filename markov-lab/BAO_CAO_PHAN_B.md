# NỘI DUNG BÁO CÁO ĐỒ ÁN — PHẦN VIỆC THÀNH VIÊN B
*(Chủ đề: Dự đoán hành vi điều hướng người dùng bằng mô hình Chuỗi Markov đa bậc có hạ bậc và Mô phỏng đánh giá hiệu năng Tải trước thích ứng mạng)*

---

## CHƯƠNG 1: TỔNG QUAN ĐỀ TÀI (Phần của B — Mục 1.2)

### 1.2. Mục tiêu và Phạm vi của Phân hệ Dự đoán & Mô phỏng (Phân hệ B)
Trong kiến trúc tổng thể gồm 3 phân hệ (**A**: Giao diện ứng dụng, Cửa nạp dữ liệu trung tâm `Repository` và Robot tự động `ReplayEngine`; **B**: Bộ dự đoán hành vi điều hướng Markov và Mô phỏng đánh giá Offline; **C**: Giả lập mạng `MockNetworkEngine`, Bộ nhớ đệm `PrefetchCache` và các chính sách tải trước `P0–P5`), phân hệ **B** đóng vai trò là **khối trí tuệ dự đoán trung tâm (Prediction Core)** với các mục tiêu cụ thể sau:

1. **Xây dựng bộ dự đoán chuỗi Markov bậc k có hạ bậc (`MarkovPredictor<T>`)**:
   - Dự đoán phân phối xác suất của màn hình kế tiếp dựa trên lịch sử các màn hình mà người dùng vừa đi qua trong phiên làm việc.
   - Giải quyết bài toán thưa dữ liệu ở giai đoạn đầu (cold-start) bằng cơ chế **Hạ bậc tự động (Backoff)** kết hợp **Ngưỡng hỗ trợ tối thiểu (`minSupport = 3`)** và quy tắc xử lý hòa xác suất tất định (deterministic tie-breaking).
   - Thiết kế độc lập hoàn toàn với thư viện Android (`android.*`) để tái sử dụng nguyên bản giữa môi trường kiểm thử trên máy tính và ứng dụng Android thực tế (`MarkovPredictor.java`).
2. **Xây dựng bộ sinh nhật ký hành vi và công cụ Kiểm tra chéo (Cross-Check)**:
   - Sinh dữ liệu phiên người dùng giả lập có quy luật chuyển tiếp bậc 1, bậc 2 và thời gian dừng đọc trang (`dwell_ms` phân bố chuẩn quanh 4000 ms) thông qua `generate_logs.py`.
   - Kiểm chứng tính tương đương toán học tuyệt đối (100% bit-exact) giữa bản cài đặt Java (`CrossCheck.java`) và bản mô phỏng Python (`crosscheck.py`).
3. **Mô phỏng đánh giá hiệu năng Offline & Phân tích Đường cong học / Biên Pareto**:
   - Đánh giá định lượng độ chính xác dự đoán (Top-1 Accuracy), độ trễ chờ đợi trung bình của người dùng (Average User Latency) và dung lượng băng thông tải thừa (Wasted Bandwidth) trên 4 hồ sơ mạng (`WiFi`, `4G/LTE`, `3G`, `Poor/EDGE`).
   - Xác định ngưỡng xác suất tải trước tối ưu (theta) thông qua phân tích biên Pareto để làm cơ sở tham số cho phân hệ **C** và **A**.

---

## CHƯƠNG 2: CƠ SỞ LÝ THUYẾT & MÔ HÌNH DỰ ĐOÁN MARKOV (Mục 2.1 – 2.3)

### 2.1. Bài toán dự đoán chuỗi điều hướng người dùng trên ứng dụng di động
Gọi S = {s_1, s_2, ..., s_M} là tập hợp hữu hạn các màn hình chức năng trong ứng dụng (ví dụ trong ứng dụng mạng xã hội kiểm chứng tin tức `SocialApp`: S = {FEED_NEWEST, POST_DETAIL, COMMENTS, FACT_CHECK, PROFILE, SEARCH, SETTINGS, ...}).

Một phiên sử dụng (session) của người dùng được biểu diễn dưới dạng một chuỗi các bước chuyển màn hình theo thời gian:
  H_t = (S_1, S_2, ..., S_t)
Đi kèm với mỗi màn hình S_i là khoảng thời gian dừng (dwell time) d_i (tính bằng mili-giây) mà người dùng lưu lại để đọc nội dung trước khi bấm chuyển sang màn hình tiếp theo S_{t+1}.

**Mục tiêu của bài toán dự đoán**: Tại thời điểm người dùng vừa chuyển đến màn hình S_t, dựa trên lịch sử đã quan sát H_t = (S_1, ..., S_t), bộ dự đoán cần ước lượng phân phối xác suất có điều kiện của màn hình kế tiếp P(S_{t+1} = s | H_t) và trả về danh sách các màn hình ứng viên được sắp xếp theo thứ tự xác suất giảm dần để bộ quản lý tải trước (Prefetch Engine) tranh thủ khoảng thời gian nhàn rỗi d_t tải ngầm dữ liệu vào bộ nhớ đệm (RAM Cache).

### 2.2. Mô hình Chuỗi Markov bậc 1 và bậc 2 (Order-1 & Order-2 Markov Chains)
- **Chuỗi Markov bậc 1 (First-Order Markov Model)**: Trạng thái tiếp theo S_{t+1} chỉ phụ thuộc vào trạng thái hiện tại S_t:
  P^(1)(s | S_t) = N(S_t -> s) / sum_{s'} N(S_t -> s')
  Hạn chế của bậc 1 là không nắm bắt được ý định điều hướng phụ thuộc vào màn hình đứng liền trước S_{t-1}.
- **Chuỗi Markov bậc 2 (Second-Order Markov Model)**: Trạng thái tiếp theo phụ thuộc vào cặp 2 màn hình liên tiếp gần nhất C_t^(2) = (S_{t-1}, S_t):
  P^(2)(s | S_{t-1}, S_t) = N((S_{t-1}, S_t) -> s) / sum_{s'} N((S_{t-1}, S_t) -> s')

### 2.3. Mô hình Markov Đa bậc có Hạ bậc (All-k-th-Order Markov Model with Backoff)
Để khắc phục đồng thời hạn chế mất ngữ cảnh của bậc 1 và tính thưa dữ liệu (cold-start) của bậc 2, đồ án áp dụng thuật toán **All-k-th-Order Markov với cơ chế Hạ bậc (Backoff) và Ngưỡng hỗ trợ tối thiểu (`minSupport = 3`)**:
1. **Học đồng thời đa bậc**: Khi người dùng chuyển từ lịch sử H_t sang S_{t+1}, cập nhật tần suất cho mọi hậu tố độ dài k từ 1 đến min(maxOrder, |H_t|).
2. **Dự đoán có hạ bậc (Backoff)**: Duyệt từ bậc cao nhất k = min(maxOrder, |H_t|) lùi về 1. Bậc cao nhất có tổng số mẫu quan sát Support(C_t^(k)) >= minSupport (3) sẽ được dùng để tính phân phối xác suất. Nếu chưa đủ 3 mẫu, mô hình tự động lùi xuống bậc k - 1.
3. **Quy tắc phá vỡ thế hòa (Tie-breaking)**: Khi hai màn hình có cùng số lần xuất hiện, sắp xếp theo thứ tự từ điển A -> Z của tên màn hình để đảm bảo tính tất định 100% giữa Java và Python.

---

## CHƯƠNG 3: THIẾT KẾ & CÀI ĐẶT PHÂN HỆ B (Mục 3.2)

### 3.2. Thiết kế và Cài đặt Bộ dự đoán `MarkovPredictor<T>` và Công cụ Mô phỏng
1. **Cấu trúc bảng băm 3 tầng trong `MarkovPredictor.java`**:
   `Map<Integer, Map<List<T>, Map<T, Integer>>> tables` lưu trữ bảng đếm tần suất theo từng bậc k -> ngữ cảnh k màn hình cuối -> màn hình đích và số lần xuất hiện.
2. **Tích hợp trong ứng dụng Android**:
   - Đặt tại package `com.socialapp.core.prefetch.policy.markov.MarkovPredictor`.
   - Được sử dụng trực tiếp bởi `PrefetchEngine.java` (`markovOrder1` cho chính sách P1–P3, `markovOrder2` cho chính sách P4–P5) và bởi `MarkovTransitionMatrix` / `MarkovTransitionTensor`.
3. **Công thức thời gian tải mạng và khấu trừ độ trễ trong `offline_simulation.py`**:
   - Thời gian tải màn hình s: `fetch_ms = rtt_ms + (size_kb * 8.0) / bw_mbps`.
   - Tỷ lệ tải ngầm thành công trong thời gian đọc trang `dwell_ms`: `frac = min(1.0, rem_budget_ms / fetch_ms)`.
   - Độ trễ người dùng thực tế phải chờ: `user_latency_ms = max(0.0, full_next_ms - saved_for_next_ms)`.

---

## CHƯƠNG 4: THỰC NGHIỆM & ĐÁNH GIÁ KẾT QUẢ (Mục 4.1 – 4.2)

### 4.1. Kết quả Kiểm thử Đơn vị (Unit Test) và Kiểm tra chéo Java – Python (Cross-Check)
- **Kiểm thử đơn vị (`MarkovPredictorTest.java` & `test_markov.py`)**: Đạt 6/6 kịch bản kiểm thử (Tính tay cơ bản `C = 0.667, D = 0.333`; Cold-start `< minSupport`; Hạ bậc Backoff từ bậc 2 về bậc 1; Ưu tiên bậc 2 khi đủ mẫu; Tie-break A-Z; Cắt lịch sử dài & Null-safe).
- **Kiểm tra chéo trên 1000 phiên (`sessions.csv`, 8932 bước chuyển màn hình)**:
  - Bậc k = 1: Java và Python cùng đạt `0.5706 (5097/8932)`.
  - Bậc k = 2: Java và Python cùng đạt `0.5756 (5141/8932)` (cao nhất).
  - Bậc k = 3: Java và Python cùng đạt `0.5720 (5109/8932)`.

### 4.2. Kết quả Mô phỏng Offline, Đường cong học và Biên Pareto
- **Kiểm chứng điểm tính tay**: Độ trễ `P0` ở mạng 3G (1.5 Mbps, RTT 150 ms) cho màn hình `Detail` (400 KB) đạt `2283.33 ms` (~2283 ms); lãng phí của `P0` bằng `0 KB`; `Oracle` có độ trễ thấp nhất (`1.69 ms`).
- **Hiệu quả giảm độ trễ trên mạng 3G (`sim_results.csv`)**:
  - `P0` (Không tải trước): Độ trễ trung bình `1265.52 ms`, lãng phí `0.00 KB`.
  - `Markov-1` (k=1, top_m=1, th=0.30): Độ trễ giảm xuống `480.51 ms` (-62.0%), lãng phí `91.72 KB/bước`.
  - `Markov-2` (k=2, top_m=1, th=0.30): Độ trễ giảm xuống `469.86 ms` (-62.9%), lãng phí `93.04 KB/bước`.
  - `Markov-2-Top2` (k=2, top_m=2, th=0.25): Độ trễ giảm xuống `346.68 ms` (-72.6%), lãng phí `146.67 KB/bước`.
- **Hiệu quả trên kịch bản 100 phiên của ứng dụng `SocialApp` (`socialapp_sim_results.csv`)**:
  - Markov bậc 2 (`P4-Markov2`) đạt độ chính xác Top-1 là `61.50%`, vượt Markov bậc 1 (`54.75%`), giúp giảm độ trễ trên mạng EDGE từ `1027.75 ms` xuống `433.62 ms` đồng thời tiết kiệm băng thông thừa hơn bậc 1 (`43.80 KB` so với `48.20 KB`).
