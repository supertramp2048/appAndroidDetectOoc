## **Hệ thống xây những gì**

| Khối | Việc làm |
| ----- | ----- |
| **App** (đã có) | Các màn hình, gọi dữ liệu qua `Repository.load(screen)` |
| **Nguồn dữ liệu giả** | Chờ theo công thức RTT \+ kích thước/băng thông, có chia sẻ băng thông giữa các request cùng lúc |
| **Bộ dự đoán Markov** | Đếm chuyển màn hình, trả về xác suất màn hình kế tiếp (bậc 1, bậc 2\) |
| **Chính sách prefetch** | Quyết định tải trước màn hình nào (P0 đến P5) |
| **PrefetchManager và cache** | Phát request nền, lưu kết quả, gộp request đang bay |
| **MetricsLogger** | Ghi độ trễ, loại hit, byte lãng phí ra CSV |
| **Bộ sinh log và replay engine** | Tạo phiên người dùng giả và tự điều hướng theo log |
| **Phân tích** | Thống kê và biểu đồ Pareto |

## **Luồng khi người dùng chuyển màn hình**

```
Người dùng bấm vào màn hình Y
        │
        ▼
Repository.load(Y)
        │
        ├─ Cache đã có Y?          ──► FULL HIT: hiện ngay
        ├─ Y đang được prefetch?   ──► LATE HIT: chờ phần còn lại, không gửi trùng
        └─ Chưa có                 ──► MISS: gọi nguồn dữ liệu giả (chờ theo mạng)
        │
        ▼
Hiển thị màn hình Y  ──► MetricsLogger ghi: độ trễ, loại hit
        │
        ▼
Bộ dự đoán Markov nhận lịch sử [..., X, Y]
        │  trả về: Z1 (60%), Z2 (25%), Z3 (10%)
        ▼
Chính sách prefetch quyết định
        │  (ví dụ P5: chỉ chọn Z có  p × thời_gian_tải > λ × byte)
        ▼
PrefetchManager tải nền các Z được chọn ──► lưu vào cache
        │
        ▼
Người dùng bấm tiếp:
   đoán đúng ──► vòng lặp mới, lần này là FULL/LATE HIT
   đoán sai  ──► ghi byte đã tải mà không dùng = byte LÃNG PHÍ
```

## **Luồng thí nghiệm**

```
Bộ sinh log (B) ──► file phiên giả
        │
        ├──► Mô phỏng offline: hit rate, đường cong học (không cần app)
        │
        └──► Replay engine (A) chạy trong app
                 │  với mỗi tổ hợp: chính sách × mạng × độ nặng
                 ▼
             CSV kết quả ──► Thống kê (D) ──► Biểu đồ Pareto
```

1: KHẢO SÁT : DUY THÀNH

| \# | Nguồn | Loại | Dùng để làm gì |
| ----- | ----- | ----- | ----- |
| 1 | **Jiang & Kleinrock 1998**, *An adaptive network prefetch scheme* | Học thuật | Cơ sở của công thức ngưỡng prefetch. Bài tính ngưỡng động theo tải hệ thống, dung lượng và chi phí thời gian, tài nguyên của người dùng. |
| 2 | **Higgins và cộng sự, MobiSys 2012**, hệ thống IMP | Học thuật | Prefetch trên di động theo chi phí và lợi ích. IMP cân nhắc giữa thời gian phản hồi với ngân sách pin và dữ liệu di động. Đề tài của mình không đo pin, cần nêu rõ là giới hạn |
| 3 | **Malavolta và cộng sự, ICSE-NIER 2019**, prefetch theo điều hướng trên Android | Học thuật | Bài **gần đề tài nhất**. Bàn về việc dùng mô hình điều hướng của người dùng để prefetch request mạng cá nhân hóa trong app Android. |
| 4 | **Chrome**, dự đoán và Speculation Rules | Ngành | Ví dụ ngoài đời của “dự đoán có ngưỡng”. Chrome preconnect khi độ tin cậy trên 30% và prerender khi trên 50%. Có thể dùng làm căn cứ chọn θ thay vì chọn tùy ý. Chrome cũng lưu ý prerender tốn thêm bộ nhớ và băng thông, chỉ nên dùng khi khả năng điều hướng cao. |
| 5 | **Thư viện Android phổ biến**: Glide `RecyclerViewPreloader` và Paging 3 | Ngành | Làm baseline “tải trước cố định”. Glide cho giới hạn số mục tải trước bằng tham số `maxPreload`. Paging 3 có `prefetchDistance` và kích thước trang. |

