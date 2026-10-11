# Kế hoạch Hoàn thiện Luồng Chụp Màn hình Bong bóng Nổi 0-Popup (Cấp quyền 1 Lần Duy nhất)

---

## 🔍 Phân tích Lỗi Vòng lặp Xin quyền

- **Hiện tượng**:
  - Mỗi khi bạn khoanh vùng trên Icon nổi, hệ thống lại mở lại màn hình xin quyền Android, tạo thành một vòng lặp phiền phức.
- **Nguyên nhân**:
  - Do các chỉnh sửa trước đó đã mở lại `MediaProjectionActivity` mỗi lần khoanh vùng.
- **Cách hoạt động chuẩn mượt mà 100%**:
  1. Khi bạn bấm **"Bắt đầu chụp màn hình"** trong app -> Bảng xin quyền của Android hiện ra **1 LẦN DUY NHẤT**.
  2. Bạn bấm *"Bắt đầu ngay"* -> Icon nổi xuất hiện.
  3. Từ thời điểm này trở đi, bạn có thể chuyển sang **Google Chrome**, **Facebook**, **Youtube** hay ở trong chính app -> Chạm Icon nổi khoanh vùng chụp **N LẦN LIÊN TỤC** -> **TỰ ĐỘNG CHỤP TỨC THÌ VÀ HIỆN KẾT QUẢ VỚI 0 POPUP XIN QUYỀN!**

---

## Giải pháp Kiến trúc Hoàn chỉnh 100%

1. **`MediaProjectionActivity.java` (Xin quyền 1 lần khi bật Bong bóng nổi)**:
   - Khi bạn bấm "Bắt đầu chụp màn hình" trong app: `MediaProjectionActivity` mở ra, bạn bấm *"Bắt đầu ngay"*.
   - Chuyển `resultCode` và `data` cho `ScreenCaptureService` rồi tự đóng lại (`finish()`).

2. **`ScreenCaptureService.java` (Giữ phiên chụp sống & Chụp trực tiếp từ Icon nổi 0-Popup)**:
   - `ScreenCaptureService` chạy Foreground Service (`FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION`).
   - Ngay khi nhận mã cấp phép, gọi `mediaProjection = manager.getMediaProjection(...)` và `registerCallback(...)` **1 LẦN DUY NHẤT** và giữ `mediaProjection` sống trong bộ nhớ.
   - Khi bạn chạm Icon nổi khoanh vùng chụp ở bất kỳ đâu (Google Chrome, Facebook, trong app...):
     - `ScreenCaptureService` **tự động chụp ngay trực tiếp từ `mediaProjection` sẵn có với flag `VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR`**, tuyệt đối KHÔNG mở lại `MediaProjectionActivity` và KHÔNG bao giờ hỏi lại quyền của Android nữa!
     - Giải phóng `virtualDisplay` sau khi lấy ảnh, giữ `mediaProjection` sống cho các lần chụp tiếp theo.

3. **Mở giao diện đè ứng dụng ngoài bằng `PendingIntent.send()`**:
   - Sử dụng `PendingIntent.send()` để mở `ScreenshotCropActivity` và màn hình **Kết quả kiểm chứng (`ResultActivity`)** đè trực tiếp lên Google Chrome hay ứng dụng bất kỳ.

---

## Các File Sẽ Sửa

- [EDIT] [MediaProjectionActivity.java](file:///C:/Users/ADMIN88/AndroidStudioProjects/BtckAndroid/app/src/main/java/com/socialapp/feature/factcheck/MediaProjectionActivity.java): Xin quyền 1 lần duy nhất khi bật Bong bóng nổi, chuyển token cho Service rồi đóng lại.
- [EDIT] [ScreenCaptureService.java](file:///C:/Users/ADMIN88/AndroidStudioProjects/BtckAndroid/app/src/main/java/com/socialapp/feature/factcheck/ScreenCaptureService.java): Giữ `mediaProjection` sống trong Foreground Service, chụp ảnh trực tiếp từ Icon nổi với **0 Popup xin quyền**, mở giao diện đè ứng dụng ngoài bằng `PendingIntent.send()`.

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
