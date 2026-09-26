package com.aloute.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * Tải lên vượt giới hạn của máy chủ (spring.servlet.multipart.*) xảy ra TRƯỚC khi vào controller,
 * nên phải bắt ở đây để người dùng thấy thông báo thay vì trang lỗi 500.
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UploadErrorAdvice {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String tooLarge(HttpServletRequest request, HttpServletResponse response) {
        FlashMap flash = RequestContextUtils.getOutputFlashMap(request);
        flash.put("composerError", "File quá lớn: mỗi ảnh tối đa 8 MB, video tối đa 25 MB, và cả bài tối đa 40 MB.");
        RequestContextUtils.saveOutputFlashMap("/", request, response);
        return "redirect:/";
    }
}
