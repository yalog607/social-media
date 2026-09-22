package com.aloute.security;

import com.aloute.config.AlouteProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/** Đặt/xóa cookie phiên. Cả hai cookie đều HttpOnly + SameSite=Lax để JS không đọc được token. */
@Component
public class CookieService {

    public static final String ACCESS_COOKIE = "ALOUTE_TOKEN";
    public static final String REFRESH_COOKIE = "ALOUTE_REFRESH";
    /** Refresh cookie chỉ gửi kèm các request tới /auth để giảm bề mặt lộ token. */
    static final String REFRESH_PATH = "/auth";

    private final boolean secure;

    public CookieService(AlouteProperties props) {
        this.secure = props.cookie().secure();
    }

    public void writeAccess(HttpServletResponse response, String token, Duration ttl) {
        add(response, build(ACCESS_COOKIE, token, "/", ttl));
    }

    public void writeRefresh(HttpServletResponse response, String token, Duration ttl) {
        add(response, build(REFRESH_COOKIE, token, REFRESH_PATH, ttl));
    }

    public void clear(HttpServletResponse response) {
        add(response, build(ACCESS_COOKIE, "", "/", Duration.ZERO));
        add(response, build(REFRESH_COOKIE, "", REFRESH_PATH, Duration.ZERO));
    }

    public Optional<String> read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private ResponseCookie build(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(path)
                .maxAge(maxAge)
                .build();
    }

    private void add(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
