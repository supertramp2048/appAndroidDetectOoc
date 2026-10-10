# Kế hoạch Hoàn thiện Tích hợp AI Search trên Màn Tìm kiếm & Mở Bài viết Chi tiết khi bấm Khuyến nghị

---

## 🔍 Chi tiết Phương án Kỹ thuật

1. **Payload `ChatRequest` Tinh gọn 100%**:
   - [ChatRequest.java](file:///C:/Users/ADMIN88/AndroidStudioProjects/BtckAndroid/app/src/main/java/com/socialapp/feature/factcheck/ChatRequest.java): Chỉ chứa duy nhất trường `query`.
   - JSON gửi lên `POST /api/v1/chat`:
     ```json
     {
       "query": "tim cac bai ve van hoa, xa hoi"
     }
     ```

2. **Model `ChatResponse.ArticleRec`**:
   - [ChatResponse.java](file:///C:/Users/ADMIN88/AndroidStudioProjects/BtckAndroid/app/src/main/java/com/socialapp/feature/factcheck/ChatResponse.java): Ánh xạ bổ sung trường `id` bên cạnh `title` và `url`:
     ```json
     {
       "title": "Khám phá miền Tây mùa nước nổi",
       "id": "6"
     }
     ```

3. **Chuyển hướng Mở Chi tiết Bài viết (`PostDetailActivity`) khi bấm vào Kết quả AI Khuyến nghị**:
   - Trong [SearchFragment.java](file:///C:/Users/ADMIN88/AndroidStudioProjects/BtckAndroid/app/src/main/java/com/socialapp/feature/feed/SearchFragment.java):
     - Mỗi bài viết khuyến nghị (`ArticleRec`) được vẽ thành một mục liên kết có icon.
     - Khi người dùng chạm vào một tiêu đề bài viết:
       - Kiểm tra nếu có `rec.id`: Gọi `Navigator.navigateToPostDetail(requireContext(), Integer.parseInt(rec.id))` để mở thẳng màn hình **Chi tiết bài viết (`PostDetailActivity`)**!
       - Nếu có `rec.url`: Gọi `Navigator.navigateTo(requireContext(), rec.url)`.

4. **Tích hợp Nút & Giao diện AI Search trên Màn Tìm kiếm (`SearchFragment`)**:
   - [fragment_search.xml](file:///C:/Users/ADMIN88/AndroidStudioProjects/BtckAndroid/app/src/main/res/layout/fragment_search.xml):
     - Bổ sung nút **"🤖 Tìm bằng AI"** (`btn_search_ai`).
     - Bổ sung Card `card_ai_search_result` chứa văn bản trả lời từ AI (`tv_ai_answer`) và danh sách bài viết khuyến nghị (`container_ai_recommendations`).
   - Dọn dẹp nút `btn_ai_chat` khỏi màn Chi tiết bài viết (`PostDetailActivity`).

---

## Các File Sẽ Sửa

### 1. Model Payload & Response (`com.socialapp.feature.factcheck`)
- [EDIT] [ChatRequest.java](file:///C:/Users/ADMIN88/AndroidStudioProjects/BtckAndroid/app/src/main/java/com/socialapp/feature/factcheck/ChatRequest.java): Giữ duy nhất trường `query`.
- [EDIT] [ChatResponse.java](file:///C:/Users/ADMIN88/AndroidStudioProjects/BtckAndroid/app/src/main/java/com/socialapp/feature/factcheck/ChatResponse.java): Ánh xạ `id`, `title`, `url` trong `ArticleRec`.

### 2. Màn Chi tiết Bài viết (`com.socialapp.feature.post`)
- [EDIT] [activity_post_detail.xml](file:///C:/Users/ADMIN88/AndroidStudioProjects/BtckAndroid/app/src/main/res/layout/activity_post_detail.xml) & [PostDetailActivity.java](file:///C:/Users/ADMIN88/AndroidStudioProjects/BtckAndroid/app/src/main/java/com/socialapp/feature/post/PostDetailActivity.java): Loại bỏ `btn_ai_chat`.

### 3. Tích hợp AI Search trên Màn Tìm kiếm (`com.socialapp.feature.feed`)
- [EDIT] [fragment_search.xml](file:///C:/Users/ADMIN88/AndroidStudioProjects/BtckAndroid/app/src/main/res/layout/fragment_search.xml): Thêm nút **"🤖 Tìm bằng AI"** và Card kết quả AI Search.
- [EDIT] [SearchFragment.java](file:///C:/Users/ADMIN88/AndroidStudioProjects/BtckAndroid/app/src/main/java/com/socialapp/feature/feed/SearchFragment.java): Xử lý gọi `POST /api/v1/chat`, hiển thị câu trả lời AI và gắn sự kiện click vào bài viết khuyến nghị để gọi `Navigator.navigateToPostDetail()`.

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
