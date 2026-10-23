package com.aloute.exception.comment;

/** Nội dung bình luận không hợp lệ (rỗng, quá dài, trả lời sai bài...). Thông báo an toàn để hiển thị cho người dùng. */
public class InvalidCommentException extends RuntimeException {
    public InvalidCommentException(String message) {
        super(message);
    }
}
