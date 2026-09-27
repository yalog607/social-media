package com.aloute.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Spring Security 6 tải CSRF token lười (chỉ khi có ai đọc). Trang không có form nhưng có JS
 * gọi fetch vẫn cần cookie XSRF-TOKEN, nên ta chủ động đọc token để buộc cookie được ghi ra.
 * <p>
 * Bỏ qua tài nguyên tĩnh ({@code SecurityConfig.PUBLIC_STATIC}): một lần tải trang kéo theo hàng chục request
 * CSS/JS/font/ảnh CHẠY SONG SONG, và trước khi cookie CSRF đầu tiên kịp có hiệu lực, mỗi request trong số đó
 * đều thấy "chưa có cookie" nên tự sinh một token MỚI của riêng nó; trình duyệt chỉ giữ lại giá trị đến sau
 * cùng, khác với giá trị đã in sẵn vào form của trang — biểu mẫu vừa tải xong đã bị 403 vì lệch token. Các
 * request này không bao giờ hiển thị {@code _csrf.token} nên không cần ép sinh cookie ở đây.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            token.getToken();
        }
        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/fonts/")
                || path.startsWith("/webjars/") || path.startsWith("/img/") || path.startsWith("/uploads/")
                || path.equals("/favicon.ico");
    }
}
