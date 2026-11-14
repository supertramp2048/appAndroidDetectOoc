# Markov Lab — Phân hệ Huấn luyện & Dự đoán Markov (Thành viên Kiên - Tasks B1 đến B5)

Thư mục này chứa toàn bộ mã nguồn mô phỏng ngoại tuyến (Offline Simulation), kiểm thử đa bậc (Cross-check), dữ liệu huấn luyện (`sessions.csv`), và biểu đồ phân tích (Pareto, Learning curve) do thành viên **Kiên** xây dựng.

---

## 1. Cấu trúc thư mục

```
markov-lab/
├── java/
│   ├── MarkovPredictor.java     # Thuật toán All-k-th-Order Markov Predictor thuần Java (đã tích hợp vào App)
│   ├── MarkovTest.java          # Bộ 7 test cases kiểm thử thuật toán
│   └── CrossCheck.java          # Đối chiếu độ chính xác Top-1 với tập sessions.csv
├── sim/
│   ├── generate_logs.py         # Kịch bản sinh dữ liệu điều hướng ngẫu nhiên (Markov chain)
│   ├── sessions.csv             # Tập dữ liệu 100 phiên điều hướng người dùng
│   ├── offline_simulation.py    # Mô phỏng nạp trước ngoại tuyến & tính toán Pareto trade-off
│   ├── crosscheck.py            # Kiểm thử tính đúng đắn giữa Python và Java
│   └── test_markov.py           # Unit test cho module Markov bằng Python
└── out/
    ├── learning_curve.png       # Biểu đồ đường cong học tập theo số lượng phiên
    ├── pareto_3g.png            # Biểu đồ Pareto biên đánh đổi: Giảm độ trễ vs Băng thông lãng phí trên mạng 3G
    └── sim_results.csv          # Kết quả mô phỏng độ trễ và byte lãng phí qua các ngưỡng θ
```

---

## 2. Tích hợp vào Dự án Android App

1. **Class lõi trong App:**
   - [MarkovPredictor.java](file:///c:/Users/ADMIN88/AndroidStudioProjects/BtckAndroid/app/src/main/java/com/socialapp/core/prefetch/policy/markov/MarkovPredictor.java) nằm tại package `com.socialapp.core.prefetch.policy.markov`.
   - Được kết nối trực tiếp với [MarkovTransitionMatrix.java](file:///c:/Users/ADMIN88/AndroidStudioProjects/BtckAndroid/app/src/main/java/com/socialapp/core/prefetch/policy/markov/MarkovTransitionMatrix.java) và [MarkovTransitionTensor.java](file:///c:/Users/ADMIN88/AndroidStudioProjects/BtckAndroid/app/src/main/java/com/socialapp/core/prefetch/policy/markov/MarkovTransitionTensor.java).
   - Huấn luyện trực tiếp (`online learning`) qua hàm `learn(history, next)` mỗi khi người dùng chuyển màn hình.

2. **Unit Test JUnit tự động:**
   - [MarkovPredictorTest.java](file:///c:/Users/ADMIN88/AndroidStudioProjects/BtckAndroid/app/src/test/java/com/socialapp/core/prefetch/MarkovPredictorTest.java) chạy tự động qua lệnh:
     ```powershell
     .\gradlew.bat testDebugUnitTest --tests com.socialapp.core.prefetch.MarkovPredictorTest
     ```
   - Bao phủ trọn vẹn 7 kịch bản:
     - Case 1: Tính tay xác suất bậc 2 cơ bản
     - Case 2: Kiểm soát Cold-start dưới ngưỡng `minSupport`
     - Case 3: Cơ chế hạ bậc Katz Backoff (từ Bậc 2 xuống Bậc 1)
     - Case 4: Ưu tiên Bậc 2 khi đã đủ mẫu quan sát
     - Case 5: Xử lý hòa xác suất (Tie-breaking theo thứ tự từ điển A-Z)
     - Case 6: Lịch sử dài hơn `maxOrder` & Null-safety
     - Case 7: Reset trạng thái huấn luyện

