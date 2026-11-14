# HƯỚNG DẪN KIỂM THỬ TOÀN BỘ CÁC TASK (TASK C1 ĐẾN TASK C5)
**Dự án:** Ứng dụng Mạng Xã Hội Android (`com.socialapp`) — Tối ưu hóa hiệu năng bằng cơ chế Tải trước dữ liệu (Prefetching) dựa trên Markov & Đánh đổi Pareto  
**Phân công kỹ thuật:** **LONG (Task C1 - C5)**  
**Đặc tả gốc tham chiếu:** [PREFETCH_SPEC_LONG.md](file:///c:/Users/ADMIN/Desktop/Bai%20tap%20lon/appAndroidDetectOoc/PREFETCH_SPEC_LONG.md) & [DEVTOOL_REQUIREMENTS.md](file:///c:/Users/ADMIN/Desktop/Bai%20tap%20lon/appAndroidDetectOoc/DEVTOOL_REQUIREMENTS.md)  
**Ngày cập nhật:** Tháng 11/2026  
**Trạng thái kiểm thử tự động:** `19/19 Unit Tests PASSED` (100% Thành công)

---

## MỤC LỤC
1. [Tổng quan kiến trúc & Bản đồ các Task](#1-tổng-quan-kiến-trúc--bản-đồ-các-task)
2. [Cách chuẩn bị và khởi chạy môi trường kiểm thử](#2-cách-chuẩn-bị-và-khởi-chạy-môi-trường-kiểm-thử)
3. [Hướng dẫn test Task C1: Giả lập mạng tất định (4 Profiles & 3 Payload Weights)](#3-hướng-dẫn-test-task-c1-giả-lập-mạng-tất-định-4-profiles--3-payload-weights)
4. [Hướng dẫn test Task C2: Tranh chấp & Chia sẻ băng thông đồng thời (Fluid-Flow Sharing)](#4-hướng-dẫn-test-task-c2-tranh-chấp--chia-sẻ-băng-thông-đồng-thời-fluid-flow-sharing)
5. [Hướng dẫn test Task C3: Bộ nhớ đệm RAM PrefetchCache & Đo lường Wasted Bytes](#5-hướng-dẫn-test-task-c3-bộ-nhớ-đệm-ram-prefetchcache--đo-lường-wasted-bytes)
6. [Hướng dẫn test Task C4: PrefetchManager, Xử lý Late-Hit & Tích hợp vào App](#6-hướng-dẫn-test-task-c4-prefetchmanager-xử-lý-late-hit--tích-hợp-vào-app)
7. [Hướng dẫn test Task C5: Bộ 6 Chính sách Prefetch (P0 - P5) & PrefetchRuntimeConfig](#7-hướng-dẫn-test-task-c5-bộ-6-chính-sách-prefetch-p0---p5--prefetchruntimeconfig)
8. [Quy trình chạy kiểm thử tự động toàn diện qua Gradle CLI](#8-quy-trình-chạy-kiểm-thử-tự-động-toàn-diện-qua-gradle-cli)
9. [Bảng tra cứu số liệu lý thuyết vs thực tế để đối chiếu](#9-bảng-tra-cứu-số-liệu-lý-thuyết-vs-thực-tế-để-đối-chiếu)

---

## 1. TỔNG QUAN KIẾN TRÚC & BẢN ĐỒ CÁC TASK

Toàn bộ phần việc của Long trong đề tài được tổ chức theo 2 package cốt lõi:

```text
app/src/main/java/com/socialapp/core/
├── network/sim/                              <--- [TASK C1 & C2: KHỐI GIẢ LẬP MẠNG]
│   ├── NetworkProfile.java                   (4 profiles: WIFI, LTE_4G, NETWORK_3G, EDGE_2G)
│   ├── PayloadWeight.java                    (3 weights: LIGHT_0_5, NORMAL_1_0, HEAVY_2_0)
│   ├── ScreenPayloadConfig.java              (Kích thước chuẩn từng màn hình: HOME 120KB, POST_DETAIL 250KB,...)
│   ├── SimulatedNetworkResponse.java         (Kết quả đo: totalLatency, rtt, transferTime, effectiveBw)
│   ├── MockNetworkEngine.java                (Bộ máy tính trễ toán học tất định, chia sẻ BW N request)
│   ├── MockNetworkInterceptor.java           (OkHttp Interceptor gắn vào Retrofit chain)
│   └── MockNetworkLabActivity.java           (Developer Workbench UI để kiểm thử tương tác)
│
└── prefetch/                                 <--- [TASK C3, C4, C5: KHỐI TẢI TRƯỚC DỮ LIỆU]
    ├── PrefetchCache.java                    [TASK C3] (RAM Cache LRU 20 item, TTL, isUsed, Wasted Bytes)
    ├── PrefetchEntry.java                    [TASK C3] (Metadata gói tin trong RAM)
    ├── PrefetchHitType.java                  [TASK C4] (FULL_HIT, LATE_HIT, MISS)
    ├── PrefetchResult.java                   [TASK C4] (Dữ liệu trả về, waitMs, savedMs, hitType)
    ├── IPrefetchTelemetryListener.java       [TASK C4] (Giao diện lắng nghe sự kiện prefetch)
    ├── PrefetchManager.java                  [TASK C4] (Quản lý In-flight requests, nối luồng Late-Hit)
    ├── PrefetchRuntimeConfig.java            [TASK C5] (Điều khiển 1 dòng, áp dụng kịch bản Replay batch)
    │
    └── policy/                               [TASK C5: BỘ 6 CHÍNH SÁCH PREFETCH]
        ├── IPrefetchPolicy.java              (Interface hợp đồng chính sách)
        ├── PolicyType.java                   (Enum P0, P1, P2, P3, P4, P5)
        ├── PrefetchPolicyFactory.java        (Factory khởi tạo)
        ├── P0_NoPrefetchPolicy.java          (P0: Đối chứng baseline, không nạp trước)
        ├── P1_PrefetchAllPolicy.java         (P1: Greedy, nạp tất cả màn kế cận)
        ├── P2_MostFrequentPolicy.java        (P2: Nạp màn hình có xác suất chuyển dịch cao nhất Top-1)
        ├── P3_MarkovOrder1Policy.java        (P3: Markov bậc 1 lọc theo ngưỡng theta + Online learning)
        ├── P4_MarkovOrder2Policy.java        (P4: Markov bậc 2 chuỗi 2 bước + Katz Backoff)
        ├── P5_NetworkCostAdaptivePolicy.java (P5: Hàm thỏa dụng kinh tế Pareto thích ứng chi phí mạng)
        └── markov/
            ├── MarkovTransitionMatrix.java   (Ma trận chuyển dịch bậc 1)
            ├── MarkovTransitionTensor.java   (Tensor chuyển dịch bậc 2)
            └── ScreenProbability.java        (Cặp giá trị màn hình - xác suất)
```

---

## 2. CÁCH CHUẨN BỊ VÀ KHỞI CHẠY MÔI TRƯỜNG KIỂM THỬ

### 2.1. Yêu cầu môi trường
* Hệ điều hành: Windows (hoặc macOS/Linux).
* JDK: Java 17 hoặc Java 21 (đã cấu hình trong `local.properties` hoặc biến môi trường `JAVA_HOME`).
* Android SDK: API Level 34.
* Thiết bị: Máy ảo Android Emulator (Pixel 6 / 7, API 33-34) hoặc thiết bị thật cắm qua USB Debugging.

### 2.2. Biên dịch và cài đặt APK
Mở terminal PowerShell tại thư mục gốc dự án:
```powershell
# 1. Biên dịch file APK bản Debug
.\gradlew.bat assembleDebug

# 2. Cài đặt trực tiếp lên thiết bị/máy ảo đang kết nối
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 2.3. Ba cách truy cập giao diện kiểm thử Developer Workbench (`MockNetworkLabActivity`)
* **Cách 1 (Nhanh nhất - Lệnh ADB):**
  ```powershell
  adb shell am start -n com.socialapp/com.socialapp.core.network.sim.MockNetworkLabActivity
  ```
* **Cách 2 (Thao tác trên giao diện App):**
  1. Mở ứng dụng `SocialApp`.
  2. Tại thanh điều hướng đáy (Bottom Navigation), bấm vào tab **Cá nhân** (Profile).
  3. Bấm vào icon **Cài đặt** (Bánh răng ở góc trên bên phải).
  4. Bấm vào mục **Phòng Lab Mạng Giả Lập & Prefetch** (`item_mock_network_lab`).
* **Cách 3 (Kiểm thử dòng lệnh thuần không cần màn hình):** Chạy Unit Test suite của Gradle.

---

## 3. HƯỚNG DẪN TEST TASK C1: GIẢ LẬP MẠNG TẤT ĐỊNH (4 PROFILES & 3 PAYLOAD WEIGHTS)

### 3.1. Mục tiêu kiểm chứng
* Công thức tính toán độ trễ toán học tất định:
  $$\text{Latency (ms)} = \text{RTT} + \frac{\text{BasePayloadSize (KB)} \times \text{WeightFactor}}{\text{EffectiveBandwidth (KB/s)}} \times 1000$$
* Đảm bảo tính lặp lại 100% trong 100 lần chạy (phục vụ tái hiện thí nghiệm khoa học cho bài báo/báo cáo lớn).
* 4 Profile mạng chuẩn:
  * **Wi-Fi:** RTT = 15 ms, BW = 6,250 KB/s (50 Mbps).
  * **LTE (4G):** RTT = 50 ms, BW = 2,500 KB/s (20 Mbps).
  * **3G:** RTT = 180 ms, BW = 250 KB/s (2 Mbps).
  * **EDGE (2G):** RTT = 400 ms, BW = 20 KB/s (160 Kbps).
* 3 Hệ số độ nặng:
  * **Nhẹ (0.5x):** Giảm dung lượng 50%.
  * **Chuẩn (1.0x):** Dung lượng gốc (`HOME`: 120 KB, `POST_DETAIL`: 250 KB, `COMMENTS`: 80 KB, `PROFILE`: 60 KB).
  * **Nặng (2.0x):** Gấp đôi dung lượng (200%).

### 3.2. Quy trình test tương tác trên Giao diện Workbench (`MockNetworkLabActivity`)
1. **Bật giả lập:** Kiểm tra Switch `switch_enable_simulation` đang ở trạng thái **ON**.
2. **Kịch bản 1: Mạng Wi-Fi (Tốc độ cao)**
   * Tại mục **1. CẤU HÌNH MẠNG**: Chọn Radio `Wi-Fi (50 Mbps · RTT 15ms · 6,250 KB/s)` (`radio_profile_wifi`).
   * Tại mục **2. HỆ SỐ TẢI NẶNG PAYLOAD**: Chọn `Chuẩn (1.0x)` (`radio_weight_normal`).
   * Bấm nút **HOME (120KB)** (`btn_test_home`).
   * **Kết quả quan sát trên thẻ trạng thái:**
     * Badge `text_status_badge`: Chuyển sang xanh lá `HOÀN TẤT`.
     * Độ trễ `text_latency_result`: **34 ms** (Tính toán: $15\text{ms} + \frac{120}{6250} \times 1000 = 15 + 19.2 = 34.2\text{ ms}$).
     * Phân rã `text_breakdown`: `Phân rã: RTT 15ms + Truyền tải 19ms`.
     * Gói tin `text_payload_info`: `Gói tin payload: 120.0 KB`.
3. **Kịch bản 2: Mạng 3G + Tải Nặng (2.0x)**
   * Chọn Radio `3G (2 Mbps · RTT 180ms · 250 KB/s)` (`radio_profile_3g`).
   * Chọn Radio `Nặng (2.0x)` (`radio_weight_heavy`).
   * Bấm nút **POSTDETAIL (250KB)** (`btn_test_post_detail`).
   * **Kết quả quan sát:**
     * Màn hình hiện ProgressBar xoay trong ~2.18 giây.
     * Độ trễ `text_latency_result`: **2180 ms** (Tính toán: Payload thực tế = $250 \times 2.0 = 500\text{ KB}$. Truyền tải = $\frac{500}{250} = 2.0\text{s} = 2000\text{ ms}$. Tổng = $180 + 2000 = 2180\text{ ms}$).
     * Phân rã `text_breakdown`: `Phân rã: RTT 180ms + Truyền tải 2000ms`.
4. **Kịch bản 3: Tắt giả lập (Bypass / 0ms)**
   * Gạt Switch `Bật Giả lập Trễ Mạng` sang **OFF**.
   * Bấm bất kỳ nút nào (`btn_test_home`, `btn_test_post_detail`).
   * **Kết quả:** Phản hồi ngay lập tức trong 0 ms - 2 ms.

### 3.3. Lệnh chạy Unit Test tự động cho Task C1
```powershell
.\gradlew.bat testDebugUnitTest --tests com.socialapp.core.network.sim.MockNetworkEngineTest.testNetworkProfileTheoreticalLatency
.\gradlew.bat testDebugUnitTest --tests com.socialapp.core.network.sim.MockNetworkEngineTest.testSingleRequestExecutionWithDelay
.\gradlew.bat testDebugUnitTest --tests com.socialapp.core.network.sim.MockNetworkEngineTest.testSimulationDisabledCompletesInstantly
```

---

## 4. HƯỚNG DẪN TEST TASK C2: TRANH CHẤP & CHIA SẺ BĂNG THÔNG ĐỒNG THỜI (FLUID-FLOW SHARING)

### 4.1. Mục tiêu kiểm chứng
* Khi có $N$ tác vụ mạng diễn ra cùng một thời điểm (ví dụ: người dùng đang tải bài viết Home, cùng lúc hệ thống kích hoạt tải trước dữ liệu PostDetail ở background), tổng băng thông vật lý không thể tăng lên mà bị chia sẻ đều:
  $$\text{EffectiveBandwidth}_i = \frac{\text{TotalBandwidth}}{N}$$
* Đảm bảo tính toán đúng thời gian truyền tải khi băng thông bị chia nửa ($N=2$).

### 4.2. Quy trình test trên Giao diện Workbench
1. Tại mục cấu hình mạng: Chọn **LTE 4G** (Băng thông gốc: $2,500\text{ KB/s}$, RTT: $50\text{ ms}$).
2. Hệ số tải: Chọn **Chuẩn (1.0x)**.
3. Bấm nút màu xanh dương:  
   **⚡ Nạp 2 Request Đồng Thời (Test Chia Sẻ Băng Thông N=2)** (`btn_test_concurrent`).
4. **Phân tích cơ chế chạy thực tế:**
   * Hệ thống phát 2 request bất đồng bộ:
     * Request 1: `SCREEN_HOME` ($120\text{ KB}$)
     * Request 2: `SCREEN_POST_DETAIL` ($250\text{ KB}$)
   * Băng thông hiệu dụng khả dụng cho mỗi request bị giảm còn:
     $$\text{EffectiveBW} = \frac{2500}{2} = 1,250\text{ KB/s}$$
   * Thời gian truyền tải lý thuyết:
     * Request 1: $\frac{120}{1250} = 0.096\text{s} = 96\text{ ms} \implies \text{Tổng trễ} \approx 50 + 96 = 146\text{ ms}$.
     * Request 2: $\frac{250}{1250} = 0.200\text{s} = 200\text{ ms} \implies \text{Tổng trễ} \approx 50 + 200 = 250\text{ ms}$.
5. **Kết quả quan sát trên thẻ đo đạc:**
   * Badge hiển thị: `HOÀN TẤT CẢ 2`.
   * Text kết quả `text_latency_result`: Hiển thị rõ trễ của cả 2 request: `Req 1: ~146ms | Req 2: ~250ms`.
   * Text chia sẻ băng thông `text_bandwidth_concurrency`:  
     `Chia đôi băng thông: R1: 1250.0 KB/s | R2: 1250.0 KB/s (N=2)`.

### 4.3. Lệnh chạy Unit Test tự động cho Task C2
```powershell
.\gradlew.bat testDebugUnitTest --tests com.socialapp.core.network.sim.MockNetworkEngineTest.testBandwidthSharingCalculation
```

---

## 5. HƯỚNG DẪN TEST TASK C3: BỘ NHỚ ĐỆM RAM PREFETCHCACHE & ĐO LƯỜNG WASTED BYTES

### 5.1. Mục tiêu kiểm chứng
* Lưu trữ dữ liệu tải trước hoàn toàn trên Heap RAM Android (không ghi I/O disk làm nghẽn bộ nhớ flash).
* Giới hạn dung lượng tối đa 20 phần tử, cơ chế trục xuất **LRU (Least Recently Used)** thông qua `LinkedHashMap(16, 0.75f, true)`.
* Quản lý vòng đời TTL (Time-To-Live, mặc định 60 giây).
* **Đo lường Wasted Bytes chuẩn xác:**
  * Nếu phần tử hết hạn (expired) hoặc bị LRU đẩy ra mà cờ `isUsed == false`: Ghi nhận toàn bộ kích thước vào biến lũy kế `wastedBytes` (phục vụ vẽ biểu đồ biên Pareto).
  * Nếu phần tử đã được người dùng tiêu thụ (`isUsed == true`): Khi bị dọn dẹp, **tuyệt đối không tính vào wastedBytes** mà tính vào `usefulBytes`.

### 5.2. Quy trình test trên Workbench
1. **Kiểm tra trạng thái ban đầu:**
   * Bấm nút **🔄 Reset Thống Kê** (`btn_sim_reset_stats`).
   * Xem mục `RAM Cache: 0 mục | Đang bay dở (In-flight): 0`.
   * `Băng thông lãng phí (Wasted): 0.0 KB | Hữu ích: 0.0 KB`.
2. **Tạo dữ liệu tải trước:**
   * Chọn chính sách `P2: Mặc định phổ biến nhất` (`radio_policy_p2`).
   * Bấm nút **1. Mở HOME (Kích hoạt Âm Thầm Nạp Trước)** (`btn_sim_visit_home`).
   * Chờ 1 giây để request nạp ngầm hoàn tất vào RAM.
   * Quan sát dòng trạng thái: `RAM Cache: 1 mục`.
3. **Kịch bản A: Người dùng KHÔNG dùng (Dẫn đến Lãng Phí - Wasted Bytes)**
   * Người dùng không bấm vào nút PostDetail mà chuyển đi hướng khác hoặc bấm nút **🗑️ Dọn dẹp Cache (Đo Lãng Phí)** (`btn_sim_cleanup_wasted`).
   * Sau khi dọn dẹp hoặc hết hạn TTL, xem chỉ số `text_prefetch_wasted_info`:
     * Băng thông lãng phí tăng chính xác thêm **250.0 KB**.
4. **Kịch bản B: Người dùng CÓ dùng (Hữu ích - Useful Bytes)**
   * Bấm lại **1. Mở HOME**, chờ nạp xong vào RAM.
   * Bấm tiếp **2. Bấm vào POSTDETAIL** (`btn_sim_acquire_post`).
   * Badge chuyển sang `FULL_HIT (0ms)`. Cờ `isUsed` được bật lên `true`.
   * Bấm nút **🗑️ Dọn dẹp Cache**:
     * Mục lãng phí giữ nguyên, mục hữu ích tăng thêm **250.0 KB**.

### 5.3. Lệnh chạy Unit Test tự động cho Task C3
```powershell
.\gradlew.bat testDebugUnitTest --tests com.socialapp.core.prefetch.PrefetchEngineTest.testWastedBytes_RecordedWhenUnusedEntryExpires
.\gradlew.bat testDebugUnitTest --tests com.socialapp.core.prefetch.PrefetchEngineTest.testUsefulBytes_NoWasteWhenEntryWasUsed
.\gradlew.bat testDebugUnitTest --tests com.socialapp.core.prefetch.PrefetchEngineTest.testLruEviction_EnforcesCapacityLimit
```

---

## 6. HƯỚNG DẪN TEST TASK C4: PREFETCHMANAGER, XỬ LÝ LATE-HIT & TÍCH HỢP VÀO APP

### 6.1. Mục tiêu kiểm chứng
Kiểm chứng 3 trạng thái của hàm `PrefetchManager.acquire(...)`:
1. **`FULL_HIT`:** Dữ liệu đã nằm sẵn trong `PrefetchCache` $\implies$ Trả về ngay lập tức, `waitMs == 0`, tiết kiệm trọn vẹn 100% thời gian tải mạng.
2. **`LATE_HIT` (Điểm mấu chốt của đề tài):**
   * Người dùng bấm vào màn hình đích đúng lúc tác vụ tải trước đang bay dở trên mạng (`inFlightRequests`).
   * **Bản chất thực thi:** Không được hủy, không được gửi trùng request thứ 2. Hệ thống gắn trực tiếp `CompletableFuture.thenAccept` vào tác vụ đang bay dở.
   * `waitMs`: Chỉ bằng phần thời gian còn lại chưa tải xong.
   * `savedMs`: Bằng phần thời gian mà request nền đã tải trước đó.
3. **`MISS`:** Chưa có trong Cache và cũng không có tác vụ nào đang bay $\implies$ Tải mạng bình thường.

### 6.2. Quy trình test tương tác trên Giao diện Workbench
1. **Test LATE_HIT (Cực kỳ trực quan):**
   * Chọn cấu hình mạng: **3G (2 Mbps)** (để có độ trễ đủ dài ~1.18 giây cho thao tác người dùng).
   * Bấm nút **1. Mở HOME (Kích hoạt Âm Thầm Nạp Trước)** (`btn_sim_visit_home`).
   * **Ngay lập tức trong vòng 0.3 giây**, bấm liền nút **2. Bấm vào POSTDETAIL (Kiểm tra FULL_HIT / LATE_HIT)** (`btn_sim_acquire_post`).
   * **Kết quả quan sát:**
     * Badge `text_prefetch_badge`: Hiện màu vàng cam **`LATE_HIT (Bắt nối)`**.
     * Thời gian chờ `text_prefetch_result_title`: ~700 ms - 900 ms (thay vì 1180 ms).
     * Thời gian tiết kiệm `text_prefetch_time_saved`: Hiện số ms đã tiết kiệm được (~300 - 400 ms).
     * Bảng thống kê `text_prefetch_hit_breakdown`: Bộ đếm `Late: 1` tăng lên.
2. **Test FULL_HIT:**
   * Bấm lại nút **1. Mở HOME**.
   * Chờ 2 giây (đảm bảo request ngầm hoàn tất tải về RAM).
   * Bấm nút **2. Bấm vào POSTDETAIL**.
   * **Kết quả quan sát:**
     * Badge `text_prefetch_badge`: Hiện màu xanh lá **`FULL_HIT (0ms)`**.
     * Thời gian chờ: **0 ms (Tức thì!)**.
     * Thời gian tiết kiệm: **1180 ms** (toàn bộ thời gian mạng của 3G).
     * Bộ đếm `Full: 1` tăng lên.
3. **Test MISS:**
   * Bấm **🔄 Reset Thống Kê**.
   * Không bấm nút (1), mà bấm thẳng vào nút **2. Bấm vào POSTDETAIL**.
   * **Kết quả:** Badge đỏ **`MISS (Hụt)`**, thời gian chờ bằng đủ trễ mạng (1180 ms), tiết kiệm: 0 ms.

### 6.3. Kiểm thử trên luồng người dùng thật của App (`HomeActivity` $\to$ `PostDetailActivity`)
1. Từ màn hình chính ứng dụng (`HomeActivity`), hệ thống tự động gọi ngầm:
   `PrefetchManager.getInstance().onScreenVisited(ScreenPayloadConfig.SCREEN_HOME);`
2. Người dùng cuộn xem bài viết và chạm vào một bài viết bất kỳ trên Feed.
3. `PostDetailRepository.getPostDetail(postId)` được kích hoạt, gọi hàm:
   `prefetchManager.acquire(SCREEN_POST_DETAIL, "post_" + postId, ...)`
4. Nếu bài viết đã được prefetch: Màn hình chi tiết bài viết `PostDetailActivity` hiển thị dữ liệu ngay lập tức với Shimmer loading biến mất sau 0 ms!
5. Kiểm tra logcat bằng lệnh:
   ```powershell
   adb logcat -s PrefetchManager:D PrefetchCache:D MockNetworkEngine:D
   ```

### 6.4. Lệnh chạy Unit Test tự động cho Task C4
```powershell
.\gradlew.bat testDebugUnitTest --tests com.socialapp.core.prefetch.PrefetchEngineTest.testFullHit_InstantReturnWithZeroWait
.\gradlew.bat testDebugUnitTest --tests com.socialapp.core.prefetch.PrefetchEngineTest.testLateHit_AttachesToInFlightTask
.\gradlew.bat testDebugUnitTest --tests com.socialapp.core.prefetch.PrefetchEngineTest.testMiss_TriggersStandardNetworkLoad
```

---

## 7. HƯỚNG DẪN TEST TASK C5: BỘ 6 CHÍNH SÁCH PREFETCH (P0 - P5) & PREFETCHRUNTIMECONFIG

### 7.1. Danh mục 6 chính sách và nguyên lý hoạt động
| Ký hiệu | Tên chính sách | Class cài đặt | Hành vi quyết định nạp |
| :---: | :--- | :--- | :--- |
| **$P_0$** | **No Prefetch** | `P0_NoPrefetchPolicy.java` | Không nạp gì cả ($\emptyset$). Làm mốc chuẩn (baseline) để so sánh. |
| **$P_1$** | **Prefetch All** | `P1_PrefetchAllPolicy.java` | Nạp tất cả màn kế cận trong đồ thị. Trúng tối đa nhưng lãng phí cực lớn. |
| **$P_2$** | **Most Frequent** | `P2_MostFrequentPolicy.java` | Chỉ nạp duy nhất 1 màn hình có xác suất chuyển dịch cao nhất (Top-1). |
| **$P_3$** | **Markov Order 1** | `P3_MarkovOrder1Policy.java` | Dựa vào ma trận chuyển dịch bậc 1 $P(S_{t+1} \mid S_t) \ge \theta$. Hỗ trợ học trực tuyến. |
| **$P_4$** | **Markov Order 2** | `P4_MarkovOrder2Policy.java` | Dựa vào chuỗi 2 bước $P(S_{t+1} \mid S_{t-1}, S_t)$ + Cơ chế lui bước **Katz Backoff** về bậc 1. |
| **$P_5$** | **Network-Adaptive**| `P5_NetworkCostAdaptivePolicy.java` | Hàm thỏa dụng Pareto thích ứng băng thông và chi phí mạng. |

### 7.2. Chi tiết công thức Thỏa dụng Kinh tế của Chính sách $P_5$
$$U(S_j) = P_j \times T_{\text{Saved}}(S_j) - \lambda \times (1.0 - P_j) \times \text{TxTimeMs}(S_j) \times \text{CostFactor}(\text{Profile})$$
* **Hệ số chi phí mạng (`CostFactor`):**
  * `Wi-Fi`: $0.15$ (Băng thông dồi dào $\implies$ Chi phí rẻ $\implies U > 0 \implies$ Ưu tiên nạp để tăng tốc độ).
  * `LTE 4G`: $1.0$ (Tiêu chuẩn cân bằng).
  * `3G`: $3.5$ (Băng thông hạn chế).
  * `EDGE 2G`: $12.0$ (Băng thông cực nghẽn $\implies$ Chi phí lãng phí rất đắt $\implies U < 0 \implies$ Chặn nạp các màn hình nặng để không làm nghẽn đường truyền của người dùng).

### 7.3. Quy trình test trên Workbench
1. **Kiểm tra chuyển đổi 1-chạm giữa 6 chính sách:**
   * Tại mục **4. CHÍNH SÁCH NẠP TRƯỚC (PREFETCH POLICIES)**:
     * Chọn lần lượt: `P0`, `P1`, `P2`, `P3`, `P4`, `P5`.
     * Nhãn bên dưới `text_prefetch_policy_label` cập nhật tức thì: `[P0]`, `[P1]`,..., `[P5]`.
2. **Kiểm tra tính thông minh thích ứng mạng của $P_5$:**
   * **Bước 1 (Trên Wi-Fi):**
     * Chọn mạng: **Wi-Fi**. Chọn chính sách: **P5**.
     * Bấm **1. Mở HOME**: Hệ thống quyết định nạp ngầm `POST_DETAIL` vì $U > 0$.
   * **Bước 2 (Trên EDGE 2G):**
     * Đổi sang mạng: **EDGE 2G**.
     * Bấm **1. Mở HOME**: Hệ thống tính toán chi phí phạt $12.0 \times \text{TxTime} > \text{Lợi ích} \implies U < 0 \implies$ **Tự động ngăn chặn không nạp** để bảo vệ băng thông 2G.

### 7.4. Kiểm tra cấu hình kịch bản Replay tự động (`PrefetchRuntimeConfig`)
Module hỗ trợ nạp chuỗi cấu hình thí nghiệm phục vụ Replay Runner của nhóm:
```java
PrefetchRuntimeConfig.getInstance().applyScenario(
    "POLICY=P5;NETWORK=3G;WEIGHT=HEAVY;PREFETCH=TRUE;LATE_HIT=TRUE;THETA=0.65;LAMBDA=1.5"
);
```

### 7.5. Lệnh chạy Unit Test tự động cho Task C5
```powershell
.\gradlew.bat testDebugUnitTest --tests com.socialapp.core.prefetch.PolicySuiteTest.testP0_NoPrefetchPolicy_AlwaysEmpty
.\gradlew.bat testDebugUnitTest --tests com.socialapp.core.prefetch.PolicySuiteTest.testP1_PrefetchAllPolicy_ReturnsAllAdjacentScreens
.\gradlew.bat testDebugUnitTest --tests com.socialapp.core.prefetch.PolicySuiteTest.testP2_MostFrequentPolicy_ReturnsTop1Only
.\gradlew.bat testDebugUnitTest --tests com.socialapp.core.prefetch.PolicySuiteTest.testP3_MarkovOrder1Policy_ThresholdFilteringAndOnlineLearning
.\gradlew.bat testDebugUnitTest --tests com.socialapp.core.prefetch.PolicySuiteTest.testP4_MarkovOrder2Policy_SequenceDisambiguationAndBackoff
.\gradlew.bat testDebugUnitTest --tests com.socialapp.core.prefetch.PolicySuiteTest.testP5_NetworkCostAdaptivePolicy_SuppressesPrefetchOnChokedNetwork
.\gradlew.bat testDebugUnitTest --tests com.socialapp.core.prefetch.PolicySuiteTest.testPrefetchRuntimeConfig_OneLineSwitchAndScenarioParser
```

---

## 8. QUY TRÌNH CHẠY KIỂM THỬ TỰ ĐỘNG TOÀN DIỆN QUA GRADLE CLI

Để kiểm tra toàn bộ 19 test cases của khối Mạng & Prefetching (Long phụ trách) cùng toàn bộ các ViewModel/Repository khác của dự án trong một lệnh duy nhất:

```powershell
# Chạy toàn bộ Unit Tests của ứng dụng
.\gradlew.bat testDebugUnitTest
```

### Kết quả đầu ra mong đợi:
```text
> Task :app:compileDebugUnitTestJavaWithJavac
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 18s
24 actionable tasks: 4 executed, 20 up-to-date
```

Báo cáo chi tiết HTML được tự động tạo tại:  
`app/build/reports/tests/testDebugUnitTest/index.html`

---

## 9. BẢNG TRA CỨU SỐ LIỆU LÝ THUYẾT VS THỰC TẾ ĐỂ ĐỐI CHIẾU

Dưới đây là bảng số liệu độ trễ chuẩn hóa để các thành viên trong nhóm (hoặc hội đồng chấm) đối chiếu khi bấm test trực tiếp trên app:

| Màn hình | Dung lượng chuẩn | Wi-Fi (1.0x) | LTE 4G (1.0x) | 3G (1.0x) | 3G (Nặng 2.0x) | EDGE 2G (1.0x) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **HOME** | $120\text{ KB}$ | **$34\text{ ms}$** | **$98\text{ ms}$** | **$660\text{ ms}$** | **$1,140\text{ ms}$** | **$6,400\text{ ms}$** |
| **POST_DETAIL** | $250\text{ KB}$ | **$55\text{ ms}$** | **$150\text{ ms}$** | **$1,180\text{ ms}$** | **$2,180\text{ ms}$** | **$12,900\text{ ms}$** |
| **COMMENTS** | $80\text{ KB}$ | **$28\text{ ms}$** | **$82\text{ ms}$** | **$500\text{ ms}$** | **$820\text{ ms}$** | **$4,400\text{ ms}$** |
| **PROFILE** | $60\text{ KB}$ | **$25\text{ ms}$** | **$74\text{ ms}$** | **$420\text{ ms}$** | **$660\text{ ms}$** | **$3,400\text{ ms}$** |

> **Quy ước tính toán:**
> * $\text{Wi-Fi: } 15\text{ms} + \frac{\text{Size}}{6250} \times 1000$
> * $\text{LTE 4G: } 50\text{ms} + \frac{\text{Size}}{2500} \times 1000$
> * $\text{3G: } 180\text{ms} + \frac{\text{Size}}{250} \times 1000$
> * $\text{EDGE 2G: } 400\text{ms} + \frac{\text{Size}}{20} \times 1000$
