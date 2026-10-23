# ALOUTE

Mạng xã hội cho tuổi teen: Spring Boot 3.5 · Thymeleaf · Bootstrap 5 · JPA · PostgreSQL · JWT · WebSocket · Firebase.
Giao diện phong cách neo-brutalism pastel kiểu sổ lưu bút.

Trạng thái: đăng ký/đăng nhập, phân quyền 4 vai trò, hồ sơ, bảng tin, đăng bài (chữ/ảnh/video), cảm xúc, bình luận, chia sẻ.

## Yêu cầu
- JDK 21+ (đã thử với JDK 24), Maven 3.9+
- Docker (cho PostgreSQL, MailHog và test tích hợp)

## Chạy ở máy dev

```bash
docker compose up -d postgres mailhog
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Mở http://localhost:8080. Email "quên mật khẩu" xem ở http://localhost:8025 (MailHog).
Trang duyệt design system (chỉ có ở profile dev): http://localhost:8080/dev/styleguide

### Tài khoản mẫu (profile `dev`, mật khẩu `Aloute@123`)
| Vai trò | Email | Username |
|---|---|---|
| Admin | admin@aloute.local | admin |
| Manager | manager@aloute.local | manager |
| Creator | creator@aloute.local | creator |
| User | user@aloute.local | mochi |

Vai trò kế thừa: `ADMIN > MANAGER > USER`, `CREATOR > USER`. Manager/Admin **không** tự có quyền Creator.

## Kiểm thử

```bash
mvn test      # test đơn vị, không cần Docker
mvn verify    # thêm test tích hợp (Testcontainers + PostgreSQL thật), cần Docker chạy
```

## Cấu hình (biến môi trường)

| Biến | Ý nghĩa | Ghi chú |
|---|---|---|
| `ALOUTE_JWT_SECRET` | Khóa ký JWT | **Bắt buộc** khi không dùng profile dev, ≥ 32 ký tự ngẫu nhiên |
| `ALOUTE_COOKIE_SECURE` | Cookie chỉ gửi qua HTTPS | Mặc định `true`, profile dev đặt `false` |
| `ALOUTE_SEED_PASSWORD` | Có giá trị thì tạo tài khoản admin (username `admin`) | Production: ≥ 12 ký tự, **xóa đi sau khi đã tạo admin** |
| `ALOUTE_SEED_ADMIN_EMAIL` | Email của admin khởi tạo (mặc định `admin@aloute.local`) | Production: dùng email thật để còn "Quên mật khẩu" |
| `ALOUTE_SEED_DEMO` | Tạo thêm manager/creator/user mẫu | Chỉ dùng khi dev |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Kết nối PostgreSQL | |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASSWORD`, `ALOUTE_MAIL_FROM` | SMTP gửi mail | |
| `ALOUTE_BASE_URL` | URL công khai, dùng trong link email | |
| `ALOUTE_UPLOAD_DIR` | Thư mục lưu ảnh người dùng khi `ALOUTE_STORAGE_TYPE` là `local` (mặc định `uploads`) | Production: đã gắn Docker volume vào `/app/uploads` |
| `ALOUTE_STORAGE_TYPE` | Nơi lưu ảnh/video: `local` (mặc định) hoặc `cloudinary` | |
| `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` | Thông tin tài khoản Cloudinary | **Bắt buộc** khi `ALOUTE_STORAGE_TYPE=cloudinary` |

### Bật đăng nhập Google/Facebook (Firebase Authentication)
Firebase chỉ dùng để xác minh danh tính, miễn phí và không cần thẻ; không liên quan tới nơi lưu ảnh.
1. Tạo project Firebase, bật **Authentication** (Google, Facebook).
2. Tải file service account JSON về một thư mục **ngoài git** (ví dụ `secrets/`, đã nằm trong `.gitignore`).
3. Đặt biến môi trường:
   - `FIREBASE_CREDENTIALS` = đường dẫn tới file service account
   - `FIREBASE_WEB_API_KEY`, `FIREBASE_WEB_AUTH_DOMAIN`, `FIREBASE_WEB_PROJECT_ID` = cấu hình web (công khai) của app
4. Thêm domain đang chạy vào **Authorized domains** trong Firebase Authentication.

Không có các biến này, ứng dụng vẫn chạy đủ chức năng: nút Google/Facebook hiển thị trạng thái tắt.

### Lưu ảnh/video trên Cloudinary (tuỳ chọn)
Mặc định ảnh/video lưu trên đĩa của máy chủ (`uploads/` khi dev, Docker volume khi production). Muốn chuyển sang Cloudinary:
1. Tạo tài khoản tại [cloudinary.com](https://cloudinary.com), lấy **Cloud name**, **API Key**, **API Secret** ở trang Dashboard.
2. Đặt biến môi trường `ALOUTE_STORAGE_TYPE=cloudinary`, `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`.
3. Khởi động lại ứng dụng — avatar, ảnh bìa, ảnh/video bài đăng và file đính kèm chat từ giờ tự động tải lên Cloudinary; ảnh/video đã lưu cục bộ từ trước KHÔNG được chuyển tự động.

## Cấu trúc (MVC ba lớp)
```
src/main/java/com/aloute/
├─ controller/<tính năng>/   Controller: nhận request, gọi service, trả view/JSON
├─ service/<tính năng>/      Model (nghiệp vụ): service, job, storage, gửi mail
├─ repository/<tính năng>/   Model (truy cập dữ liệu): Spring Data JPA
├─ model/<tính năng>/        Model (miền dữ liệu): entity, enum
├─ dto/<tính năng>/          Model (đối tượng truyền): *View, *Form, *Page
├─ exception/<tính năng>/    ngoại lệ nghiệp vụ
├─ util/<tính năng>/         tiện ích thuần (không phụ thuộc Spring)
├─ security/                 JWT, cookie, refresh token, filter (hạ tầng)
└─ config/                   Security, MVC, Firebase, thuộc tính cấu hình
src/main/resources/templates   View: Thymeleaf
src/main/resources/{db/migration, static}
```
`<tính năng>`: admin, auth, chat, comment, creator, feed, post, social, user, wallet…