- Đọc 5 nguồn trên, mỗi nguồn viết: làm gì, kết quả chính, liên quan đề tài thế nào   
- Đóng góp và giới hạn của đề tài so với 5 nguồn   
- So sánh, nhận xét gì đó  
- Viết chương **tổng quan** 

\=\> Nhờ AI đọc hộ nhưng viết lại theo ý của mình

2: APP : HUY

| \# | Việc | Xong khi |
| ----- | ----- | ----- |
| A1 | Gom mọi chỗ lấy dữ liệu trong app về **một hàm** `Repository.load(screen)` | App chạy như cũ |
| A2 | Ghi kết quả ra file CSV (độ trễ mỗi lần chuyển màn hình), có script kéo file về máy tính | File ra đúng cột đã chốt |
| A3 | **Replay:** đọc file hành vi, tự bấm qua các màn hình đúng thứ tự và đúng thời gian dừng | Chạy 100 phiên không bị lỗi |
| A4 | **Thanh hiển thị demo:** dự đoán tiếp theo, trạng thái tải trước, số lần đoán đúng/sai | Cập nhật khi chuyển màn hình |
| A5 | Chạy thí nghiệm trên máy ảo, quay video demo | Có đủ CSV cho mọi cấu hình |

3: Dữ liệu và bộ đoán: KIÊN

| \# | Việc | Xong khi |
| ----- | ----- | ----- |
| B1 | **Tạo hành vi giả:** màn hình nào hay đi sang màn hình nào, thời gian dừng, thêm vài mẫu “đi 2 bước rồi mới ra quyết định” | Sinh được hàng nghìn phiên. **Đưa A file mẫu 50 phiên cuối tuần 1** |
| B2 | **Bộ đoán Markov** bậc 1 và bậc 2: viết Python trước, rồi chuyển JAVA | Đúng với ví dụ tính tay |
| B3 | **Mô phỏng ngoài app:** tính tỉ lệ đoán đúng, đường cong “cần bao nhiêu phiên để học tốt” | Có số liệu và biểu đồ |
| B4 | **Thống kê và vẽ biểu đồ** (trung vị, kiểm định, biểu đồ đánh đổi độ trễ và dữ liệu lãng phí) | Chạy được trên CSV giả trước, CSV thật sau |

4: Mạng giả và tải trước : LONG

| \# | Việc | Xong khi |
| ----- | ----- | ----- |
| C1 | **Mạng giả:** 4 profile (Wi-Fi, LTE, 3G, EDGE), độ trễ \= RTT \+ dung lượng/băng thông, hệ số độ nặng ×0,5/×1/×2 | Độ trễ khớp với bảng tính tay |
| C2 | **Chia sẻ băng thông:** hai request chạy cùng lúc thì chia nhau đường truyền | Tải trước và tải thật cùng lúc thì cả hai chậm hơn |
| C3 | **Cache:** lưu dữ liệu đã tải trước, có hạn dùng, đánh dấu “đã dùng hay chưa” | Có kiểm thử đơn giản |
| C4 | **Bộ tải trước:** nhận biết người dùng vào màn hình nào, hỏi bộ đoán, quyết định tải gì, tải nền. Xử lý **late hit** (người dùng bấm khi đang tải dở thì tái sử dụng request, không gửi lại) | Phân loại đúng 3 trường hợp: có sẵn / đang tải / chưa có |
| C5 | **Các chính sách:** P0 không tải trước, P1 tải hết, P2 tải màn phổ biến nhất, P3 Markov ngưỡng cố định, P4 Markov bậc 2, P5 ngưỡng theo mạng | Đổi chính sách bằng một dòng cấu hình |

