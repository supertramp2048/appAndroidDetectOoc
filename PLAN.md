# Kế hoạch phát triển app Android – Đọc tin & Kiểm chứng tin giả

Oct 9, 2026 · @text in vietnamese

## Tổng quan

App được chia thành 11 module (M0–M10) để giao song song: M0 làm trước, M1–M9 làm độc lập ngay sau đó, M10 (kiểm duyệt/quản trị) làm sau cùng.

Hiện trạng và quyết định công nghệ. App đã có tính năng nhận biết tin giả từ ảnh tải lên và ảnh chụp. Stack đã chốt: 100% Java cho code mới và code cũ; Android Views với XML + ViewBinding; Fragment, RecyclerView, Material Components và Navigation Component. Không dùng Java, Android Views (XML + ViewBinding) hay các công nghệ UI/async đặc thù Java. Giữ một module :app, chia package core/ và feature/<tên>; feature chỉ import core/.

Phạm vi API. Swagger có 74 route (75 thao tác, vì category có cả PUT và PATCH). Trong đó 44 thao tác phục vụ người dùng thường (M0–M9) và 31 thao tác dành cho Moderator/Admin/Superadmin (M10). Phụ lục ở cuối đối chiếu từng endpoint với module.

Nguyên tắc chia module.

Chỉ có một Gradle module :app; chia package core/ và feature/<tên>. Feature chỉ import core/ và không gọi trực tiếp feature khác; điều hướng qua Navigator/route khai báo ở M0.

Mỗi module tự có repository, màn hình và test, chạy được với dữ liệu giả dựng từ ví dụ trong swagger nên không phải chờ module khác.

Ranh giới cắt theo tài nguyên API (posts, comments, profiles…) chứ không theo màn hình, để hai người không sửa chung một file.

## Kiến trúc và quy ước kỹ thuật

Mọi module theo cùng một bộ quy ước; M0 hiện thực hóa chúng, các module khác chỉ việc dùng.

| Hạng mục | Quy ước |
| --- | --- |
| Ngôn ngữ/UI/kiến trúc | 100% Java; Android Views với XML + ViewBinding, Fragment, RecyclerView (ListAdapter + DiffUtil), Material Components, Navigation Component; MVVM với ViewModel + LiveData + Repository. Mọi luồng bất đồng bộ dùng LiveData/callback theo quy ước chung |
| DI | AppContainer viết tay trong lớp Application; không dùng framework DI tự động |
| Mạng | Retrofit + OkHttp + Gson. Sinh DTO và API interface từ swagger bằng openapi-generator (generator java, library retrofit2, serializationLibrary gson); không gõ tay model. Repository gọi Retrofit bằng Call.enqueue và phát kết quả qua LiveData<ApiResult<T>> hoặc callback; callback chạy trên main thread |
| Phân trang | PageLoader dùng chung trong core/; không dùng thư viện phân trang tự động |
| Ảnh | Glide để hiển thị ảnh cho code mới; nén và resize phía client trước khi upload |
| Lưu token | SharedPreferences được mã hóa bằng Android Keystore (EncryptedSharedPreferences hoặc giải pháp tương đương còn được hỗ trợ). Kiểm tra tình trạng bảo trì thư viện trước khi chọn; không lưu token dạng văn bản thuần |
| Điều hướng | Navigation Component; Navigator và route khai báo tập trung trong core/ |
| Cache offline | Room, tùy chọn, bắt đầu từ giai đoạn 2 |
| Base URL | Debug: http://10.0.2.2:8000 (emulator); Release: https://socialappbe-8tjc.onrender.com. Chỉ bật cleartext cho debug |
| Header bắt buộc | Accept: application/json trên mọi request (thiếu header này Laravel có thể trả redirect hoặc HTML thay vì JSON 401/422); Authorization: Bearer {token} khi đã đăng nhập |
| Timeout | Connect/read 60 giây. Server trên Render có thể "ngủ" và mất 30–60 giây cho lần gọi đầu, nên hiển thị skeleton và nút thử lại thay vì báo lỗi ngay |
| Xử lý lỗi | 401: xóa token, về màn đăng nhập (trừ endpoint công khai). 403: không đủ quyền hoặc email chưa xác thực. 404: nội dung đã bị gỡ, dùng message từ server. 422: ánh xạ errors{field:[msg]} vào từng ô nhập. 429/5xx/mất mạng: thông báo và cho thử lại |
| Upload ảnh | multipart/form-data. Tên field: avatar (tối đa 5MB), cover (10MB), file (5MB, dùng cho /api/image-upload và ảnh nền admin), logo (2MB), image_file (quảng cáo, 2MB) |
| Khách và đăng nhập | Khách xem được feed, chi tiết bài, bình luận, hồ sơ, chuyên mục, quảng cáo. Mọi hành động ghi (vote, bình luận, follow, đăng bài, báo cáo) yêu cầu đăng nhập, sau đó quay lại đúng hành động đang làm |
| Cập nhật lạc quan | Vote và follow đổi giao diện ngay, hoàn tác nếu API lỗi |
| Ngôn ngữ | Tiếng Việt, mọi chuỗi nằm trong strings.xml |
| Kiểm thử | Unit test cho ViewModel và Repository; dùng MockWebServer với JSON ví dụ lấy từ swagger |

## Cần chốt với backend trước khi code

Có 11 điểm swagger chưa nói rõ; điểm #2–#5 (mức "Chặn") ảnh hưởng trực tiếp tới việc dev module nào đó nên gửi cho người làm backend ngay hôm nay. Điểm #1 (phân trang) đã chạy đúng trên server, chỉ cần sửa tài liệu và địa chỉ https trong links.

| # | Vấn đề trong swagger | Đề xuất | Mức | Module bị ảnh hưởng |
| --- | --- | --- | --- | --- |
| 1 | Swagger không khai báo tham số page và không mô tả links/meta, dù server thật đã trả đủ (đã kiểm tra /api/posts, bình luận, phản hồi, follower, tìm user) | Chỉ cần sửa swagger: thêm page, schema links/meta, số bản ghi mặc định mỗi trang (phản hồi 5, follower 15) và ghi rõ /replies, follower có nhận limit không. Sửa links.next đang là http:// thành https:// (cấu hình trusted proxy) | Nên chốt (đã kiểm tra) | M2, M4, M6, M10 |
| 2 | OAuth Google/GitHub: callback trả về trang HTML (view) chứa token, chỉ hợp với web | Thêm tham số client=android để callback chuyển hướng về deep link socialapp://oauth?token=…, hoặc thêm endpoint nhận Google ID token từ Credential Manager | Chặn (chỉ phần OAuth) | M1 |
| 3 | UserResource không có email_verified_at và role; mô tả /api/admin/login nói trả về role nhưng schema không có | Bổ sung hai trường này vào UserResource cho /api/user, /api/login, /api/admin/login | Chặn | M1, M5, M10 |
| 4 | Đăng bài "yêu cầu đã kích hoạt email" nhưng swagger không ghi mã lỗi; chưa rõ comment, vote, follow có đòi xác thực email không | Liệt kê endpoint dùng middleware verified và chốt mã lỗi (thường 403 "Your email address is not verified.") | Chặn | M1, M3, M4, M5 |
| 5 | Có danh sách và khôi phục nội dung bị gỡ, nhưng không có endpoint để moderator gỡ bài/bình luận; DELETE /api/posts/{post} chỉ mô tả "tác giả xóa" | Xác nhận moderator/admin dùng chính DELETE đó (gỡ mềm) hay có endpoint riêng chưa ghi | Chặn | M10 |
| 6 | Link xác thực email redirect (302) về giao diện web | Hỗ trợ Android App Links, hoặc chấp nhận luồng: xác thực trên trình duyệt rồi quay lại app bấm "Tôi đã xác thực" (app gọi /api/user kiểm tra) | Nên chốt | M1 |
| 7 | position của quảng cáo là chuỗi tự do (ví dụ sidebar_top) | Chốt danh sách vị trí cho mobile, ví dụ feed_inline, post_detail_bottom | Nên chốt | M8 |
| 8 | content_html do người dùng nhập; chưa rõ server có lọc HTML | Server lọc bằng HTMLPurifier hoặc tương đương; app vẫn tắt JavaScript khi hiển thị | Nên chốt | M3, M5 |
| 9 | status của bài viết và bình luận chỉ ghi "published, hidden…" | Liệt kê đầy đủ giá trị enum | Nên chốt | M3, M4, M10 |
| 10 | Chưa có rate limit/mã 429 trong swagger | Ghi giới hạn cho login, đăng ký, bình luận, báo cáo | Nên chốt | M0 |
| 11 | Không có API cho lưu bài (bookmark), thông báo, đăng ký push (FCM), đổi email | Đưa vào backlog; nếu cần push thì thêm POST /api/devices | Backlog | Ngoài phạm vi hiện tại |

Tính năng nhận biết tin giả hiện không có endpoint trong swagger này, nên M9 giữ nguyên nguồn gọi cũ; cần xác nhận app đang gọi dịch vụ riêng nào.

## Bảng module

Ước đoán tổng công M0–M9 khoảng 49–60 người-ngày; thêm 9–12 người-ngày cho M10. Với 3 người, dự kiến khoảng 5–6 tuần cho M0–M9 và 6–8 tuần để hoàn thành cả M10, QA và tích hợp. Đây là ước lượng gần đúng: mỗi module đã tăng khoảng 10–15% để phản ánh Java dài dòng hơn, đồng thời M0 được cộng thêm 1 ngày cho PageLoader.

