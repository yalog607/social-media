package com.aloute.post;

/** Nội dung bài không hợp lệ (rỗng, quá dài...). Thông báo tiếng Việt, an toàn để hiển thị cho người dùng. */
public class InvalidPostException extends RuntimeException {

    public InvalidPostException(String message) {
        super(message);
    }
}
