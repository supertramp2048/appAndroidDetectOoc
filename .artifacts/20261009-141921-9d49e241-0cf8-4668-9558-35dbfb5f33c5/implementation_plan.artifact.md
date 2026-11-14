# Kế hoạch Khắc phục Màn hình Trắng trong `ResultActivity` (`dialog_prediction_result.xml`)

---

## 🔍 Phân tích Lỗi Màn hình Trắng (Blank White Screen)

- **Nguyên nhân chính trong XML**:
  - File giao diện `dialog_prediction_result.xml` khai báo root `LinearLayout` thuộc tính `android:orientation="horizontal"`.
  - Tuy nhiên, các khung con bên trong lại khai báo `android:layout_height="0dp"` kèm `android:layout_weight`.
  - Trong `LinearLayout` chiều ngang (`horizontal`), thuộc tính `layout_height="0dp"` làm toàn bộ chiều cao của các phần tử co về **0 pixel**, khiến giao diện bị ẩn hoàn toàn và ứng dụng chỉ hiển thị một màn hình trắng tinh.

---

## Giải pháp Khắc phục Triệt để

1. **Cấu trúc lại Layout theo chiều dọc (`android:orientation="vertical"`)**:
   - Thay đổi chiều xếp lớp của `LinearLayout` root sang `vertical`.
   - Cài đặt `top_image_container` chứa ảnh chụp và nút Đóng với chiều cao cố định `220dp`.
   - Cài đặt `NestedScrollView` chứa thông tin kết quả (Verdict, % Độ tin cậy, Lý do AI, Bài báo đối chứng) với chiều cao `0dp` và `layout_weight="1"` để chiếm trọn phần diện tích màn hình còn lại.

---

## Các File Sẽ Sửa

- [EDIT] [dialog_prediction_result.xml](file:///C:/Users/ADMIN88/AndroidStudioProjects/BtckAndroid/app/src/main/res/layout/dialog_prediction_result.xml): Sửa orientation sang `vertical` và thiết lập lại chiều cao phần tử.

---

## Kế hoạch Kiểm thử (Verification Plan)

### Automated Unit Tests
- Chạy toàn bộ suite unit test:
```powershell
./gradlew app:testDebugUnitTest
```

### Direct Build & Deploy
- Biên dịch APK và deploy lên thiết bị kiểm tra:
```powershell
./gradlew app:assembleDebug
```
