package com.aloute.exception.creator;

/** Tin nhắn hàng loạt không hợp lệ (trống, quá dài, gửi quá nhiều, không có người nhận...). Thông báo tiếng Việt. */
public class InvalidBroadcastException extends RuntimeException {
    public InvalidBroadcastException(String message) {
        super(message);
    }
}