| Module | Phạm vi | Thao tác API | Phụ thuộc | Giai đoạn | Ước lượng (người-ngày) |
| --- | --- | --- | --- | --- | --- |
| M0 Nền tảng | Mạng, session, theme, điều hướng, component dùng chung và PageLoader | 0 | — | 0 | 7–8 |
| M1 Xác thực | Đăng ký, đăng nhập, quên/đặt lại mật khẩu, xác thực email, OAuth, đăng xuất | 11 | M0 | 1 | 5–6 |
| M2 Bảng tin | Feed (mới nhất, nổi bật, đang theo dõi), chuyên mục, tìm bài | 3 | M0 | 1 | 4–5 |
| M3 Chi tiết bài | Đọc bài, vote, báo cáo bài, nút kiểm tra tin giả | 4 | M0 | 1 | 5–6 |
| M4 Bình luận | Bình luận đa cấp, thêm, sửa, xóa, báo cáo | 6 | M0 | 1 | 5–6 |
| M5 Soạn bài | Tạo, sửa, xóa bài, chọn chuyên mục, upload ảnh | 4 | M0, M1 | 2 | 7–8 |
| M6 Hồ sơ và mạng xã hội | Trang cá nhân, follow, danh sách follower, tìm người, báo cáo user | 6 | M0 | 2 | 5–6 |
| M7 Cài đặt tài khoản | Đổi tên, mật khẩu, avatar, ảnh bìa, xóa tài khoản | 6 | M0, M1 | 2 | 4 |
| M8 Quảng cáo và cấu hình | Banner theo vị trí, logo, ảnh nền, footer | 4 | M0 | 1 | 3 |
| M9 Kiểm chứng tin giả | Tách tính năng hiện có thành module, nối với bài viết | 0 (+ M5) | M0 | 1 | 4–5 |
| M10 Kiểm duyệt và quản trị | Đăng nhập admin, xử lý báo cáo, khôi phục, ban/unban, vai trò | 31 | M0, M1 | 3 | 9–12 |

Thành phần báo cáo (ReportSheet) nằm trong M0 để M3, M4, M6 không phải chờ nhau; mỗi module tự gắn endpoint báo cáo của mình.

## M0 – Nền tảng (Core)

M0 dựng khung mà mọi module khác chạy trên đó, nên phải xong và merge trước khi các module còn lại bắt đầu code UI. Giao cho dev có kinh nghiệm nhất; trong lúc chờ, người khác đọc swagger và dựng dữ liệu giả.

Việc cần làm

**M0 chia hai đợt**
- **Đợt 1:** cấu trúc một module :app và package core/ + feature/<tên>; cấu hình mạng/API sinh tự động, AppContainer, ApiResult, SessionManager, Navigator/HomeActivity/bottom nav; hoàn thành PageLoader và unit test để M2, M4, M6 có thể bắt đầu ngay.
- **Đợt 2:** hoàn thiện theme/components dùng chung, chọn và nén ảnh, FakeApi/MockWebServer, cấu hình build/CI và tài liệu hướng dẫn; merge các phần còn lại sau khi đợt 1 ổn định.

☐ Giữ một module Gradle :app; chia package core/ và feature/<tên>. Feature chỉ import core/; không tạo Gradle module riêng cho core/ hoặc feature/.

☐ Chạy openapi-generator (generator java, library retrofit2, serializationLibrary gson) trên swagger, commit DTO và API interface vào package mạng trong core/; viết script sinh lại khi swagger đổi.

☐ OkHttp: interceptor Accept: application/json, interceptor Authorization, timeout 60 giây, log chỉ ở debug.

☐ ApiResult<T> và bộ parse lỗi 401/403/404/422/429/5xx thành AppError (có fieldErrors cho 422).

☐ SessionManager: lưu token và user, phát LiveData<Session>; 401 toàn cục thì đăng xuất; logout() gọi POST /api/logout và luôn xóa token cục bộ; helper requireLogin(Runnable) cho người dùng khách. Dùng ApiResult<T> là lớp Java biểu diễn trạng thái thành công hoặc lỗi.

☐ Theme bằng Material Components (sáng/tối) và component dùng chung: PostCard, UserAvatar, LoadingSkeleton, ErrorState(retry), EmptyState, ConfirmDialog, ReportSheet (ô lý do 10–500 ký tự, trả kết quả qua lambda), AdSlot(position) (khe quảng cáo, mặc định rỗng, M8 gắn nội dung vào).

☐ Điều hướng: bottom nav 4 tab (Trang chủ, Kiểm chứng, Tìm kiếm, Hồ sơ), nút viết bài là FAB ở Trang chủ; khai báo toàn bộ route (xem dưới).

☐ Chọn ảnh và nén ảnh, tái dùng từ luồng chụp ảnh tin giả hiện có, trả ra Uri/File để upload multipart.

☐ Cấu hình build debug/release (base URL), CI chạy lint và unit test.

☐ Triển khai PageLoader dùng chung trong core/ theo đặc tả bên dưới, kèm unit test.

☐ Bộ FakeApi/MockWebServer kèm JSON mẫu lấy từ swagger để các module khác dùng.

Route cần khai báo: home, post/{id}, post/{id}/comments, post/new?title=&imageUrl=&body=, post/{id}/edit, search, search/users, factcheck?imageUrl=&postId=, profile/{userId}, profile/{userId}/followers, profile/{userId}/following, login, register, forgot-password, reset-password, settings, about, admin (M10).

### Đặc tả PageLoader dùng chung trong core/ (M0, đợt 1)

- Giao diện lấy dữ liệu: `fetch(page, callback)` trả `PageResult` gồm `items`, `currentPage` và `hasNext`; `hasNext = links.next != null`.
- Gọi theo tham số `page` bắt đầu từ 1; không đi theo URL trong `links` (đang là `http://`).
- Có `refresh()` và `loadNext()`; gắn vào RecyclerView để tự gọi `loadNext()` khi còn khoảng 3 item tới cuối danh sách.
- Phát trạng thái qua LiveData: đang tải lần đầu, đã tải, đang tải thêm, lỗi (có nút thử lại), rỗng, đã hết.
- Khử trùng item theo `id`; bỏ qua phản hồi cũ khi bộ lọc đổi bằng mã thế hệ của yêu cầu.
- Có unit test. Dùng cho M2, M4 (bình luận gốc và phản hồi), M6 (follower, following, bài của người dùng), M10 (các hàng đợi).

Hoàn thành khi: app chạy bản rỗng có bottom nav; gọi được GET /api/categories trên cả emulator và server Render; 401 đưa về màn đăng nhập; PageLoader có unit test cho refresh, loadNext, trạng thái lỗi/rỗng/hết, khử trùng id và bỏ qua phản hồi cũ; README hướng dẫn thêm package feature/ mới.

### Đặc tả PageLoader dùng chung trong core/ (M0, đợt 1)

- API lấy dữ liệu: `fetch(page, callback)` trả `PageResult` gồm `items`, `currentPage`, `hasNext`; `hasNext = links.next != null`.
- Trang bắt đầu từ 1; tự tăng tham số `page`, không đi theo URL trong `links` vì URL hiện là `http://`.
- Có `refresh()` và `loadNext()`; gắn với RecyclerView để tự gọi `loadNext()` khi còn khoảng 3 item tới cuối danh sách.
- Phát trạng thái qua LiveData: đang tải lần đầu, đã tải, đang tải thêm, lỗi (có nút thử lại), rỗng, đã hết.
- Khử trùng item theo `id`; bỏ qua phản hồi cũ khi bộ lọc đổi bằng mã thế hệ của yêu cầu.
- Có unit test. Dùng cho M2, M4 (bình luận gốc và phản hồi), M6 (follower, following, bài của người dùng), M10 (các hàng đợi).

## M1 – Xác thực

M1 lo đăng ký, đăng nhập và duy trì phiên; các module khác chỉ dùng SessionManager của M0, không tự xử lý token.

