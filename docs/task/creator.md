# Công việc role Creator

Trạng thái: ghi nhận yêu cầu; chưa triển khai các thay đổi bên dưới.

## Đã chốt

- [ ] Lưu lịch sử follow và thống kê **số người khác nhau** đã follow trong khoảng chọn, kể cả khi đã unfollow; cùng một người follow lại trong khoảng chỉ tính một lần.
- Hủy lịch hẹn thì **xóa bài**. Đây là hành vi hiện có: `CreatorController.cancel` gọi `posts.delete`; cần giữ và kiểm tra khi thay đổi luồng liên quan.
- Kích hoạt Creator vẫn tự do, **không cần phê duyệt**.

## Cân nhắc, chưa chốt triển khai

- [ ] Cho follower tắt thông báo gửi hàng loạt từ Creator. Có thể tham khảo cách bật/tắt bằng chuông thông báo của YouTube.
- Cần chốt phạm vi: cài đặt theo từng Creator hay toàn bộ Creator, trạng thái mặc định, các loại thông báo chịu tác động và hành vi khi bỏ theo dõi hoặc theo dõi lại.

## Kiểm tra khi triển khai

- Kiểm tra nhãn và số liệu trên dashboard trong các khoảng thời gian được hỗ trợ.
- Kiểm tra hủy lịch xóa đúng bài của chủ sở hữu và bài không được đăng sau giờ hẹn.
- Nếu duyệt thông báo: kiểm tra người bật/tắt, từng đối tượng nhận, quyền truy cập và thay đổi cài đặt; mở trình duyệt kiểm tra UI.
