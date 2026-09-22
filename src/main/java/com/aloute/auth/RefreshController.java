package com.aloute.auth;

import com.aloute.common.SafeRedirect;
import com.aloute.security.CookieService;
import com.aloute.security.RefreshTokenService;
import com.aloute.security.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Làm mới phiên bằng refresh cookie (cookie này chỉ được gửi tới /auth/**).
 * GET dành cho điều hướng trang (do {@code AuthEntryPoint} chuyển tới), POST dành cho fetch/AJAX.
 */
@Controller
public class RefreshController {

    private final CookieService cookies;
    private final RefreshTokenService refreshTokens;
    private final SessionService sessions;

    public RefreshController(CookieService cookies, RefreshTokenService refreshTokens, SessionService sessions) {
        this.cookies = cookies;
        this.refreshTokens = refreshTokens;
        this.sessions = sessions;
    }

    @GetMapping("/auth/refresh")
    public String refreshAndReturn(@RequestParam(required = false) String next,
                                   HttpServletRequest request, HttpServletResponse response) {
        String target = SafeRedirect.sanitize(next);
        if (rotate(request, response)) {
            return "redirect:" + target;
        }
        cookies.clear(response);
        return "redirect:/login?next=" + UriUtils.encodeQueryParam(target, StandardCharsets.UTF_8);
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<Void> refreshForFetch(HttpServletRequest request, HttpServletResponse response) {
        if (rotate(request, response)) {
            return ResponseEntity.noContent().build();
        }
        cookies.clear(response);
        return ResponseEntity.status(401).build();
    }

    private boolean rotate(HttpServletRequest request, HttpServletResponse response) {
        Optional<RefreshTokenService.Rotation> rotation = cookies.read(request, CookieService.REFRESH_COOKIE)
                .flatMap(raw -> refreshTokens.rotate(raw, request.getHeader(HttpHeaders.USER_AGENT)));
        rotation.ifPresent(r -> sessions.writeCookies(r.user(), r.newRawToken(), response));
        return rotation.isPresent();
    }
}
