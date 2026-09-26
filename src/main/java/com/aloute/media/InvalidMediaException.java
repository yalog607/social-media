package com.aloute.media;

/** Ảnh/video gửi lên không hợp lệ. Thông báo bằng tiếng Việt, an toàn để hiển thị cho người dùng. */
public class InvalidMediaException extends RuntimeException {

    public InvalidMediaException(String message) {
        super(message);
    }
}
