# ALOUTE — hướng dẫn cho phiên làm việc tiếp theo

## Quy tắc làm việc

- Đọc cấu trúc dự án, file liên quan và test hiện có trước khi sửa. Không đoán API, tên hàm hoặc schema; hỏi khi chưa chắc.
- Với task không tầm thường, trình bày plan/spec gồm mục tiêu, phạm vi, file ảnh hưởng, edge case và cách verify; chờ người dùng duyệt trước khi code. Hỏi trước về yêu cầu mơ hồ.
- Với tính năng quan trọng có thể ảnh hưởng người dùng hoặc tính năng khác, đề xuất và chốt feature flag trước khi code: tên, mặc định on/off, hành vi khi tắt.
- Verify từng task trước khi sang task tiếp theo, kèm bằng chứng phù hợp: output test, response API thật hoặc screenshot UI. UI phải mở trình duyệt kiểm tra; API phải gọi thật bằng curl/test. Sau mỗi phần việc lớn, gọi subagent `reviewer` kiểm tra độc lập.
- Chỉ làm việc trong thư mục dự án; không đọc/ghi bên ngoài khi chưa hỏi. Hỏi trước khi xóa file hàng loạt, đổi schema/migration, config/CI/deploy, cài package lớn, push hoặc thay đổi ảnh hưởng toàn dự án/người dùng.

## Tổng quan

ALOUTE là mạng xã hội cho tuổi teen, dùng Java 21, Spring Boot 3.5, Thymeleaf, Bootstrap 5, Spring Security, JWT, PostgreSQL, Flyway và WebSocket. `README.md` là điểm bắt đầu cho chạy dự án và cấu hình; đối chiếu mã nguồn trước khi thay đổi vì tài liệu có thể cũ.

## Bản đồ mã nguồn

- `src/main/java/com/aloute/controller`, `service`, `repository`, `model`, `dto`: các lớp theo nhóm tính năng; `guestbook` nằm riêng tại `com.aloute.guestbook`.
- `src/main/java/com/aloute/security` và `config`: xác thực, phân quyền và cấu hình; chat WebSocket nằm trong `service/chat`.
- `src/main/resources/templates`: view Thymeleaf; `static`: CSS, JavaScript và tài nguyên; `db/migration`: migration Flyway.
- `src/test/java/com/aloute`: unit test `*Test` và integration test `*IT`. Tài liệu thiết kế và kế hoạch cũ nằm trong `docs/superpowers`.

## Tính năng có trong mã nguồn

- Đăng ký, đăng nhập, đăng xuất, refresh token, quên/đặt lại mật khẩu; đăng nhập Google qua Firebase khi được cấu hình.
- Hồ sơ, avatar/ảnh bìa, cài đặt riêng tư và quyền nhắn tin; kết bạn, theo dõi và chặn.
- Bảng tin, bài chữ/ảnh/video, sửa/xóa/chia sẻ/ghim, gắn thẻ bạn bè, hashtag, danh mục, lượt xem; tìm kiếm người, bài và hashtag.
- Cảm xúc bài viết, bình luận và tin nhắn; bình luận trả lời kèm ảnh, mention và thông báo.
- Chat riêng và nhóm, file đính kèm, lịch sử, tìm kiếm, sửa/xóa/ghim tin, phản ứng và chuỗi chat; quản lý nhóm, vai trò, biệt danh và duyệt thành viên.
- Creator tự đăng ký, xem thống kê, hẹn giờ đăng và quản lý lịch, gửi thông báo tới follower.
- Mood có hạn 24 giờ; sổ lưu bút cho phép ghi, ghim, ẩn và xóa lời nhắn.
- Báo cáo, kiểm duyệt, hỗ trợ, quản lý hashtag/danh mục; Admin quản lý người dùng, quyền Manager, nhật ký và cấu hình đăng ký.

Danh mục trên là bản đồ để tìm mã, không thay thế việc kiểm tra hành vi thực tế. Khi thay đổi tính năng hoặc cấu trúc, cập nhật phần liên quan nếu cần.

## Ranh giới kỹ thuật

- Phân cấp quyền: `ADMIN > MANAGER > USER`, `CREATOR > USER`; Admin/Manager không tự có quyền Creator. Các trang nội dung yêu cầu đăng nhập, kể cả bài có visibility công khai. Xem `config/SecurityConfig.java` và kiểm tra các quy tắc nghiệp vụ liên quan.
- Firebase dùng cho xác thực social. Media lưu local mặc định hoặc Cloudinary khi cấu hình; không đưa thông tin bí mật vào tài liệu hoặc git.
- Tôn trọng quyền xem hồ sơ/bài, chặn người dùng, thành viên hội thoại, CSRF và giao dịch ví khi sửa các luồng liên quan.

## Chạy và kiểm tra

- Theo `README.md`: `docker compose up -d postgres mailhog`, sau đó `mvn spring-boot:run -Dspring-boot.run.profiles=dev`.
- `mvn test`: unit test; `mvn verify`: thêm integration test Testcontainers với PostgreSQL, cần Docker. Chọn kiểm tra phù hợp thay đổi và báo rõ kiểm tra nào chưa chạy.
- UI tại `http://localhost:8080`; MailHog tại `http://localhost:8025`; styleguide chỉ ở profile dev tại `/dev/styleguide`.
