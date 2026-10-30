# Hướng dẫn cài đặt & chạy ALOUTE

ALOUTE là mạng xã hội cho tuổi teen, xây dựng bằng Java 21, Spring Boot 3.5, Thymeleaf, Bootstrap 5, Spring Security, JWT, PostgreSQL, Flyway và WebSocket.

File này hướng dẫn cài đặt và chạy dự án ở máy local theo từng bước, kèm tuỳ chọn import sẵn một database mẫu từ `aloute.sql` để không phải tạo dữ liệu từ đầu. Xem `README.md` để biết chi tiết đầy đủ về tính năng và biến môi trường; xem `deploy/DEPLOY.md` nếu cần triển khai production.

## 1. Yêu cầu hệ thống

- JDK 21 trở lên (dự án đã thử chạy được với JDK 24).
- Maven 3.9 trở lên — repo **không có** Maven Wrapper (`mvnw`/`mvnw.cmd`), nên cần cài Maven sẵn trên máy.
- Docker + Docker Compose — dùng để chạy PostgreSQL, MailHog (và tuỳ chọn pgAdmin), cũng như cho integration test (`mvn verify`).

## 2. Lấy mã nguồn & khởi động dịch vụ nền

Từ thư mục gốc repo, khởi động PostgreSQL và MailHog:

```bash
docker compose up -d postgres mailhog
```

Muốn xem database qua giao diện web, khởi động thêm pgAdmin:

```bash
docker compose up -d pgadmin
```

Các service và cổng (theo `docker-compose.yml`):

| Service  | Container         | Cổng       | Thông tin đăng nhập |
|----------|--------------------|------------|----------------------|
| postgres | `aloute-postgres`  | `5432`     | DB `aloute`, user `aloute`, mật khẩu `aloute_dev` |
| mailhog  | `aloute-mailhog`   | `1025` (SMTP), `8025` (web UI) | — |
| pgadmin  | `aloute-pgadmin`   | `5050`     | `admin@aloute.com` / `admin` |

## 3. Khởi tạo database — chọn 1 trong 2 cách

### Cách A — Để Flyway tự tạo schema trống (mặc định, khuyến nghị)

Không cần làm gì thêm ở bước này. Khi chạy ứng dụng ở bước 4, Flyway sẽ tự động áp dụng toàn bộ migration từ `src/main/resources/db/migration` (`V1` đến `V57`) lên database `aloute` vừa tạo ở bước 2, tạo schema sạch và không có dữ liệu mẫu (ngoài vài tài khoản seed mô tả ở bước 5).

### Cách B — Import database mẫu có sẵn từ `aloute.sql`

Repo có kèm file `aloute.sql` ở thư mục gốc — bản dump PostgreSQL đầy đủ (schema + data) để có ngay dữ liệu mẫu thay vì chạy từ database rỗng. File này **không được đưa vào git** (chỉ nằm cục bộ trên máy), nên không phải nguồn chân lý của schema — các file migration vẫn là nguồn chân lý.

Sau khi container `postgres` đã chạy (bước 2), import bằng:

```bash
docker compose exec -T postgres psql -U aloute -d aloute < aloute.sql
```

Lưu ý:

- Dump giả định role `aloute` đã tồn tại trong PostgreSQL — điều này tự động đúng khi dùng `docker compose up` ở bước 2 (image Postgres tạo role/db theo `POSTGRES_USER`/`POSTGRES_DB`). Nếu dùng một instance PostgreSQL khác tự cài, phải tạo role `aloute` trước khi import.
- Dump này có bảng `flyway_schema_history` ghi nhận đã áp dụng tới `V53`, trong khi thư mục migration hiện tại có tới `V57`. Sau khi import, chỉ cần chạy ứng dụng bình thường ở bước 4 — Flyway sẽ tự phát hiện và áp dụng tiếp các migration còn thiếu (`V54`→`V57`), không cần cấu hình baseline hay bỏ qua migration nào.
- Nếu muốn import lại từ đầu (ví dụ sau khi đã chạy app và có dữ liệu khác trong database), cần xoá dữ liệu cũ trước để tránh lỗi trùng dữ liệu:

  ```bash
  docker compose down -v   # xoá volume aloute-pgdata
  docker compose up -d postgres mailhog
  docker compose exec -T postgres psql -U aloute -d aloute < aloute.sql
  ```

  `docker compose down -v` xoá toàn bộ dữ liệu trong volume Postgres — chỉ dùng khi chắc chắn không cần giữ dữ liệu hiện tại.

## 4. Chạy ứng dụng

```bash
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Sau khi chạy:

- Ứng dụng: http://localhost:8080
- MailHog (xem email quên mật khẩu): http://localhost:8025
- Trang duyệt design system (chỉ có ở profile `dev`): http://localhost:8080/dev/styleguide

## 5. Tài khoản mẫu (profile dev, Cách A)

Nếu khởi tạo database theo **Cách A** (Flyway seed mặc định), các tài khoản sau có sẵn với mật khẩu `Aloute@123`:

| Vai trò  | Email                     |
|----------|----------------------------|
| Admin    | admin@aloute.local         |
| Manager  | manager@aloute.local       |
| Creator  | creator@aloute.local       |
| User     | user@aloute.local          |

Phân cấp quyền: `ADMIN > MANAGER > USER`, `CREATOR > USER`.

Nếu khởi tạo theo **Cách B** (import `aloute.sql`), tài khoản thực tế là dữ liệu có trong file dump, không phải danh sách trên.

## 6. Biến môi trường tuỳ chọn

Các biến dưới đây đều có giá trị mặc định phù hợp cho dev, chỉ cần khai báo khi muốn tuỳ chỉnh hoặc bật tính năng tuỳ chọn (xem `README.md` để biết đầy đủ):

| Biến | Mục đích |
|------|----------|
| `ALOUTE_JWT_SECRET` | Secret ký JWT (đã có giá trị dev mặc định) |
| `ALOUTE_COOKIE_SECURE` | Bật cờ Secure cho cookie (mặc định tắt ở dev) |
| `ALOUTE_SEED_PASSWORD`, `ALOUTE_SEED_ADMIN_EMAIL`, `ALOUTE_SEED_DEMO` | Tuỳ chỉnh tài khoản seed |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Kết nối PostgreSQL khác với mặc định docker-compose |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASSWORD`, `ALOUTE_MAIL_FROM` | Cấu hình gửi email (mặc định dùng MailHog) |
| `ALOUTE_BASE_URL` | URL gốc dùng trong link email |
| `ALOUTE_UPLOAD_DIR`, `ALOUTE_STORAGE_TYPE` | Nơi lưu media: local (mặc định) hoặc Cloudinary |
| `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` | Chỉ cần khi `ALOUTE_STORAGE_TYPE=cloudinary` |
| `FIREBASE_CREDENTIALS`, `FIREBASE_WEB_API_KEY`, `FIREBASE_WEB_AUTH_DOMAIN`, `FIREBASE_WEB_PROJECT_ID` | Tuỳ chọn, bật đăng nhập Google qua Firebase |

## 7. Kiểm thử

```bash
mvn test      # unit test, không cần Docker
mvn verify    # thêm integration test (Testcontainers + PostgreSQL), cần Docker đang chạy
```

## 8. Tham khảo thêm

- `README.md` — mô tả đầy đủ tính năng, cấu hình và biến môi trường.
- `deploy/DEPLOY.md` — hướng dẫn triển khai production (Docker + Caddy + HTTPS).
- `scripts/README-seed-local.md` — script Python sinh dữ liệu test tổng hợp (khác với import `aloute.sql`).
