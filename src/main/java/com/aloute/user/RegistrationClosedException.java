package com.aloute.user;

/** Admin đang tạm đóng đăng ký tài khoản mới. */
public class RegistrationClosedException extends RuntimeException {
    public RegistrationClosedException() {
        super("Hiện hệ thống tạm ngừng nhận đăng ký mới, bạn quay lại sau nhé.");
    }
}
