package com.aloute.controller.auth;

import com.aloute.util.common.SafeRedirect;
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
import org.springframework.ui.Model;

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
                                   @RequestParam(defaultValue = "0") int retry,
                                   HttpServletRequest request, HttpServletResponse response, Model model) {
        String target = SafeRedirect.sanitize(next);
        var result = rotate(request, response);
        if (result.rotation().isPresent()) {
            return "redirect:" + target;
        }
        if (result.retry()) {
            model.addAttribute("retryUrl", "/auth/refresh?next=" + UriUtils.encodeQueryParam(target, StandardCharsets.UTF_8)
                    + "&retry=" + (retry < 20 ? retry + 1 : 0));
            model.addAttribute("automaticRetry", retry < 20);
            return "auth/refresh-retry";
        }
        return "redirect:/login?next=" + UriUtils.encodeQueryParam(target, StandardCharsets.UTF_8);
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<Void> refreshForFetch(HttpServletRequest request, HttpServletResponse response) {
        var result = rotate(request, response);
        if (result.rotation().isPresent()) {
            return ResponseEntity.noContent().build();
        }
        if (result.retry()) return ResponseEntity.status(409).build();
        return ResponseEntity.status(401).build();
    }

    @GetMapping("/api/auth/session")
    public ResponseEntity<Void> session() {
        return ResponseEntity.noContent().build();
    }

    private RefreshTokenService.RotationResult rotate(HttpServletRequest request, HttpServletResponse response) {
        var result = cookies.read(request, CookieService.REFRESH_COOKIE)
                .map(raw -> refreshTokens.rotateResult(raw, request.getHeader(HttpHeaders.USER_AGENT)))
                .orElseGet(() -> new RefreshTokenService.RotationResult(Optional.empty(), false));
        result.rotation().ifPresent(r -> sessions.writeCookies(r.user(), r.newRawToken(), response));
        return result;
    }
}
