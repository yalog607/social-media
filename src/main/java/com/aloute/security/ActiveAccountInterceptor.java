package com.aloute.security;

import com.aloute.model.user.User;
import com.aloute.repository.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * JWT không cần chạm DB nên một tài khoản vừa bị khóa/xóa vẫn còn access token hợp lệ tới vài phút.
 * Interceptor này kiểm tra tài khoản còn hoạt động trước khi vào controller; nếu không thì thu hồi phiên
 * và đưa về trang đăng nhập (hoặc 401 JSON với API). Vai trò được làm tươi ở {@link JwtAuthFilter}, nơi cũng
 * đã tải {@code User} và lưu vào request; interceptor chỉ truy vấn lại khi attribute đó vắng mặt
 * (ví dụ user bị xóa cứng khỏi DB), để tránh truy vấn hai lần.
 */
@Component
public class ActiveAccountInterceptor implements HandlerInterceptor {

    public static final String CURRENT_USER_ATTRIBUTE = ActiveAccountInterceptor.class.getName() + ".USER";

    private final UserRepository users;
    private final RefreshTokenService refreshTokens;
    private final CookieService cookies;

    public ActiveAccountInterceptor(UserRepository users, RefreshTokenService refreshTokens, CookieService cookies) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.cookies = cookies;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AlouteUserPrincipal principal)) {
            return true;
        }
        Optional<User> active = loadedByFilter(request)
                .or(() -> users.findById(principal.id()))
                .filter(User::isActive);
        if (active.isPresent()) {
            request.setAttribute(CURRENT_USER_ATTRIBUTE, active.get());
            return true;
        }

        refreshTokens.revokeAll(principal.id());
        cookies.clear(response);
        if (AuthEntryPoint.isApi(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"error\":\"unauthorized\",\"message\":\"Tài khoản không còn hoạt động\"}");
        } else {
            response.sendRedirect(request.getContextPath() + "/login");
        }
        return false;
    }

    private Optional<User> loadedByFilter(HttpServletRequest request) {
        return Optional.ofNullable(request.getAttribute(CURRENT_USER_ATTRIBUTE))
                .filter(User.class::isInstance)
                .map(User.class::cast);
    }
}