| Chức năng | Endpoint | Ghi chú |
| --- | --- | --- |
| Đăng ký | POST /api/register | name ≤255, email, password ≥8, password_confirmation; trả {user, token} |
| Đăng nhập | POST /api/login | email, password, remember?; có kiểm tra tài khoản bị cấm; trả {user, token} |
| Đăng xuất | POST /api/logout | Gọi SessionManager.logout() của M0, luôn xóa token cục bộ kể cả khi API lỗi |
| Quên mật khẩu | POST /api/forgot-password | Gửi email, trả status |
| Đặt lại mật khẩu | POST /api/reset-password | token, email, password, password_confirmation |
| Xác thực email | GET /api/verify-email/{id}/{hash} | Link nằm trong email, mở bằng trình duyệt (xem backend #6) |
| Gửi lại thư xác thực | POST /api/email/verification-notification | Cần token |
| OAuth Google, GitHub | GET /api/login/{google,github}/redirect và /callback | Phụ thuộc backend #2 |

Việc cần làm

☐ Màn Đăng nhập và Đăng ký, kiểm tra phía client (email hợp lệ, mật khẩu ≥8, xác nhận khớp), hiển thị lỗi 422 đúng từng ô.

☐ Lưu {user, token} qua SessionManager; sau khi đăng nhập quay lại màn và hành động đang chờ.

☐ Tài khoản bị cấm: hiện thông báo từ server, kèm banned_until nếu có.

☐ Banner "Chưa xác thực email" với nút "Gửi lại thư" (đếm ngược 60 giây giữa hai lần bấm) và nút "Tôi đã xác thực" (gọi GET /api/user để kiểm tra, cần email_verified_at, backend #3).

☐ Quên và đặt lại mật khẩu: mở bằng deep link socialapp://reset-password?token=…&email=…, kèm ô dán mã thủ công làm phương án dự phòng.

☐ OAuth Google/GitHub bằng Chrome Custom Tabs + deep link nhận token; bọc sau feature flag cho đến khi backend #2 xong.

☐ Bottom sheet "Cần đăng nhập" dùng chung cho khách.

☐ Không log token; test các ca: sai mật khẩu, tài khoản bị cấm, 422, mất mạng.

Hoàn thành khi: đăng ký rồi vào app bằng token mới, đăng xuất, quên mật khẩu chạy trọn luồng với email thật, mọi lỗi 422 hiển thị đúng ô.

## M2 – Bảng tin và chuyên mục

M2 là màn hình người dùng thấy đầu tiên: danh sách bài viết có phân trang, lọc theo chuyên mục, sắp xếp và tìm kiếm theo tiêu đề.

| Chức năng | Endpoint | Tham số |
| --- | --- | --- |
| Danh sách bài | GET /api/posts | sort (newest, hot), limit (1–50, mặc định 10), category, q (≤255), user_id, feed (all, following), page (từ 1; swagger chưa ghi nhưng server đã hỗ trợ) |
| Danh sách chuyên mục | GET /api/categories | — |
| Chi tiết chuyên mục | GET /api/categories/{category} | — |

Việc cần làm

☐ Trang chủ: ba tab "Mới nhất", "Nổi bật", "Đang theo dõi"; hàng chip chuyên mục cuộn ngang (chip đầu là "Tất cả").

☐ Dùng PageLoader trong core/ cho GET /api/posts: fetch(page, callback) trả PageResult gồm items, currentPage và hasNext (hasNext = links.next != null); bắt đầu page=1, tăng số trang theo tham số page và dừng khi links.next là null; không đi theo URL trong links vì đang là http://. Gửi limit=10; đổi sort, category, q hoặc feed thì tải lại từ đầu; khử trùng bài theo id, nhất là khi sort=hot.

☐ Tab "Đang theo dõi" dùng feed=following; khách bấm vào thì hiện bottom sheet đăng nhập.

☐ Màn Tìm kiếm: ô nhập có debounce 400 ms gọi q, nút chuyển sang search/users (màn thuộc package feature/profile của M6).

☐ Thẻ bài viết (PostCard của M0): ảnh thumbnail_url, tiêu đề, chuyên mục, tác giả, vote_score, comments_count, thời gian tương đối, source_name nếu có. Chạm vào mở post/{id}.

☐ Chèn khe quảng cáo AdSlot("feed_inline") sau mỗi 5 bài (nội dung do M8 cung cấp).

☐ Kéo để làm mới, giữ vị trí cuộn khi quay lại từ chi tiết, làm mới thẻ khi quay về.

☐ Trạng thái rỗng, lỗi, đang tải (skeleton); FAB "Viết bài" mở post/new, khách thì yêu cầu đăng nhập.

Lưu ý: q chỉ tìm theo tiêu đề; đừng hứa tìm theo nội dung trên giao diện. Phân trang đã chạy đúng trên server (xem backend #1), chỉ là swagger chưa ghi page, links và meta.

Hoàn thành khi: cuộn hết ít nhất 3 trang không trùng bài, đổi tab/chuyên mục/từ khóa cho kết quả đúng, khách và người đã đăng nhập đều dùng được, test với danh sách rỗng và mất mạng.

## M3 – Chi tiết bài viết và tương tác

M3 hiển thị nội dung bài (content_html) và các thao tác ngay trên bài: bầu chọn, theo dõi tác giả, báo cáo, chia sẻ, kiểm tra tin giả.

| Chức năng | Endpoint | Ghi chú |
| --- | --- | --- |
| Chi tiết bài | GET /api/posts/{post} | Có content_html, user_vote, is_following_author nếu gửi token |
| Upvote | POST /api/posts/{post}/upvote | Toggle; trả vote_score, user_vote (1 hoặc 0) |
| Downvote | POST /api/posts/{post}/downvote | Toggle; trả vote_score, user_vote (-1 hoặc 0) |
| Báo cáo bài | POST /api/posts/{post}/report | reason 10–500 ký tự |
| Theo dõi tác giả | POST /api/users/{user}/follow | Gọi thẳng API đã sinh trong package mạng của core/, không import code của M6 |

Việc cần làm

☐ Màn chi tiết: ảnh đầu bài, tiêu đề, chuyên mục, tác giả (chạm mở profile/{userId}), thời gian, nguồn (source_name, mở source_url bằng Custom Tabs).

☐ Hiển thị content_html bằng WebView: tắt JavaScript, chặn mọi điều hướng ra ngoài (link mở bằng Custom Tabs), chèn CSS để ảnh max-width:100%, chữ và nền theo theme sáng/tối, chiều cao tự co theo nội dung.

☐ Thanh vote (upvote/downvote): cập nhật lạc quan, sau đó lấy vote_score và user_vote từ phản hồi server làm chuẩn vì chưa biết server xử lý chuyển từ downvote sang upvote thế nào.

☐ Nút Theo dõi tác giả theo is_following_author, cập nhật từ is_following, followers_count trong phản hồi.

☐ Menu ba chấm: người khác thấy "Báo cáo" (mở ReportSheet); tác giả thấy "Sửa bài" (mở post/{id}/edit của M5).

☐ Nút "Bình luận (n)" mở post/{id}/comments của M4.

☐ Nút "Kiểm tra tin giả" mở factcheck?imageUrl=<thumbnail_url>&postId=<id> của M9; ẩn nếu bài không có ảnh.

☐ Chia sẻ qua Intent (tiêu đề + link web của bài; cần chốt mẫu URL web vì swagger chưa có).

☐ Màn lỗi 404 hiển thị message của server ("Nội dung này không tồn tại hoặc đã bị gỡ bỏ.") kèm nút quay lại.

☐ Khe quảng cáo AdSlot("post_detail_bottom") dưới bài.

Lưu ý: việc xóa bài thuộc M5 (nút nằm ở màn sửa bài), không đặt ở đây để tránh hai nơi cùng sở hữu một endpoint. Xử lý HTML an toàn phụ thuộc backend #8.

Hoàn thành khi: bài có ảnh, bảng, danh sách và link hiển thị đúng ở cả theme sáng/tối; vote và follow đúng khi bấm liên tiếp nhanh; khách bấm hành động ghi thì được đưa đi đăng nhập rồi quay lại đúng bài.

## M4 – Bình luận

M4 làm toàn bộ khu bình luận của một bài: xem, trả lời nhiều cấp, sửa, xóa và báo cáo.

| Chức năng | Endpoint | Ghi chú |
| --- | --- | --- |
| Bình luận gốc | GET /api/posts/{post}/comments | limit 1–50 (mặc định 10), page (từ 1; swagger chưa ghi); mỗi mục có replies_count |
| Phản hồi của một bình luận | GET /api/comments/{comment}/replies | Có phân trang bằng page, mặc định 5 mỗi trang (swagger chưa ghi); tải khi bấm "Xem n phản hồi", bấm "Xem thêm" để lấy trang sau |
| Thêm bình luận hoặc trả lời | POST /api/comments | content, post_id, parent_id?; trả 201 kèm CommentResource |
| Sửa | PATCH /api/comments/{comment} | Chỉ tác giả |
| Xóa | DELETE /api/comments/{comment} | 204; chỉ tác giả |
| Báo cáo | POST /api/comments/{comment}/report | reason 10–500 ký tự |

Việc cần làm

☐ Màn post/{id}/comments: danh sách bình luận gốc dùng PageLoader của core/ theo page (dừng khi links.next là null, không đi theo URL trong links vì đang là http://), mỗi bình luận hiển thị avatar, tên, thời gian, nội dung, số phản hồi.

☐ Phản hồi tải lười qua /replies bằng PageLoader của core/ theo page (5 mỗi trang): bấm "Xem n phản hồi" lấy trang 1, còn links.next thì hiện "Xem thêm phản hồi" để lấy trang sau; thụt lề tối đa 2 cấp, các cấp sâu hơn hiển thị phẳng và thêm tiền tố "@tên" của người được trả lời.

☐ Ô nhập cố định ở đáy; khi trả lời ai đó hiện chip "Đang trả lời X" có nút hủy; gửi xong chèn ngay bình luận từ phản hồi 201.

☐ Menu ba chấm: tác giả (so author.id với user trong session) thấy "Sửa" và "Xóa", người khác thấy "Báo cáo" (dùng ReportSheet).

☐ Sửa tại chỗ bằng PATCH; hiện nhãn "đã sửa" khi updated_at khác created_at.

☐ Xóa có hộp xác nhận; sau 204 tải lại nhánh cha để phản ánh đúng trạng thái server.

☐ Khách thấy ô nhập "Đăng nhập để bình luận"; mở bottom sheet đăng nhập khi chạm.

☐ Bình luận có status khác published (nếu server trả về) hiển thị "Bình luận đã bị gỡ" và ẩn nội dung.

Lưu ý: swagger chưa ghi giới hạn độ dài content và hành vi khi xóa bình luận đã có phản hồi (xóa cả nhánh hay giữ lại); tạm giới hạn 2000 ký tự phía client và hỏi lại backend. Phân trang đã chạy đúng trên server (xem backend #1): limit áp dụng cho bình luận gốc, còn /replies tự phân trang 5 mỗi trang; chưa rõ /replies có nhận limit không.

Hoàn thành khi: trả lời lồng 3 cấp hiển thị đúng; thêm, sửa, xóa phản ánh ngay trên giao diện và khớp với server sau khi tải lại; bài bị gỡ (404) báo lỗi gọn thay vì treo.

## M5 – Soạn, sửa và xóa bài viết

M5 là module khó nhất và nặng nhất, vì phải tạo content_html từ một trình soạn thảo trên di động; việc đầu tiên là chốt trình soạn thảo bằng một spike một ngày.

| Chức năng | Endpoint | Ghi chú |
| --- | --- | --- |
| Tạo bài | POST /api/posts | title ≤255, content_html, category_id bắt buộc; thumbnail_url ≤500 ký tự, tùy chọn; trả 201 |
| Sửa bài | PUT /api/posts/{post} | Cùng trường như tạo; 403 nếu không phải tác giả |
| Xóa bài | DELETE /api/posts/{post} | 204; 403 nếu không phải tác giả |
| Upload ảnh | POST /api/image-upload | multipart, field file, tối đa 5MB, jpeg/png/jpg/gif; trả {location} |
| Chuyên mục (để chọn) | GET /api/categories | Dùng lại API của M2 |

Quyết định trình soạn thảo (spike 1 ngày, làm đầu tiên)

Phương án A: WebView chứa Quill hoặc TinyMCE đóng gói trong assets, cầu nối Java–JavaScript để upload ảnh. HTML đầu ra giống bản web. Định dạng {location} của /api/image-upload đúng chuẩn trình upload của TinyMCE nên nhiều khả năng web đang dùng TinyMCE.

Phương án B: trình soạn thảo native tương thích Java/XML; chỉ chọn nếu chèn ảnh giữa bài và HTML đầu ra hiển thị đúng ở M3.

Tiêu chí chọn: chèn được ảnh giữa bài và xuất HTML hiển thị đúng ở M3 (đậm, nghiêng, tiêu đề, danh sách, link, trích dẫn, ảnh). Mặc định chọn A nếu B không đạt cả hai tiêu chí.

Việc cần làm

☐ Spike trình soạn thảo và ghi kết luận vào README module.

☐ Màn post/new: tiêu đề (đếm ký tự, ≤255), chuyên mục (bắt buộc), ảnh bìa (tùy chọn), nội dung. Route nhận thêm tham số tùy chọn title, imageUrl, body (văn bản thuần ngắn) để M9 điền sẵn.

☐ Chọn ảnh, nén xuống dưới 5MB, upload qua /api/image-upload với thanh tiến trình và nút hủy; gán location vào thumbnail_url hoặc chèn vào thân bài.

☐ Lưu nháp cục bộ khi thoát giữa chừng và khôi phục khi quay lại; hỏi xác nhận khi thoát có thay đổi chưa lưu.

☐ Đăng bài: kiểm tra phía client, xử lý 201 (mở post/{id}), 422 (lỗi từng ô), 403 email chưa xác thực (dialog dẫn tới banner của M1), 401 (đăng nhập).

☐ Màn post/{id}/edit: tải bài bằng GET /api/posts/{id}, điền sẵn, lưu bằng PUT.

☐ Nút "Xóa bài viết" ở cuối màn sửa, có hộp xác nhận; sau 204 quay về Trang chủ.

Lưu ý: không có API xóa ảnh đã upload nên xóa bài không dọn ảnh trên Cloudinary. Lọc HTML phụ thuộc backend #8; email chưa xác thực phụ thuộc backend #4.

Hoàn thành khi: đăng một bài có ảnh bìa và ảnh trong thân, mở lại trên web và app đều hiển thị đúng; sửa và xóa chỉ thấy với tác giả; thoát giữa chừng không mất nội dung.

## M6 – Hồ sơ và mạng xã hội

M6 làm trang cá nhân (của mình và của người khác), theo dõi, danh sách người theo dõi và tìm người dùng.

| Chức năng | Endpoint | Ghi chú |
| --- | --- | --- |
| Hồ sơ | GET /api/profiles/{user} | Có followers_count, following_count, posts_count, is_following, role |
| Người theo dõi | GET /api/profiles/{user}/followers | Có phân trang bằng page (mặc định 15 mỗi trang, swagger chưa ghi); mảng UserResource |
| Đang theo dõi | GET /api/profiles/{user}/following | Phân trang như followers (chưa kiểm tra riêng endpoint này); mảng UserResource |
| Tìm người | GET /api/users/search | q 2–100 ký tự, limit 1–20 (mặc định 5); response cũng có phân trang nhưng chỉ cần trang đầu |
| Theo dõi / bỏ theo dõi | POST /api/users/{user}/follow | Toggle; trả is_following, followers_count; 422 khi tự theo dõi mình |
| Báo cáo người dùng | POST /api/users/{user}/report | reason 10–500 ký tự |
| Bài của người dùng | GET /api/posts?user_id= | Dùng lại API của M2, gọi thẳng qua package mạng trong core/ |

Việc cần làm

☐ Màn profile/{userId}: ảnh bìa, avatar, tên, nhãn vai trò (chỉ hiện với moderator trở lên), ba số liệu (bài viết, người theo dõi, đang theo dõi) chạm vào mở danh sách tương ứng.

☐ Tab Hồ sơ trên bottom nav mở hồ sơ của chính mình (lấy id từ session); khách thì thấy lời mời đăng nhập.

☐ Hồ sơ của mình có nút "Cài đặt" mở settings (M7); hồ sơ người khác có nút Theo dõi theo is_following, cập nhật lạc quan và đồng bộ lại followers_count từ phản hồi.

☐ Danh sách bài của người dùng: lọc bằng user_id và dùng PageLoader của core/ theo page giống M2 (dừng khi links.next là null), dùng PostCard của M0.

☐ Màn profile/{userId}/followers và /following: cuộn vô hạn bằng PageLoader của core/ theo page (15 mỗi trang), mỗi dòng gồm avatar và tên, chạm mở hồ sơ. Vì UserResource không có is_following nên không đặt nút Theo dõi trên từng dòng.

☐ Màn search/users: ô nhập có debounce 400 ms, chỉ gọi khi đủ 2 ký tự, chỉ lấy trang đầu và hiển thị tối đa 20 kết quả.

☐ Menu ba chấm trên hồ sơ người khác: "Báo cáo người dùng" qua ReportSheet.

☐ Ẩn nút Theo dõi và Báo cáo trên hồ sơ của chính mình.

Lưu ý: phân trang danh sách follower đã chạy đúng trên server (xem backend #1), chỉ là swagger chưa ghi; swagger chưa có bio hay mô tả cá nhân nên giao diện chưa có chỗ cho trường này.

Hoàn thành khi: theo dõi rồi bỏ theo dõi hai lần liên tiếp cho số đếm đúng; hồ sơ của mình và của người khác đều mở được; tìm người với 1 ký tự không gọi API.

## M7 – Cài đặt tài khoản

M7 cho người dùng sửa thông tin cá nhân, đổi ảnh, đổi mật khẩu, xóa tài khoản và chọn giao diện sáng/tối.

| Chức năng | Endpoint | Ghi chú |
| --- | --- | --- |
| Lấy tài khoản hiện tại | GET /api/user | Làm mới session.user khi mở app |
| Đổi tên | PATCH /api/profile/details | name ≤50 ký tự |
| Đổi mật khẩu | PATCH /api/profile/password | current_password, password ≥8, password_confirmation |
| Đổi avatar | POST /api/user/avatar | multipart, field avatar, tối đa 5MB, jpg/png |
| Đổi ảnh bìa | POST /api/user/cover-photo | multipart, field cover, tối đa 10MB, jpeg/png/gif |
| Xóa tài khoản | DELETE /api/profile/delete | Body JSON {password} |

Việc cần làm

☐ Màn settings chia nhóm: Thông tin cá nhân, Bảo mật, Giao diện, Về ứng dụng (mở about của M8), Đăng xuất, Xóa tài khoản.

☐ Đổi tên với kiểm tra ≤50 ký tự; sau khi thành công cập nhật session.user để mọi màn hình đổi theo.

☐ Đổi avatar và ảnh bìa: chọn ảnh, cắt (vuông cho avatar, 16:9 cho ảnh bìa), nén theo giới hạn dung lượng, upload với tiến trình; cập nhật session.user từ phản hồi.

☐ Đổi mật khẩu: ba ô, lỗi 422 của current_password hiện tại đúng ô; thành công thì hiện thông báo từ server.

☐ Xóa tài khoản: vùng nguy hiểm, bắt nhập mật khẩu và gõ lại chữ "XOA" để xác nhận; thành công thì xóa session và về Trang chủ ở chế độ khách.

☐ Giao diện sáng/tối/theo hệ thống, lưu bằng cơ chế SharedPreferences mã hóa bằng Android Keystore.

☐ Nút Đăng xuất gọi SessionManager.logout() của M0.

☐ Chừa một mục "Quản trị" ẩn mặc định để M10 gắn vào sau.

Lưu ý kỹ thuật: DELETE có body nên khai báo Retrofit bằng @HTTP(method = "DELETE", hasBody = true), nếu không sẽ mất password. Giới hạn tên không nhất quán (đăng ký cho tới 255 ký tự, đổi tên chỉ 50), vì vậy giới hạn 50 ký tự ở cả M1 và M7 và báo backend thống nhất.

Hoàn thành khi: đổi avatar xong thì feed, bình luận và hồ sơ đều hiện ảnh mới không cần khởi động lại; xóa tài khoản với mật khẩu sai báo lỗi đúng ô và không đăng xuất.

## M8 – Quảng cáo và cấu hình hệ thống

M8 là module nhỏ nhất: hiển thị banner quảng cáo theo vị trí và đọc logo, ảnh nền, chân trang do admin cấu hình trên web.

| Chức năng | Endpoint | Ghi chú |
| --- | --- | --- |
| Quảng cáo theo vị trí | GET /api/advertisements?position= | position bắt buộc, ≤100 ký tự; trả id, image_url, link_url, position |
| Logo | GET /api/settings/logo | {logo_url}, có thể null |
| Ảnh nền | GET /api/settings/background | {background_url}, có thể null |
| Chân trang | GET /api/settings/footer | description, copyright, links[], socials[] |

Việc cần làm

☐ AdSlot(position): gọi API theo vị trí, nhớ kết quả trong bộ nhớ 5 phút, hiển thị banner bằng Glide với nhãn "Quảng cáo", chạm mở link_url bằng Custom Tabs. Không có quảng cáo thì không chiếm chỗ.

☐ Vị trí dùng trong app: feed_inline (M2) và post_detail_bottom (M3); chốt danh sách chính thức với backend (#7).

☐ Logo: hiển thị trên màn khởi động và thanh trên của Trang chủ; dùng logo mặc định trong app khi null hoặc lỗi.

☐ Ảnh nền: dùng làm nền màn Đăng nhập/Chào mừng với lớp phủ tối để dễ đọc; không dùng làm nền các màn đọc bài. Bỏ qua nếu thiết kế không cần.

☐ Màn about: mô tả, bản quyền, danh sách liên kết và mạng xã hội từ footer.

☐ Tải logo, nền, footer một lần khi khởi động app (song song); lỗi thì dùng mặc định và không chặn người dùng vào app.

Lưu ý: phần tử của links và socials trong swagger chỉ ghi object; lấy một response thật để dựng model rồi nhờ backend cập nhật swagger. Không có API ghi nhận lượt hiển thị hay nhấp quảng cáo.

Hoàn thành khi: quảng cáo xuất hiện đúng vị trí và biến mất gọn khi không có; tắt mạng thì app vẫn dùng logo mặc định; màn about hiển thị đủ liên kết.

## M9 – Kiểm chứng tin giả

M9 không thêm endpoint nào từ swagger; việc chính là tách tính năng nhận biết tin giả hiện có thành module riêng rồi nối nó với bài viết, vì đây là điểm khác biệt của app so với các app đọc tin thông thường.

Việc cần làm

☐ Chuyển code nhận diện tin giả hiện có (chọn ảnh, chụp ảnh) vào feature/factcheck, giữ nguyên hành vi và dịch vụ gọi bên dưới; viết test hồi quy cho hai luồng cũ trước khi sửa gì khác.

☐ Tab "Kiểm chứng" (route factcheck): nút "Chọn ảnh", nút "Chụp ảnh", màn kết quả như hiện tại.

☐ Nhận tham số imageUrl (và postId nếu có) từ M3: tải ảnh về bộ nhớ đệm, resize nếu lớn, chạy cùng pipeline rồi hiện kết quả.

☐ Nút "Đăng thảo luận" ở màn kết quả: nếu ảnh là ảnh người dùng chọn hoặc chụp thì upload qua POST /api/image-upload trước (gọi API đã sinh trong package mạng của core/), sau đó mở post/new?title=&imageUrl=&body= với tiêu đề gợi ý, ảnh và tóm tắt kết quả bằng văn bản thuần ngắn. Khách thì yêu cầu đăng nhập.

☐ Khi mở từ một bài viết (postId có giá trị), màn kết quả có nút "Báo cáo bài viết" mở ReportSheet với lý do điền sẵn (người dùng sửa được) rồi gọi POST /api/posts/{post}/report. Việc báo cáo luôn do người dùng bấm, không tự động.

☐ Lịch sử kiểm tra lưu cục bộ bằng SQLite (ảnh thu nhỏ, kết quả, thời gian), xem lại và xóa được; không cần backend.

☐ Dòng nhắc cố định trên màn kết quả: kết quả chỉ mang tính tham khảo.

☐ Chia sẻ kết quả (ảnh và văn bản) qua Intent.

Cần người làm cung cấp: định dạng kết quả hiện tại của bộ nhận diện (nhãn, độ tin cậy, có lời giải thích hay không) để chốt nội dung tóm tắt và cách hiển thị.

Hoàn thành khi: hai luồng cũ chạy y như trước khi tách; kiểm tra từ ảnh bìa của một bài viết trả kết quả; "Đăng thảo luận" tạo được bài mới với ảnh đã upload; lịch sử còn nguyên sau khi tắt app.

## M10 – Kiểm duyệt và quản trị trong app (giai đoạn 3)

M10 gồm 31 thao tác API, nhưng chỉ nên đưa vào app phần xử lý báo cáo, khôi phục nội dung và cấm người dùng; phần quản lý quảng cáo, chuyên mục, cấu hình và phân quyền để trên trang web quản trị, vì form phức tạp và dễ thao tác nhầm trên điện thoại.

| Nhóm | Endpoint | Làm trong app |
| --- | --- | --- |
| Đăng nhập quản trị | POST /api/admin/login | Có |
| Thống kê | GET /api/moderator/dashboard/stats | Có (chỉ xem) |
| Báo cáo bài, bình luận, người dùng | GET /api/moderator/reports/{posts,comments,users} và DELETE …/{id} | Có |
| Nội dung đã gỡ | GET /api/moderator/content/removed-{posts,comments}, POST /api/moderator/{posts,comments}/{id}/restore | Có |
| Cấm người dùng | GET /api/admin/users/banned, POST /api/admin/users/{user}/ban (duration_days 1–3650, bỏ trống là vĩnh viễn), POST …/unban, GET …/moderation-history | Có |
| Quảng cáo | GET, POST, POST {id} (cập nhật dạng form-data), DELETE {id} dưới /api/admin/advertisements | Để web |
| Chuyên mục | GET, POST, GET {id}, PUT/PATCH {id}, DELETE {id} dưới /api/admin/categories | Để web |
| Cấu hình | POST /api/admin/settings/{logo,background,footer} | Để web |
| Superadmin | GET /api/superadmin/adminlist, PATCH /api/superadmin/users/{user}/role | Để web |

Việc cần làm

☐ Màn admin/login, dùng chung SessionManager; lối vào đặt trong Cài đặt, chỉ hiện khi tài khoản có vai trò từ moderator trở lên (cần role, backend #3).

☐ Dashboard: các số đếm (counts) hiển thị dạng thẻ. growth và top_content trong swagger chỉ ghi object, cần một response thật để dựng model.

☐ Ba hàng đợi báo cáo (bài, bình luận, người dùng) dạng tab, dùng PageLoader của core/ để phân trang và lọc theo user; mỗi thẻ có lý do, người báo cáo, thời gian, bằng chứng (chạm xem đầy đủ) và nút "Bỏ qua báo cáo" gọi DELETE.

☐ Nội dung đã gỡ (hai tab) có nút "Khôi phục"; lỗi 422 khi nội dung đang hiển thị bình thường thì báo "đã hiển thị".

☐ Người dùng bị cấm: danh sách và "Gỡ cấm"; hành động "Cấm" chọn số ngày hoặc vĩnh viễn, có bước xác nhận thứ hai cho cấm vĩnh viễn; 403 khi cấm Super Admin hiển thị đúng thông báo từ server.

☐ Lịch sử kiểm duyệt của người dùng: bài bị gỡ, bình luận bị gỡ, báo cáo đang hoạt động.

☐ Ẩn hoặc hiện chức năng theo role để dễ dùng, nhưng coi 403 từ server là nguồn quyết định.

Lưu ý quan trọng: DELETE báo cáo chỉ đóng báo cáo, không gỡ nội dung. Cho đến khi backend trả lời điểm #5 (moderator gỡ bài, bình luận bằng endpoint nào), đừng làm nút "Gỡ nội dung".

Hoàn thành khi: moderator đăng nhập, xem và xử lý báo cáo, khôi phục nội dung; admin cấm 7 ngày rồi gỡ cấm; tài khoản thường không thấy lối vào quản trị; gọi sai quyền hiển thị lỗi 403 gọn.

## Lộ trình theo giai đoạn

Ước đoán: do M0 (bao gồm PageLoader) khoảng 7–8 người-ngày, cần dự trù 1–2 tuần đầu cho nền tảng và chuẩn bị. Sau khi M0 merge, ba luồng chạy song song; M0–M9 dự kiến xong trong khoảng 5–6 tuần, M10 và hoàn thiện ở tuần 6–8.

lộ trình ước đoán · 4 giai đoạn, 3 luồng, 3 cổng · M0–M9: 5–6 tuần; M10 và QA: tuần 6–8

**Lưu ý:** Sơ đồ hình nhúng gốc chưa được chỉnh sửa; các nhãn “sau tuần 1”, “M0–M9 xong sau 5 tuần”, “Tuần 1”, “Tuần 2–3”, “Tuần 4–5”, “Tuần 6–7” trong hình không còn khớp hoàn toàn với ước lượng đã cập nhật. Cần vẽ lại hình nếu muốn sơ đồ trực quan đồng bộ.

![Sơ đồ lộ trình gốc — chưa cập nhật](PLAN-roadmap-original.png)

Luồng A đi đường khó nhất (M0, rồi M1 và M8, rồi M5); luồng B làm các màn đọc và xã hội; luồng C làm bình luận, kiểm chứng và dành giai đoạn cuối cho quản trị.

Quy tắc để chạy song song không đạp chân nhau

Mỗi phần việc/module một nhánh git, PR nhỏ, merge vào develop; chỉ chủ M0 sửa core/ và file khai báo route. Module khác cần sửa core thì mở PR riêng, nhỏ, nhờ chủ M0 duyệt.

Mỗi module phải chạy được độc lập với FakeApi trước khi nối server thật.

Ở mỗi cổng, cả nhóm chạy app trên server Render và đối chiếu mục "Hoàn thành khi" của từng module.

Nếu chỉ có 1–2 người, giữ thứ tự M0, M1, M2, M3, M4, M9, M5, M6, M7, M8, M10 và cắt từ cuối lên khi thiếu thời gian (M10 trước, rồi M8, M7).

## Mẫu brief để giao một module

Dùng cùng một mẫu cho mọi module, đổi các phần trong dấu <>, và đính kèm swagger.json cùng mục tương ứng của kế hoạch này; giao cho AI thì mỗi module một phiên làm việc riêng.

# Brief giao việc: <Mx – Tên module>

## Bối cảnh
App Android đọc tin, 100% Java, Android Views (XML + ViewBinding), kết nối backend Laravel 9 dùng Sanctum.
Tài liệu API là swagger.json đính kèm. Bạn chỉ làm module <Mx>, không sửa module khác.

## Đọc trước
1. Mục "Kiến trúc và quy ước kỹ thuật" của kế hoạch
2. Mục "<Mx>" của kế hoạch: endpoint, việc cần làm, hoàn thành khi
3. Điểm backend liên quan: #<n>, #<n>

## Phạm vi
- Làm: toàn bộ "Việc cần làm" của <Mx>
- Không làm: <các việc thuộc module khác, lấy từ mục Lưu ý>

## Ràng buộc
- Chỉ phụ thuộc package core/; điều hướng bằng route đã khai báo ở M0
- 100% Java; UI dùng XML + ViewBinding, Fragment, RecyclerView/ListAdapter + DiffUtil; không Java, Compose, coroutine hoặc luồng trạng thái Java.
- Kiến trúc MVVM: ViewModel + LiveData + Repository; Retrofit dùng Call.enqueue, kết quả qua LiveData<ApiResult<T>> hoặc callback trên main thread.
- DI dùng AppContainer viết tay trong Application; không framework DI tự động.
- Mọi request dùng Retrofit `Call.enqueue`; Repository trả kết quả qua `LiveData<ApiResult<T>>` hoặc callback, callback chạy trên main thread.
- `SessionManager` phát `LiveData<Session>`; `requireLogin(Runnable)` là helper cho hành động cần đăng nhập.
- `PageLoader` là lớp dùng chung trong core/; mọi màn phân trang liên quan dùng lớp này, không tự tạo cơ chế riêng.
- Dùng DTO và API interface sinh từ swagger bằng openapi-generator (generator java, library retrofit2, serializationLibrary gson); không gõ tay model.
- Phân trang dùng PageLoader của core/, không phân trang tự động; ảnh code mới dùng Glide.
- Chạy được với FakeApi trước khi nối server thật; có unit test cho ViewModel và Repository
- Mọi chuỗi nằm trong strings.xml, bằng tiếng Việt
- Nếu swagger thiếu hoặc khác response thật: dừng ở điểm đó, ghi vào mô tả PR, không đoán

## Bàn giao
- Một nhánh feature/<module>, PR nhỏ vào develop
- Mô tả PR gồm: ảnh chụp màn hình, danh sách "Hoàn thành khi" đã đối chiếu, điểm còn chưa chắc chắn

## Prompt giao việc theo từng module

Mỗi module có một prompt viết sẵn dưới đây; dán vào một phiên làm việc riêng theo thứ tự: prompt 0, rồi M0, rồi M1–M9 (song song), cuối cùng M10.

Chuẩn bị một lần

Đặt kế hoạch này vào repo thành docs/PLAN.md (xuất Markdown từ doc) và đặt swagger thành docs/swagger.json.

Dán khối "Quy ước chung" bên dưới vào CLAUDE.md hoặc AGENTS.md ở gốc repo; nếu không dùng file này thì dán nó vào đầu mỗi phiên.

Mỗi phiên đều bắt đầu bằng bước "viết kế hoạch ngắn rồi đợi duyệt"; đọc kế hoạch đó trước khi cho phép viết code.

### Quy ước chung (dán một lần vào CLAUDE.md hoặc AGENTS.md)

# Quy ước chung cho mọi phiên làm việc

- Nguồn sự thật: docs/PLAN.md (kế hoạch) và docs/swagger.json (API). Khi hai nguồn mâu thuẫn hoặc thiếu thông tin thì dừng và hỏi, không đoán.
- Chỉ làm đúng module được giao và chỉ sửa package feature/<tên> tương ứng trong module :app. Cần sửa core/, file khai báo route hoặc module khác: dừng và báo, không tự sửa.
- 100% Java; UI dùng XML + ViewBinding, Fragment, RecyclerView/ListAdapter + DiffUtil; không Java, Compose, coroutine hoặc luồng trạng thái Java.
- Kiến trúc MVVM: ViewModel + LiveData + Repository; Retrofit dùng Call.enqueue, kết quả qua LiveData<ApiResult<T>> hoặc callback trên main thread.
- DI dùng AppContainer viết tay trong Application; không framework DI tự động.
- Dùng DTO và API interface sinh từ swagger bằng openapi-generator (generator java, library retrofit2, serializationLibrary gson); không gõ tay model.
- Phân trang dùng PageLoader của core/, không phân trang tự động; ảnh code mới dùng Glide..
- Mọi request có Accept: application/json; xử lý lỗi theo mục "Kiến trúc và quy ước kỹ thuật" của kế hoạch.
- Phân trang theo tham số page; không đi theo URL trong links (đang là http://).
- Chuỗi giao diện nằm trong strings.xml, bằng tiếng Việt.
- Không log token hay mật khẩu.
- Trước khi code: viết kế hoạch ngắn (file sẽ tạo hoặc sửa, cách làm) và đợi tôi duyệt.
- Sau khi code: chạy build, lint và unit test; báo đúng kết quả đã chạy, không nói đã chạy khi chưa chạy.
- Bàn giao: nhánh feature/<module>, mô tả thay đổi, đối chiếu từng mục "Hoàn thành khi" của module, liệt kê điểm chưa chắc chắn.

### Prompt 0 – Khám phá repo (không sửa gì)

Chạy đầu tiên, trước M0. Kết quả quyết định có phải sửa mục "Kiến trúc và quy ước kỹ thuật" của kế hoạch hay không.

Tôi sắp mở rộng app Android đọc tin này thành app có tài khoản, bài viết và bình luận.
Hôm nay CHỈ khám phá, KHÔNG sửa hay tạo file nào trong repo.

Đọc docs/PLAN.md (các mục Tổng quan, Kiến trúc và quy ước kỹ thuật, M0, M9) rồi xem repo và báo cáo ngắn gọn:
1. Xác nhận repo dùng 100% Java, XML + ViewBinding, MVVM/ViewModel + LiveData + Repository, AppContainer trong Application, Retrofit/OkHttp/Gson, Glide và Navigation Component; kiểm tra minSdk, targetSdk và cấu trúc một module :app với package core/ và feature/.
2. Tính năng nhận biết tin giả: file nào xử lý chọn ảnh, chụp ảnh, nén ảnh, gọi dịch vụ nhận diện; dịch vụ đó là gì (URL, định dạng request và response, xác thực); kết quả trả về gồm những trường nào.
3. Đối chiếu repo với stack đã chốt (Java, XML + ViewBinding, AppContainer, Retrofit/OkHttp/Gson, PageLoader, Glide); liệt kê từng điểm khác biệt và báo rõ tác động, không tự đổi stack.
4. Rủi ro khi tách code tin giả thành feature/factcheck.
5. Phần nào của M0 đã có sẵn (ví dụ mạng, theme, bottom nav).
6. Những câu hỏi bạn cần tôi trả lời trước khi bắt đầu M0.

Chỉ báo cáo sự thật tìm được, kèm đường dẫn file; đừng đề xuất giải pháp dài.

### Prompt M0 – Nền tảng

Chạy sau prompt 0. Bạn review kỹ và merge xong thì mới giao các module khác.

Làm module M0 (Nền tảng) theo mục "M0" của docs/PLAN.md. Tuân thủ quy ước chung.
Bạn là người duy nhất được sửa package core/ và file khai báo route. Các module sau phụ thuộc vào kết quả này, nên làm kỹ, đừng làm vội.

Trước tiên đọc báo cáo khám phá repo (prompt 0) và nêu những gì đã có sẵn để tái dùng.

Phạm vi: một Gradle module :app, các package core/ và feature/<tên>, mạng, session, theme, component dùng chung, điều hướng, chọn và nén ảnh, PageLoader, FakeApi, CI.

Đã chốt:
- Giữ một module :app; tạo package core/ và feature/<tên>; feature chỉ import core/.
- 100% Java, XML + ViewBinding, Fragment, RecyclerView/ListAdapter + DiffUtil, Material Components, Navigation Component; MVVM + ViewModel + LiveData + Repository.
- DI bằng AppContainer viết tay trong Application; không framework DI tự động.
- Sinh DTO và API interface từ docs/swagger.json bằng openapi-generator (generator java, library retrofit2, serializationLibrary gson) vào package mạng trong core/; viết script sinh lại khi swagger đổi.
- Interceptor Accept: application/json và Authorization; timeout 60 giây (server Render có thể ngủ 30–60 giây ở lần gọi đầu).
- Base URL: debug http://10.0.2.2:8000, release https://socialappbe-8tjc.onrender.com; chỉ bật cleartext cho debug.
- ApiResult và AppError cho 401/403/404/422/429/5xx; 422 giữ fieldErrors theo từng ô.
- SessionManager: lưu token và user, phát LiveData<Session>; 401 toàn cục thì đăng xuất; logout() gọi POST /api/logout và luôn xóa token cục bộ; helper requireLogin(Runnable) cho khách. ApiResult<T> là lớp Java cho trạng thái thành công/lỗi. Lưu token bằng SharedPreferences mã hóa với Android Keystore; kiểm tra tình trạng bảo trì EncryptedSharedPreferences trước khi chọn.
- Component dùng chung: PostCard, UserAvatar, LoadingSkeleton, ErrorState(retry), EmptyState, ConfirmDialog, ReportSheet (lý do 10–500 ký tự, trả kết quả qua lambda), AdSlot(position) mặc định rỗng.
- Bottom nav 4 tab (Trang chủ, Kiểm chứng, Tìm kiếm, Hồ sơ), FAB viết bài ở Trang chủ; khai báo toàn bộ route liệt kê trong mục M0.
- Chọn và nén ảnh: tái dùng từ luồng tin giả hiện có.
- PageLoader trong core/: fetch(page, callback) trả PageResult(items, currentPage, hasNext), hasNext = links.next != null; bắt đầu page=1, refresh(), loadNext(), cuộn gần cuối RecyclerView (ngưỡng khoảng 3 item), trạng thái LiveData (đang tải lần đầu/đã tải/đang tải thêm/lỗi có thử lại/rỗng/đã hết), khử trùng theo id, bỏ qua phản hồi cũ bằng mã thế hệ khi bộ lọc đổi, có unit test. Không theo URL links và không dùng phân trang tự động.
- Repository gọi Retrofit bằng Call.enqueue; trả LiveData<ApiResult<T>> hoặc callback, callback chạy trên main thread.
- FakeApi hoặc MockWebServer kèm JSON mẫu lấy từ swagger cho các module khác dùng.
- Dùng Java, XML + ViewBinding, LiveData/callback, AppContainer, PageLoader, Glide và lưu token mã hóa theo quy ước chung. Giữ PickVisualMedia, CameraX và uCrop hiện có; không thay đổi chúng.

Không làm: màn hình nghiệp vụ của bất kỳ feature nào.
Dừng và báo khi: stack hiện tại khác giả định của plan ở điểm ảnh hưởng nhiều module.

### Prompt M1 – Xác thực

Chạy sau khi M0 merge. Phần OAuth bị chặn bởi backend #2, nên được đặt sau feature flag.

Làm module M1 (Xác thực) trong feature/auth, theo mục "M1" của docs/PLAN.md. Tuân thủ quy ước chung.

Phạm vi: đăng ký, đăng nhập, đăng xuất, quên và đặt lại mật khẩu, xác thực email (banner, gửi lại thư), OAuth Google và GitHub, bottom sheet "Cần đăng nhập" cho khách.

Đã chốt:
- Lưu {user, token} qua SessionManager của M0; sau đăng nhập quay lại màn và hành động đang chờ.
- Đăng xuất gọi SessionManager.logout(), không tự gọi API.
- Giới hạn tên 50 ký tự ở form đăng ký (swagger cho 255 nhưng đổi tên chỉ cho 50).
- Đặt lại mật khẩu: nhận deep link socialapp://reset-password?token=&email= và có ô dán mã thủ công.
- OAuth chạy bằng Chrome Custom Tabs và deep link nhận token, đặt sau feature flag cho đến khi backend hỗ trợ (backend #2). Không viết cách lấy token từ trang HTML bằng WebView.
- Nút "Tôi đã xác thực" gọi GET /api/user; nếu chưa có email_verified_at (backend #3) thì ghi TODO và dùng cờ tạm, không bịa trường.
- Nút "Gửi lại thư" có đếm ngược 60 giây.

Không làm: đăng nhập quản trị (M10), đổi mật khẩu khi đã đăng nhập (M7).
Dừng và báo khi: phản hồi đăng nhập của tài khoản bị cấm, hoặc mã lỗi khi chưa xác thực email, khác với plan.

### Prompt M2 – Bảng tin và chuyên mục

Chạy sau khi M0 merge, song song với các module khác.

Làm module M2 (Bảng tin và chuyên mục) trong feature/feed, theo mục "M2" của docs/PLAN.md. Tuân thủ quy ước chung.

Phạm vi: Trang chủ 3 tab (Mới nhất, Nổi bật, Đang theo dõi), chip chuyên mục, màn Tìm kiếm bài theo tiêu đề, thẻ bài viết, khe quảng cáo.

Đã chốt:
- PageLoader theo page cho GET /api/posts: page bắt đầu từ 1, dừng khi links.next là null, limit=10, không đi theo URL trong links.
- Đổi sort, category, q hoặc feed thì tải lại từ đầu; khử trùng bài theo id (nhất là khi sort=hot).
- Tab Đang theo dõi dùng feed=following; khách bấm thì gọi requireLogin(Runnable) của M0.
- Dùng PostCard của M0; chèn AdSlot("feed_inline") sau mỗi 5 bài (M8 cung cấp nội dung).
- Màn Tìm kiếm có nút sang route search/users (màn thuộc package feature/profile của M6).
- q chỉ khớp tiêu đề; đừng hứa tìm theo nội dung trên giao diện.
- Kéo để làm mới, giữ vị trí cuộn khi quay lại từ chi tiết.

Không làm: chi tiết bài (M3), viết bài (M5), tìm người (M6).
Dừng và báo khi: response thật của GET /api/posts khác swagger ở chỗ nào ngoài phần phân trang đã biết.

### Prompt M3 – Chi tiết bài viết và tương tác

Chạy sau khi M0 merge.

Làm module M3 (Chi tiết bài viết và tương tác) trong feature/post, theo mục "M3" của docs/PLAN.md. Tuân thủ quy ước chung.

Phạm vi: màn post/{id}, hiển thị content_html, upvote và downvote, theo dõi tác giả, báo cáo bài, chia sẻ, nút Kiểm tra tin giả, màn lỗi 404.

Đã chốt:
- Hiển thị content_html bằng WebView: tắt JavaScript, chặn mọi điều hướng ra ngoài (link mở bằng Custom Tabs), CSS cho ảnh max-width:100% và theme sáng/tối. Nếu gặp HTML nghi ngờ (script, iframe), báo tôi.
- Vote: cập nhật lạc quan, sau đó lấy vote_score và user_vote từ phản hồi server làm chuẩn, không tự tính.
- Theo dõi tác giả gọi POST /api/users/{user}/follow qua API đã sinh trong package mạng của core/; không import code của M6.
- Menu ba chấm: người khác thấy Báo cáo (ReportSheet của M0, gọi POST /api/posts/{post}/report); tác giả thấy Sửa bài (mở post/{id}/edit). Không làm nút Xóa (thuộc M5).
- Nút Bình luận mở post/{id}/comments (M4). Nút Kiểm tra tin giả mở factcheck?imageUrl=<thumbnail_url>&postId=<id>, ẩn nếu bài không có ảnh.
- Khe quảng cáo AdSlot("post_detail_bottom").
- Chia sẻ qua Intent; mẫu URL web của bài chưa có, hỏi tôi nếu cần.
- Màn lỗi 404 hiển thị message của server kèm nút quay lại.

Không làm: bình luận (M4), sửa và xóa bài (M5).
Dừng và báo khi: bài có nội dung HTML không hiển thị đúng trong WebView, hoặc response chi tiết bài khác swagger.

### Prompt M4 – Bình luận

Chạy sau khi M0 merge.

Làm module M4 (Bình luận) trong feature/comments, theo mục "M4" của docs/PLAN.md. Tuân thủ quy ước chung.

Phạm vi: màn post/{id}/comments gồm bình luận gốc, phản hồi, thêm, sửa, xóa, báo cáo.

Đã chốt:
- Bình luận gốc: dùng PageLoader của core/ với GET /api/posts/{post}/comments theo page, dừng khi links.next là null, không đi theo URL trong links.
- Phản hồi: dùng PageLoader của core/ với GET /api/comments/{comment}/replies theo page, 5 mỗi trang; nút "Xem n phản hồi" rồi "Xem thêm phản hồi". Chưa rõ endpoint này có nhận limit không, đừng dùng limit cho nó.
- Thụt lề tối đa 2 cấp; các cấp sâu hơn hiển thị phẳng kèm tiền tố "@tên".
- Trả lời: POST /api/comments với parent_id; chèn ngay bình luận từ phản hồi 201.
- Sửa (PATCH) và xóa (DELETE) chỉ cho tác giả (so author.id với user trong session). Sau 204 tải lại nhánh cha.
- Báo cáo dùng ReportSheet của M0, gọi POST /api/comments/{comment}/report.
- Giới hạn tạm 2000 ký tự phía client vì swagger chưa ghi giới hạn.
- Khách chỉ xem; ô nhập hiện "Đăng nhập để bình luận".
- Bình luận có status khác published (nếu server trả về) hiển thị "Bình luận đã bị gỡ" và ẩn nội dung.

Không làm: màn chi tiết bài và báo cáo bài (M3).
Dừng và báo khi: xóa bình luận đã có phản hồi cho kết quả khác dự kiến, hoặc danh sách trả về bình luận có status lạ.

### Prompt M5 – Soạn, sửa và xóa bài viết

Chạy sau khi M0 và M1 merge. Đây là module nặng nhất nên có bước dừng để bạn chọn trình soạn thảo.

Làm module M5 (Soạn, sửa và xóa bài viết) trong feature/compose, theo mục "M5" của docs/PLAN.md. Tuân thủ quy ước chung.

Việc đầu tiên là spike 1 ngày chọn trình soạn thảo.
- Phương án A: WebView chứa Quill hoặc TinyMCE đóng gói trong assets, cầu nối Java–JavaScript để upload ảnh.
- Phương án B: trình soạn thảo native tương thích Java/XML; chỉ chọn nếu chèn ảnh giữa bài và HTML đầu ra hiển thị đúng ở M3.
- Tiêu chí: chèn được ảnh giữa bài, và HTML xuất ra hiển thị đúng ở M3 (đậm, nghiêng, tiêu đề, danh sách, link, trích dẫn, ảnh). Mặc định chọn A nếu B không đạt cả hai tiêu chí.
Trình bày kết quả spike và ĐỢI TÔI CHỌN trước khi làm tiếp.

Phạm vi: post/new, post/{id}/edit, upload ảnh, nháp cục bộ, xóa bài.

Đã chốt:
- POST /api/posts, PUT /api/posts/{post}, DELETE /api/posts/{post}, POST /api/image-upload (field file, tối đa 5MB, trả {location}). title ≤255 ký tự; thumbnail_url ≤500 ký tự.
- Ảnh bìa: upload rồi gán location vào thumbnail_url. Chọn chuyên mục từ GET /api/categories.
- Route post/new nhận tham số tùy chọn title, imageUrl, body (văn bản thuần ngắn) để M9 điền sẵn.
- Lưu nháp cục bộ; hỏi xác nhận khi thoát có thay đổi chưa lưu.
- Xử lý 201 (mở post/{id}), 422 (lỗi từng ô), 403 email chưa xác thực (dẫn tới banner của M1), 401.
- post/{id}/edit tải bài bằng GET /api/posts/{id} và lưu bằng PUT. Nút Xóa bài viết nằm ở cuối màn sửa, có hộp xác nhận.
- Không có API xóa ảnh đã upload, nên xóa bài không dọn ảnh.

Không làm: hiển thị bài (M3).
Dừng và báo khi: HTML do trình soạn thảo xuất ra bị M3 hiển thị sai, hoặc server từ chối HTML hợp lệ.

### Prompt M6 – Hồ sơ và mạng xã hội

Chạy sau khi M0 merge.

Làm module M6 (Hồ sơ và mạng xã hội) trong feature/profile, theo mục "M6" của docs/PLAN.md. Tuân thủ quy ước chung.

Phạm vi: profile/{userId}, followers, following, search/users, theo dõi, báo cáo người dùng.

Đã chốt:
- GET /api/profiles/{user}; danh sách followers và following cuộn vô hạn bằng PageLoader của core/ theo page (15 mỗi trang). Following chưa được kiểm tra riêng, nên báo tôi nếu nó khác followers.
- Bài của người dùng: GET /api/posts?user_id=, phân trang bằng PageLoader của core/ giống M2; dùng PostCard của M0; gọi API qua package mạng trong core/, không import code của M2.
- Tìm người: GET /api/users/search, q 2–100 ký tự, debounce 400 ms, chỉ lấy trang đầu, tối đa 20 kết quả. Từng thấy q=ad trả rỗng dù có user tên "Admin"; ghi lại hành vi thật bạn quan sát được, đừng tự sửa.
- Theo dõi POST /api/users/{user}/follow: cập nhật lạc quan, đồng bộ followers_count từ phản hồi. Ẩn nút Theo dõi và Báo cáo trên hồ sơ của chính mình.
- Danh sách follower không có nút Theo dõi trên từng dòng vì UserResource không có is_following.
- Tab Hồ sơ lấy id từ session; khách thấy lời mời đăng nhập. Hồ sơ của mình có nút Cài đặt mở route settings.
- Báo cáo người dùng dùng ReportSheet của M0, gọi POST /api/users/{user}/report.

Không làm: cài đặt tài khoản (M7), màn Tìm kiếm bài (M2).
Dừng và báo khi: response của GET /api/profiles/{user} thiếu trường so với swagger.

### Prompt M7 – Cài đặt tài khoản

Chạy sau khi M0 và M1 merge.

Làm module M7 (Cài đặt tài khoản) trong feature/settings, theo mục "M7" của docs/PLAN.md. Tuân thủ quy ước chung.

Phạm vi: màn settings gồm đổi tên, đổi mật khẩu, avatar, ảnh bìa, giao diện sáng/tối, Về ứng dụng (mở route about), Đăng xuất, Xóa tài khoản.

Đã chốt:
- Đổi tên PATCH /api/profile/details (≤50 ký tự); đổi mật khẩu PATCH /api/profile/password; avatar POST /api/user/avatar (field avatar, ≤5MB); ảnh bìa POST /api/user/cover-photo (field cover, ≤10MB). Cắt ảnh vuông cho avatar, 16:9 cho ảnh bìa, nén theo giới hạn.
- Sau mỗi thay đổi cập nhật session.user từ phản hồi để mọi màn hình đổi theo.
- Xóa tài khoản: DELETE /api/profile/delete có body {password}; khai báo Retrofit bằng @HTTP(method = "DELETE", hasBody = true). Bắt nhập mật khẩu và gõ lại "XOA"; thành công thì xóa session và về Trang chủ ở chế độ khách.
- Đăng xuất gọi SessionManager.logout() của M0.
- Giao diện sáng, tối, theo hệ thống lưu bằng cơ chế SharedPreferences mã hóa bằng Android Keystore.
- Lỗi 422 hiển thị đúng ô (nhất là current_password sai).
- Chừa một mục "Quản trị" ẩn mặc định để M10 gắn vào sau.

Không làm: màn about (M8), đăng nhập quản trị (M10).
Dừng và báo khi: GET /api/user thiếu trường cần để cập nhật session.

### Prompt M8 – Quảng cáo và cấu hình hệ thống

Chạy sau khi M0 merge. Module nhỏ, có thể giao kèm M1.

Làm module M8 (Quảng cáo và cấu hình hệ thống) trong feature/config, theo mục "M8" của docs/PLAN.md. Tuân thủ quy ước chung.

Phạm vi: AdSlot(position) thật, logo, ảnh nền, màn about.

Đã chốt:
- GET /api/advertisements?position= (bắt buộc). Nhớ kết quả trong bộ nhớ 5 phút; banner bằng Glide với nhãn "Quảng cáo"; chạm mở link_url bằng Custom Tabs; không có quảng cáo thì không chiếm chỗ.
- Vị trí tạm dùng: feed_inline và post_detail_bottom (chờ backend #7 chốt danh sách).
- GET /api/settings/logo, /api/settings/background, /api/settings/footer: tải một lần khi khởi động, chạy song song; lỗi thì dùng mặc định và không chặn người dùng vào app.
- Ảnh nền chỉ dùng cho màn Đăng nhập hoặc Chào mừng, có lớp phủ tối; bỏ qua nếu thiết kế không cần.
- Phần tử links và socials của footer chưa có cấu trúc trong swagger: gọi API thật một lần, ghi lại cấu trúc, dựng model theo đó và báo tôi để cập nhật swagger.
- AdSlot đã có bản rỗng trong core/ui (M0): thay phần hiện thực, không đổi chữ ký.

Không làm: ghi nhận lượt hiển thị hay nhấp (không có API), quản lý quảng cáo (M10 hoặc web).
Dừng và báo khi: response thật của footer khác với mô tả trong swagger.

### Prompt M9 – Kiểm chứng tin giả

Chạy sau khi M0 merge. Module này đụng vào code đang chạy tốt, nên bắt đầu bằng test hồi quy.

Làm module M9 (Kiểm chứng tin giả) trong feature/factcheck, theo mục "M9" của docs/PLAN.md. Tuân thủ quy ước chung.

Việc đầu tiên: viết test hồi quy cho hai luồng hiện có (chọn ảnh, chụp ảnh) TRƯỚC khi chuyển bất kỳ code nào, rồi nhắc lại cho tôi định dạng kết quả của bộ nhận diện (nhãn, độ tin cậy, có lời giải thích hay không).

Phạm vi: tách code nhận diện tin giả hiện có thành feature/factcheck, giữ nguyên hành vi và dịch vụ gọi bên dưới; thêm kết nối với bài viết.

Đã chốt:
- Route factcheck?imageUrl=&postId=: tải ảnh về bộ nhớ đệm, resize nếu lớn, chạy cùng pipeline hiện có.
- Nút "Đăng thảo luận": ảnh người dùng chọn hoặc chụp thì upload qua POST /api/image-upload (qua core/network), rồi mở post/new?title=&imageUrl=&body= với tiêu đề gợi ý, ảnh và tóm tắt kết quả bằng văn bản thuần ngắn. Khách thì gọi requireLogin(Runnable) của M0.
- Khi có postId: hiện nút "Báo cáo bài viết" mở ReportSheet với lý do điền sẵn (người dùng sửa được) rồi gọi POST /api/posts/{post}/report. Không bao giờ báo cáo tự động.
- Lịch sử kiểm tra lưu cục bộ bằng Room (ảnh thu nhỏ, kết quả, thời gian), xem lại và xóa được.
- Chia sẻ kết quả (ảnh và văn bản) qua Intent.
- Luôn hiện dòng nhắc: kết quả chỉ mang tính tham khảo.
- Không đổi dịch vụ nhận diện, không đổi định dạng request hoặc response của nó.

Không làm: thêm endpoint backend, đổi mô hình nhận diện.
Dừng và báo khi: việc tách module làm kết quả nhận diện khác so với trước.

### Prompt M10 – Kiểm duyệt và quản trị

Chỉ chạy sau khi M0 và M1 merge, và backend đã trả lời #3 và #5.

Làm module M10 (Kiểm duyệt và quản trị) trong feature-admin, theo mục "M10" của docs/PLAN.md. Tuân thủ quy ước chung.

Phạm vi trong app: đăng nhập quản trị, dashboard (chỉ xem), 3 hàng đợi báo cáo, nội dung đã gỡ và khôi phục, cấm và gỡ cấm, lịch sử kiểm duyệt.

Đã chốt:
- POST /api/admin/login dùng chung SessionManager. Lối vào gắn vào mục "Quản trị" M7 đã chừa trong Cài đặt, chỉ hiện khi role từ moderator trở lên.
- Báo cáo: GET /api/moderator/reports/{posts,comments,users} và DELETE .../{id}. DELETE chỉ đóng báo cáo, KHÔNG gỡ nội dung; không làm nút "Gỡ nội dung" cho đến khi backend trả lời #5.
- Nội dung đã gỡ: GET /api/moderator/content/removed-{posts,comments}; khôi phục bằng POST /api/moderator/{posts,comments}/{id}/restore; 422 khi nội dung đang hiển thị thì báo "đã hiển thị".
- Cấm: GET /api/admin/users/banned, POST /api/admin/users/{user}/ban (duration_days 1–3650, bỏ trống là vĩnh viễn, xác nhận hai bước cho cấm vĩnh viễn), POST .../unban, GET .../moderation-history.
- Dashboard: chỉ hiển thị counts; growth và top_content chưa có cấu trúc trong swagger, gọi API thật một lần để dựng model và báo tôi.
- Ẩn hoặc hiện theo role để dễ dùng, nhưng coi 403 từ server là quyết định cuối cùng.
- Danh sách phân trang theo page như quy ước; xác nhận bằng response thật.

Không làm: CRUD quảng cáo, chuyên mục, cấu hình hệ thống, phân quyền Superadmin (để trên web).
Dừng và báo khi: response thật của các endpoint quản trị khác với swagger.

## Phụ lục: đối chiếu endpoint với module

Cả 75 thao tác trong swagger đều có chủ: 44 thuộc M1–M8, 31 thuộc M10; M9 dùng bộ nhận diện hiện có nên không có endpoint riêng.

| Module | Thao tác | Endpoint |
| --- | --- | --- |
| M1 | 11 | POST /api/register; POST /api/login; POST /api/logout; POST /api/forgot-password; POST /api/reset-password; GET /api/verify-email/{id}/{hash}; POST /api/email/verification-notification; GET /api/login/google/redirect; GET /api/login/google/callback; GET /api/login/github/redirect; GET /api/login/github/callback |
| M2 | 3 | GET /api/posts; GET /api/categories; GET /api/categories/{category} |
| M3 | 4 | GET /api/posts/{post}; POST /api/posts/{post}/upvote; POST /api/posts/{post}/downvote; POST /api/posts/{post}/report |
| M4 | 6 | GET /api/posts/{post}/comments; GET /api/comments/{comment}/replies; POST /api/comments; PATCH /api/comments/{comment}; DELETE /api/comments/{comment}; POST /api/comments/{comment}/report |
| M5 | 4 | POST /api/posts; PUT /api/posts/{post}; DELETE /api/posts/{post}; POST /api/image-upload |
| M6 | 6 | GET /api/profiles/{user}; GET /api/profiles/{user}/followers; GET /api/profiles/{user}/following; GET /api/users/search; POST /api/users/{user}/follow; POST /api/users/{user}/report |
| M7 | 6 | GET /api/user; PATCH /api/profile/details; PATCH /api/profile/password; POST /api/user/avatar; POST /api/user/cover-photo; DELETE /api/profile/delete |
| M8 | 4 | GET /api/advertisements; GET /api/settings/logo; GET /api/settings/background; GET /api/settings/footer |
| M9 | 0 | Không có (dùng lại POST /api/image-upload và POST /api/posts/{post}/report qua core/network) |
| M10 | 31 | POST /api/admin/login; 11 route dưới /api/moderator/ (stats, removed-posts, removed-comments, 2 restore, 3 GET + 3 DELETE báo cáo); 4 route /api/admin/advertisements; 6 thao tác /api/admin/categories; 3 route /api/admin/settings/*; 4 route /api/admin/users/*; 2 route /api/superadmin/* |
