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
| `ALOUTE_UPLOAD_DIR` | Thư mục lưu ảnh người dùng (mặc định `uploads`) | Production: đã gắn Docker volume vào `/app/uploads` |

### Bật đăng nhập Google/Facebook (Firebase Authentication)
Firebase chỉ dùng để xác minh danh tính, miễn phí và không cần thẻ. Ảnh người dùng lưu trên đĩa của máy chủ, không dùng Cloud Storage.
1. Tạo project Firebase, bật **Authentication** (Google, Facebook).
2. Tải file service account JSON về một thư mục **ngoài git** (ví dụ `secrets/`, đã nằm trong `.gitignore`).
3. Đặt biến môi trường:
   - `FIREBASE_CREDENTIALS` = đường dẫn tới file service account
   - `FIREBASE_WEB_API_KEY`, `FIREBASE_WEB_AUTH_DOMAIN`, `FIREBASE_WEB_PROJECT_ID` = cấu hình web (công khai) của app
4. Thêm domain đang chạy vào **Authorized domains** trong Firebase Authentication.

Không có các biến này, ứng dụng vẫn chạy đủ chức năng: nút Google/Facebook hiển thị trạng thái tắt. Ảnh luôn được lưu vào thư mục `uploads/` (dev) hoặc Docker volume (production).

## Cấu trúc
```
src/main/java/com/aloute/
├─ auth/      đăng ký, đăng nhập, quên mật khẩu, Social
├─ security/  JWT, cookie, refresh token, filter
├─ user/      tài khoản, vai trò, hồ sơ, cài đặt
├─ storage/   lưu ảnh lên đĩa (kiểm tra chữ ký byte thật)
├─ home/      trang chủ, khu vực theo vai trò
├─ common/    mail, tiện ích dùng chung
└─ config/    Security, MVC, Firebase, thuộc tính cấu hình
src/main/resources/{db/migration, templates, static}
```
