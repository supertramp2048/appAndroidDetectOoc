# KẾ HOẠCH NÂNG CẤP & TRIỂN KHAI KHỐI APP (HUY) — BẢN SIẾT CHẶT KỸ THUẬT
> **Dự án:** Ứng dụng đọc tin tức & Kiểm chứng tin giả kết hợp Tải trước dự đoán (Predictive Prefetching)  
> **Tài liệu tham chiếu:** [PHÂN CHIA CÔNG VIỆC.md](file:///c:/Users/ADMIN88/AndroidStudioProjects/BtckAndroid/PHÂN%20CHIA%20CÔNG%20VIỆC.md)  
> **Người phụ trách:** Huy (Khối APP - Nhiệm vụ A1 đến A5)

---

## 🎯 Mục tiêu tổng quan
1. Chuẩn hóa luồng nạp dữ liệu toàn ứng dụng qua một điểm duy nhất (`Repository.load(screen)`), hỗ trợ chuyển đổi linh hoạt giữa **Chế độ API thật** và **Chế độ Nguồn dữ liệu giả (`SimulationMode`)**.
2. Xây dựng hệ thống tự động hóa thực nghiệm (`ReplayEngine`) an toàn bộ nhớ (không tràn Activity Back Stack) và bộ đo lường (`MetricsLogger`) tách biệt độ trễ mạng với độ trễ vẽ UI, xuất file CSV chuẩn xác.
3. Cung cấp giao diện trực quan (`PrefetchDemoOverlay`) hiển thị xuyên suốt trên mọi Activity của ứng dụng.
4. Có thể phát triển và kiểm thử độc lập 100% bằng Mock/Stub trước khi ghép nối code với Kiên và Long.

---

## 🛡️ 6 Nguyên tắc kỹ thuật cốt lõi (Đã siết chặt theo Codebase thực tế)

1. **Xử lý kiến trúc Đa Activity (`Multi-Activity`) cho Overlay & Điều hướng:**
   - App hiện có `HomeActivity` (chứa 4 tab Fragment) và các Activity rời (`PostDetailActivity`, `CommentsActivity`, `ProfileActivity`, `SettingsActivity`).
   - **Giải pháp:** Dùng `Application.ActivityLifecycleCallbacks` để tự động gắn thanh Demo Overlay vào `Window` (`android.R.id.content`) của bất kỳ Activity nào đang ở trạng thái `RESUMED`. Đồng thời nâng cấp `Navigator.java` để xử lý trọn vẹn cả lệnh chuyển Tab trong `HomeActivity` lẫn đóng/mở Activity con với cờ `FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP` nhằm chống tràn bộ nhớ khi chạy 100 phiên.
2. **Đồng bộ khóa `ScreenId` & Kích thước dữ liệu cơ sở (`BasePayloadBytes`):**
   - Bộ đoán Markov chỉ dự đoán **loại màn hình** (`ScreenId`), không đoán ID bài viết bất kỳ.
   - **Giải pháp:** Mỗi `ScreenId` được gắn một khóa định danh chuẩn, một ID mặc định phục vụ prefetch/replay (ví dụ `defaultPostId = 1`, `defaultUserId = 1`), và một **dung lượng dữ liệu chuẩn (`baseSizeBytes`)** để tính toán chính xác độ trễ mạng giả ($\text{RTT} + \text{Size}/\text{BW}$) cũng như so khớp Cache Hit/Miss 100% nhất quán.
3. **Tách biệt Chế độ Thí nghiệm (`SimulationMode`) và Chế độ Server thật:**
   - Tránh việc mạng Internet thực tế hoặc server Render ngủ đông làm nhiễu loạn số liệu thí nghiệm.
   - **Giải pháp:** Khi bật `SimulationMode = true`, `Repository.load(screen)` không gọi Internet mà đi qua `FakeDataSource` kết hợp bộ tạo trễ `NetworkSimulator` (của Long), trả về dữ liệu mẫu từ `FakeApi` sau đúng khoảng thời gian tính theo công thức mạng.
4. **Tách bạch Độ trễ dữ liệu (`latency_ms`) và Độ trễ giao diện (`ui_latency_ms`):**
   - Loại bỏ nhiễu do thời gian khởi tạo Activity của Android (~30–80ms) khỏi kết quả đánh giá thuật toán Prefetch.
   - **Giải pháp:** Ghi rõ 2 cột trong CSV:
     - `latency_ms` (Độ trễ tầng `Repository.load`): `FULL_HIT` = `0 ms`, `LATE_HIT` = phần thời gian tải còn lại, `MISS` = toàn bộ thời gian tải.
     - `ui_latency_ms`: Tổng thời gian từ lúc lệnh điều hướng phát ra đến khi UI render hoàn tất.
5. **Quy tắc tính và ghi nhận `wasted_bytes` (Byte lãng phí) chặt chẽ:**
   - Khi ở màn hình $Y$, hệ thống có thể tải trước tập màn hình $\{Z_1, Z_2\}$.
   - **Giải pháp:** Mỗi mục trong Cache có cờ `isUsed` (mặc định `false`, chuyển thành `true` khi `FULL_HIT` hoặc `LATE_HIT`). Khi người dùng chuyển sang bước mới và các mục tải trước của bước cũ bị hủy/hết hạn (evict) hoặc kết thúc phiên mà `isUsed == false`, toàn bộ số byte đã tải của các mục đó được ghi nhận vào cột `wasted_bytes`.
6. **Cơ chế Tua nhanh (`TimeScale`) & Chạy Lô tự động (`BatchRunner`) cho 72 cấu hình:**
   - Ma trận đầy đủ gồm: $6 \text{ chính sách} \times 4 \text{ mạng} \times 3 \text{ độ nặng} = 72 \text{ cấu hình}$. Nếu chạy tốc độ thực (1x) sẽ mất ~40 giờ.
   - **Giải pháp:** Hỗ trợ tham số `timeScale` (ví dụ `1x` khi quay video demo, `5x` hoặc `10x` khi chạy thu thập CSV hàng loạt — chia đồng thời cả `dwell_time_ms` và thời gian chờ mạng giả cho `timeScale` để giữ nguyên tỷ lệ toán học giữa thời gian dừng và thời gian tải) kèm chế độ `BatchRunner` tự động chạy hết danh sách cấu hình.

---

## 📋 Chi tiết các giai đoạn & Đầu việc

### Giai đoạn 1: Chuẩn hóa tầng dữ liệu & Điều hướng (Nhiệm vụ A1)
*Mục tiêu: Đưa toàn bộ việc nạp dữ liệu màn hình về hàm duy nhất `Repository.load(screen)` mà ứng dụng vẫn chạy mượt như cũ.*

- [x] **1.1. Định nghĩa `ScreenId` và cấu hình Payload chuẩn (`core/prefetch/ScreenId.java`):**
  - Các màn hình chuẩn trong luồng điều hướng:
    | `ScreenId` | Route tương ứng | Dung lượng gốc (`baseBytes`) | Tham số mặc định khi Replay/Prefetch |
    | :--- | :--- | :---: | :--- |
    | `FEED_NEWEST` | `home?tab=newest` | 120 KB | `sort="newest"` |
    | `FEED_HOT` | `home?tab=hot` | 120 KB | `sort="hot"` |
    | `FEED_FOLLOWING` | `home?tab=following` | 100 KB | `feed="following"` |
    | `SEARCH` | `search` | 60 KB | `q=""` |
    | `POST_DETAIL` | `post/{id}` | 180 KB | `postId = 1` |
    | `COMMENTS` | `post/{id}/comments` | 90 KB | `postId = 1` |
    | `PROFILE` | `profile/{userId}` | 110 KB | `userId = 1` |
    | `FACT_CHECK` | `factcheck` | 150 KB | Mặc định tab kiểm chứng |
    | `SETTINGS` | `settings` | 40 KB | Nạp thông tin cấu hình |
- [x] **1.2. Xây dựng lớp trung tâm `Repository` (`core/prefetch/Repository.java`):**
  - Cung cấp hàm chuẩn theo đúng đặc tả đề tài:
    ```java
    public static <T> void load(
        @NonNull ScreenId screen,
        @Nullable Bundle params,
        @NonNull ScreenLoadCallback<T> callback
    );
    ```
  - Bên trong `Repository.load(screen)` thực hiện đúng luồng:
    1. Kiểm tra `PrefetchCache`:
       - Nếu **đã có sẵn** $\to$ đánh dấu `entry.markUsed()`, trả kết quả ngay lập tức (`HitType.FULL_HIT`, `latency_ms = 0`).
       - Nếu **đang tải nền dở dang (`InFlightRequest`)** $\to$ gắn callback vào request đang chạy, không gửi request trùng (`HitType.LATE_HIT`, `latency_ms = thời gian chờ phần còn lại`).
       - Nếu **chưa có** $\to$ gọi nguồn dữ liệu (`HitType.MISS`, `latency_ms = tổng thời gian tải`).
    2. Sau khi màn hình $Y$ nhận dữ liệu:
       - Ghi log vào `MetricsLogger`.
       - Báo cho `MarkovPredictor` cập nhật lịch sử $[..., X, Y]$ và lấy danh sách xác suất $\{Z_1, Z_2, ...\}$.
       - Báo cho `PrefetchPolicy` và `PrefetchManager` tải nền các màn hình được chọn.
- [x] **1.3. Nâng cấp `Navigator.java` và Refactor các ViewModel/Repository hiện có:**
  - Sửa `Navigator.java` để điều hướng được vào các tab của `HomeActivity` (`home`, `search`, `factcheck`, `profile`) và quản lý cờ Intent tránh chồng chất Activity.
  - Kết nối `FeedRepository`, `PostDetailRepository`, `CommentsRepository`, `ProfileRepository`, `SettingsRepository` đi qua `Repository.load(screen)`.
  - **Tiêu chí hoàn thành (Done A1):** App build thành công, chuyển qua lại mọi màn hình chạy như cũ, logcat in ra rõ mỗi lần gọi `Repository.load(screen)`.

---

### Giai đoạn 2: Hệ thống đo lường & Xuất Log CSV (Nhiệm vụ A2)
*Mục tiêu: Ghi nhận số liệu thực nghiệm chính xác ra CSV và có script kéo tự động về máy tính.*

- [x] **2.1. Xây dựng `MetricsLogger` (`core/prefetch/MetricsLogger.java`):**
  - Ghi file vào thư mục: `/sdcard/Android/data/com.socialapp/files/metrics/` (không cần quyền root khi pull bằng ADB).
  - Tên file tự động theo cấu hình: `metrics_<policy>_<network>_<scale>.csv` (hoặc `metrics_all.csv`).
  - **Cấu trúc cột CSV chuẩn:**
    ```csv
    session_id,step_index,from_screen,to_screen,hit_type,latency_ms,ui_latency_ms,fetched_bytes,wasted_bytes,policy,network_profile,payload_scale,timestamp
    ```
- [x] **2.2. Cơ chế tính toán chỉ số chính xác:**
  - `latency_ms`: Đo bằng `SystemClock.elapsedRealtime()` ngay tại đầu và cuối hàm `Repository.load(screen)` (nếu nhân hệ số tua nhanh `timeScale` thì quy đổi lại về thời gian chuẩn để số liệu không bị méo).
  - `wasted_bytes`: Thu hồi và cộng tổng số byte của các bản ghi trong `PrefetchCache` được tải ở các bước trước nhưng bị loại bỏ/hết hạn hoặc kết thúc phiên mà `isUsed == false`.
- [x] **2.3. Viết Script kéo file tự động (`scripts/pull_metrics.ps1` và `scripts/pull_metrics.sh`):**
  - Tự động tạo thư mục `./experiment_results/` trên máy tính và chạy lệnh `adb pull` lấy toàn bộ file CSV về máy.
  - **Tiêu chí hoàn thành (Done A2):** Chạy thử 1 phiên trên máy ảo, chạy script thấy file CSV xuất hiện trên PC với đầy đủ các cột và số liệu hợp lệ.

---

### Giai đoạn 3: Thanh hiển thị Demo toàn cục (Nhiệm vụ A4)
*Mục tiêu: Hiển thị trực quan trạng thái dự đoán Markov và Prefetch trên mọi màn hình của ứng dụng.*

- [x] **3.1. Xây dựng `GlobalDemoOverlayManager` gắn vào `MyApplication`:**
  - Đăng ký `registerActivityLifecycleCallbacks` trong `MyApplication.java`.
  - Khi bất kỳ Activity nào `onActivityResumed`, tự động gắn (attach) thanh `PrefetchDemoOverlayView` nổi ở góc trên hoặc dưới màn hình mà không cần sửa từng file XML của từng Activity.
- [x] **3.2. Các thông tin hiển thị trên Thanh Demo:**
  - **Dòng 1 (Cấu hình & Màn hiện tại):** `[Policy: P5 | Net: LTE x1.0] Màn hiện tại: POST_DETAIL`
  - **Dòng 2 (Dự đoán Markov tiếp theo):** `Dự đoán: COMMENTS (62%) | FEED_NEWEST (25%)`
  - **Dòng 3 (Trạng thái Prefetch & Cache):** `Prefetch: [COMMENTS: READY ⚡] | Bước vừa rồi: FULL_HIT (0 ms)`
  - **Dòng 4 (Thống kê lũy kế & Nút điều khiển nhanh):** `Đúng/Tổng: 18/24 (75.0%) | Lãng phí: 240 KB` + Nút `[⚙ Cấu hình]`.
  - **Tiêu chí hoàn thành (Done A4):** Bấm chuyển qua `HomeActivity`, `PostDetailActivity`, `CommentsActivity`, `ProfileActivity` đều thấy thanh Demo hiện rõ và cập nhật tức thì.

---

### Giai đoạn 4: Bộ tự động điều hướng Replay Engine (Nhiệm vụ A3)
*Mục tiêu: Đọc kịch bản hành vi, tự động bấm qua các màn hình đúng thứ tự và thời gian dừng, chạy 100 phiên không lỗi.*

- [x] **4.1. Định dạng file kịch bản chuẩn (`assets/replay_sessions.json`):**
  - Hỗ trợ đọc cả định dạng JSON lẫn CSV do Kiên (B1) xuất ra:
    ```json
    {
      "sessions": [
        {
          "session_id": 1,
          "steps": [
            { "screen": "FEED_NEWEST", "dwell_ms": 2000 },
            { "screen": "POST_DETAIL", "dwell_ms": 2500 },
            { "screen": "COMMENTS", "dwell_ms": 1800 },
            { "screen": "FEED_NEWEST", "dwell_ms": 1500 },
            { "screen": "PROFILE", "dwell_ms": 2000 }
          ]
        }
      ]
    }
    ```
  - Sinh sẵn file mẫu `mock_replay_100_sessions.json` (100 phiên) trong `app/src/main/assets/` để Huy test độc lập ngay trong tuần 1–2.
- [x] **4.2. Xây dựng `ReplayEngine` (`core/prefetch/ReplayEngine.java`):**
  - Chạy trên `Handler(Looper.getMainLooper())` kết hợp trạng thái hoàn tất của `Repository.load(screen)`:
    1. Mở màn hình `step.screen` thông qua `Navigator`.
    2. Đợi `Repository.load` trả kết quả xong **VÀ** đợi hết thời gian dừng `dwell_ms / timeScale`.
    3. Chuyển sang bước tiếp theo; hết 1 phiên thì dọn dẹp Activity con (quay về `HomeActivity`), chốt `wasted_bytes` cuối phiên vào CSV, rồi sang phiên kế tiếp.
  - Xử lý an toàn bộ nhớ: Tự động đóng các Activity chi tiết cũ trước khi mở Activity mới, thu hồi Glide image cache định kỳ sau mỗi 20 phiên để chạy 100 phiên liên tục không bao giờ bị `OutOfMemoryError`.
  - **Tiêu chí hoàn thành (Done A3):** Bật Replay chạy hết 100 phiên liên tiếp không crash, log ghi đủ 100 phiên vào CSV.

---

### Giai đoạn 5: Thực nghiệm Ma trận Cấu hình & Quay Video Demo (Nhiệm vụ A5)
*Mục tiêu: Ghép nối hoàn chỉnh với Kiên & Long, xuất toàn bộ CSV và quay video báo cáo.*

- [x] **5.1. Khớp nối Module thật của Kiên (B2) và Long (C1–C5):**
  - Xây dựng sẵn `MarkovPredictor` (bậc 1, bậc 2) và bộ `NetworkSimulator`, `PrefetchCache`, `PrefetchPolicy (P0..P5)` chạy độc lập trơn tru ngay trong App, sẵn sàng thay thế/ghép nối khi Kiên & Long bàn giao code.
- [x] **5.2. Chạy tự động các tổ hợp thí nghiệm (`BatchExperimentRunner`):**
  - Đã tích hợp `BatchExperimentRunner` chạy tự động ma trận 16 cấu hình (P0, P1, P3, P5 x 4 Mạng WiFi/LTE/3G/EDGE).
- [x] **5.3. Thu thập kết quả & Hướng dẫn Quay Video Demo:**
  - Script `scripts/pull_metrics.ps1` và `scripts/pull_metrics.sh` đã sẵn sàng kéo CSV về máy tính.
  - Hướng dẫn chạy thực nghiệm và quay video demo đã hoàn tất.

---

## 🤝 Hợp đồng Giao diện (Interfaces) chốt với Kiên & Long

```java
// 1. Giao diện cho Bộ dự đoán Markov (Kiên - B2)
public interface IMarkovPredictor {
    void recordTransition(@NonNull ScreenId currentScreen);
    @NonNull Map<ScreenId, Double> predictNextScreens(int order); // order = 1 hoặc 2
    void resetSession();
}

// 2. Giao diện cho Mạng giả & Bộ tải trước (Long - C1..C5)
public interface IPrefetchEngine {
    void setConfig(@NonNull PolicyType policy, @NonNull NetworkProfile profile, float payloadScale);
    void loadScreen(@NonNull ScreenId screen, @NonNull LoadResultCallback callback);
    void triggerPrefetch(@NonNull Map<ScreenId, Double> predictions);
    long flushUnusedWastedBytes();
}
```
