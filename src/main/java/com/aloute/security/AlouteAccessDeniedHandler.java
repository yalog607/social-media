package com.aloute.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Đã đăng nhập nhưng không đủ quyền (hoặc thiếu CSRF token): API → JSON 403, trang → view 403. */
public class AlouteAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        if (AuthEntryPoint.isApi(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"error\":\"forbidden\",\"message\":\"Bạn không có quyền làm việc này\"}");
            return;
        }
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
    }
}
