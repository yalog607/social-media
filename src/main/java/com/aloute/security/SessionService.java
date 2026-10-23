package com.aloute.security;

import com.aloute.model.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

/** Bắt đầu/kết thúc phiên đăng nhập: cấp/thu hồi cặp cookie access + refresh. */
@Service
public class SessionService {

    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;
    private final CookieService cookies;

    public SessionService(JwtService jwtService, RefreshTokenService refreshTokens, CookieService cookies) {
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.cookies = cookies;
    }

    public void start(User user, HttpServletRequest request, HttpServletResponse response) {
        String refresh = refreshTokens.issue(user, request.getHeader(HttpHeaders.USER_AGENT));
        writeCookies(user, refresh, response);
    }

    /** Dùng khi xoay refresh token: đã có token mới, chỉ cần ghi cookie. */
    public void writeCookies(User user, String rawRefreshToken, HttpServletResponse response) {
        String access = jwtService.generateAccessToken(AlouteUserPrincipal.of(user));
        cookies.writeAccess(response, access, jwtService.accessTtl());
        cookies.writeRefresh(response, rawRefreshToken, refreshTokens.ttl());
    }

    public void end(HttpServletRequest request, HttpServletResponse response) {
        cookies.read(request, CookieService.REFRESH_COOKIE).ifPresent(refreshTokens::revoke);
        cookies.clear(response);
    }
}
