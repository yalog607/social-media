package com.aloute.exception.creator;

/** Chủ đề (creator_topics) không hợp lệ: quá 5 mục, danh mục không tồn tại hoặc đã ngưng dùng. Thông báo tiếng Việt. */
public class InvalidTopicException extends RuntimeException {
    public InvalidTopicException(String message) {
        super(message);
    }
}
