package com.aloute.exception.moderation;

/** Thao tác kiểm duyệt không hợp lệ (báo cáo đã xử lý, nhắm vào nhân sự, hành động không áp dụng được...). Thông báo tiếng Việt. */
public class InvalidModerationException extends RuntimeException {
    public InvalidModerationException(String message) {
        super(message);
    }
}
