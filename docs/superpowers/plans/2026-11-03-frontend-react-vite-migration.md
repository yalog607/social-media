# Kế hoạch chuyển UI ALOUTE sang React + Vite

> Bước đầu tiên khi được duyệt: lưu bản này vào repo tại `docs/superpowers/plans/2026-11-03-frontend-react-vite-migration.md` (thư mục `docs/superpowers/plans/` hiện chưa có, sẽ tạo). Không commit/push nếu chưa được hỏi.

## Context
Hiện ~79 template Thymeleaf + 13 file JS; chỉ 3 `@RestController` (sticker, reaction, social auth), hầu hết controller trả HTML, chưa có JSON API. Auth: JWT trong cookie, CSRF cookie (`CookieCsrfTokenRepository.withHttpOnlyFalse()`), stateless (`config/SecurityConfig.java`). Chat dùng WebSocket (`service/chat`).
Mục tiêu: UX hiện đại/realtime. Backend giữ Java/Spring.

## Phạm vi
- Trong phạm vi: frontend web React + Vite (TypeScript) và REST API `/api/v1/**` phục vụ nó.
- **Ngoài phạm vi (để sau): app mobile.** Không thiết kế luồng token cho mobile, không dựng Expo/React Native lúc này. API vẫn thiết kế sạch (OpenAPI) nên sau này mobile có thể dùng lại.

## Quyết định đã chốt
- Web: React + Vite (SPA), không dùng Next.js.
- API-first: Spring thêm controller JSON `/api/v1/**`, tái dùng `service/**` và `dto/**`; controller MVC cũ chạy song song.
- **Không dùng feature flag.** Bản React và bản Thymeleaf ở hai đường dẫn khác nhau trong lúc thử; đạt yêu cầu thì chuyển hướng/thay route cũ.
- Cùng origin: build Vite (`dist`) do Spring phục vụ như static resource (hoặc reverse proxy cùng domain); lúc dev dùng proxy của Vite → Spring. Giữ cookie JWT + CSRF, không cần CORS.
- Khu vực thử đầu tiên: creator dashboard.

## Hệ quả của việc bỏ Next.js (cần bạn xác nhận)
Vite SPA không có SSR nên SEO cho nội dung công khai yếu. Đề xuất mặc định: landing (và hồ sơ/bài công khai nếu sau này mở cho khách) vẫn do Spring + Thymeleaf render, có meta/OG tag; phần đăng nhập (feed, chat, creator, admin...) là React SPA. Việc có mở trang công khai hay không là quyết định sản phẩm riêng (quyền riêng tư, an toàn teen), để sau.

## Còn phải chốt khi tới phase tương ứng
- Realtime: client cho WebSocket hiện tại, cần đọc `service/chat` và config WebSocket (Phase 5).
- Routing SPA: Spring cần fallback trả `index.html` cho các route của React (ví dụ `/app/**`).

## Các giai đoạn
0. **Spec/kiểm kê**: liệt kê endpoint theo nhóm tính năng, chọn OpenAPI (springdoc), layout `frontend/`.
1. **Nền tảng API**: springdoc trong `pom.xml`; quy tắc `/api/v1/**` trong `SecurityConfig` (CSRF, quyền; `/api/v1/creator/**` yêu cầu `CREATOR`); chuẩn lỗi JSON; test `*IT`.
2. **Creator dashboard (thử nghiệm)**: `CreatorApiController` (thống kê, lịch hẹn giờ) tái dùng service creator hiện có + trang React ở đường dẫn riêng; Spring phục vụ build Vite và fallback `index.html`.
3. **Feed/bài viết/bình luận/cảm xúc** (lõi, lớn nhất).
4. **Hồ sơ, bạn bè, tìm kiếm, thông báo**.
5. **Chat/WebSocket realtime**.
6. **Gỡ dần Thymeleaf** từng khu vực khi bản mới ổn định; giữ landing/trang công khai bằng Thymeleaf; admin/kiểm duyệt để cuối.

## File ảnh hưởng (đại diện)
- Backend: `pom.xml` (springdoc, bước copy build Vite nếu cần), `config/SecurityConfig.java`, `config` MVC (fallback SPA), `controller/creator/*ApiController` (mới).
- Frontend mới: `frontend/` (Vite + React + TS).
- Giữ nguyên `templates/**`, `static/**` cho đến khi từng khu vực chuyển xong.

## Verify (mỗi giai đoạn)
- API: `mvn verify` (IT Testcontainers, cần Docker) + gọi thật bằng curl; kiểm tra quyền (ADMIN>MANAGER>USER, CREATOR; Admin/Manager không tự có quyền Creator), chặn người dùng, visibility, CSRF.
- UI: mở trình duyệt kiểm tra, chụp screenshot, đối chiếu với bản Thymeleaf; F5 trên route sâu của SPA vẫn load đúng.
- Sau mỗi phần lớn: gọi subagent `reviewer`.

## Cần hỏi/duyệt trước khi chạm vào
`pom.xml`, `SecurityConfig`, thư mục `frontend/` mới, cài package lớn (Node/Vite), commit/push.
