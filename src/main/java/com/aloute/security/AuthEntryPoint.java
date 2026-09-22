package com.aloute.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Chưa đăng nhập (hoặc access token hết hạn):
 * <ul>
 *   <li>API/AJAX → 401 JSON để JS gọi {@code POST /auth/refresh} rồi thử lại.</li>
 *   <li>Trang GET → {@code /auth/refresh?next=...}; nếu refresh cookie còn hợp lệ sẽ quay lại trang cũ,
 *       nếu không sẽ chuyển tới /login.</li>
 *   <li>Các method khác → /login.</li>
 * </ul>
 */
public class AuthEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        if (isApi(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"error\":\"unauthorized\",\"message\":\"Bạn cần đăng nhập\"}");
            return;
        }
        if (HttpMethod.GET.matches(request.getMethod())) {
            String target = request.getRequestURI();
            if (request.getQueryString() != null) {
                target += "?" + request.getQueryString();
            }
            response.sendRedirect(request.getContextPath() + "/auth/refresh?next="
                    + UriUtils.encodeQueryParam(target, StandardCharsets.UTF_8));
            return;
        }
        response.sendRedirect(request.getContextPath() + "/login");
    }

    static boolean isApi(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String accept = request.getHeader("Accept");
        return path.startsWith("/api/")
                || "XMLHttpRequest".equals(request.getHeader("X-Requested-With"))
                || (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE)
                && !accept.contains(MediaType.TEXT_HTML_VALUE));
    }
}
