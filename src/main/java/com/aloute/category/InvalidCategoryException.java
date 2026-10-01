package com.aloute.category;

/** Danh mục không hợp lệ (tên trống/trùng, không tồn tại, đã ngưng dùng). Thông báo tiếng Việt, an toàn để hiển thị. */
public class InvalidCategoryException extends RuntimeException {
    public InvalidCategoryException(String message) {
        super(message);
    }
}
