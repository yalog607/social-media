# Review Creator — 2026-10-29

Phạm vi: review tĩnh các thay đổi Creator hiện tại đối chiếu `docs/plan/2026-10-29-creator.md` và `AGENTS.md`. Không phát hiện lỗi nghiêm trọng qua kiểm tra tĩnh.

## Findings

1. **Trung bình — Hủy sau khi bài đã xuất bản.** `src/main/java/com/aloute/controller/creator/CreatorController.java:118` gọi `posts.delete` mà không kiểm tra bài còn hẹn giờ. Sau khi publisher đặt `scheduled_at = null`, chủ bài vẫn có thể gọi URL hủy cũ để xóa mềm bài đã đăng. `src/test/java/com/aloute/post/ScheduledPostIT.java:134` ghi nhận hành vi này, nhưng plan yêu cầu báo owner nếu hành vi sau giờ hẹn cần quyết định. Cần owner xác nhận nghiệp vụ; test pass không tự chứng minh hành vi đã được duyệt. Qua đọc mã, quyền sở hữu được kiểm tra bởi `ownedLivePost`, chưa thấy người khác có quyền hủy bài.

2. **Trung bình — Thiếu kiểm thử migration đồng thời.** `src/test/java/com/aloute/creator/InsightsServiceIT.java:128` chạy migration với một connection và một quan hệ có sẵn, chưa kiểm tra ghi đồng thời trong lúc backfill, dữ liệu rỗng hoặc lịch sử Flyway trên DB thử như plan yêu cầu. `src/main/resources/db/migration/V28__follow_history.sql:2` dùng khóa `SHARE ROW EXCLUSIVE` phù hợp để chặn ghi trong giai đoạn backfill và tạo trigger, nhưng cần bổ sung kiểm thử và xác nhận rollout bằng runtime.

3. **Thấp — Thiếu ranh giới UTC và distinct.** `src/test/java/com/aloute/creator/InsightsServiceIT.java:160` chưa kiểm tra đầu khoảng 30 ngày, thời điểm ngay trước/sau nửa đêm UTC, và follow lại nhiều lần trong cùng ngày đối với từng cột. Test tại dòng 106 dùng đồng hồ thực và trừ 24 giờ, có thể không ổn định nếu chạy sát nửa đêm UTC giữa lúc tạo sự kiện và đọc đồng hồ. Nên dùng thời điểm cố định và bổ sung các trường hợp này.

4. **Thấp — Truy vấn theo ngày đọc cả sự kiện sau khoảng chọn.** `src/main/java/com/aloute/service/creator/InsightsService.java:68` chỉ có cận dưới; vòng tạo chuỗi bỏ các ngày sau hôm nay nên kết quả hiển thị vẫn loại chúng, nhưng DB vẫn nhóm và trả dữ liệu tương lai. Truy vấn tổng distinct tại dòng 92 đã có cận trên. Nên thêm cùng cận trên UTC cho truy vấn theo ngày để giới hạn công việc và làm điều kiện nhất quán.

## Giới hạn xác minh và tương thích

- Đây là review tĩnh; Docker chưa chạy. Chưa xác nhận migration/backfill trên DB thử, integration test, API hoặc UI ở runtime.
- Đọc tĩnh cho thấy backfill giữ `id` và `created_at` của từng quan hệ hiện có; trigger ghi sự kiện trong cùng giao dịch. Chưa xác nhận follow đồng thời và rollback giao dịch ở runtime.
- Binary mới phụ thuộc bảng V28; cần đảm bảo migration thành công trước khi dashboard mới truy cập. Chưa xác nhận tương thích rollout hoặc rollback binary.
- Cần kiểm tra dashboard 7/30 ngày trên trình duyệt và xác nhận các bước verify còn thiếu theo plan trước khi báo hoàn tất.
