package com.aloute.post;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Bài không tồn tại, đã bị xóa, hoặc người xem không có quyền xem/sửa/xóa. Cố ý gộp chung để không lộ
 * việc một bài riêng tư có tồn tại hay không. Hiển thị là HTTP 404.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class PostNotFoundException extends RuntimeException {

    public PostNotFoundException() {
        super("Không tìm thấy bài viết");
    }
}
