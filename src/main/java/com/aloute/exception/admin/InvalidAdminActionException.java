package com.aloute.exception.admin;

/** Thao tác quản trị không hợp lệ (nhắm vào Admin, tự thao tác lên mình, người dùng không tồn tại...). Thông báo tiếng Việt. */
public class InvalidAdminActionException extends RuntimeException {
    public InvalidAdminActionException(String message) {
        super(message);
    }
}
