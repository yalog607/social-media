package com.aloute.controller.auth;

import com.aloute.service.auth.AuthService;
import com.aloute.service.auth.SocialLoginService;
import com.aloute.service.auth.SocialTokenVerifier;

import com.aloute.security.SessionService;
import com.aloute.model.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Nhận Firebase ID token từ trình duyệt, xác thực rồi cấp phiên (cookie JWT) của ALOUTE. */
@RestController
public class SocialAuthController {

    public record FirebaseLoginRequest(@NotBlank String idToken) {
    }

    private final SocialTokenVerifier verifier;
    private final SocialLoginService socialLogin;
    private final SessionService sessions;

    public SocialAuthController(SocialTokenVerifier verifier, SocialLoginService socialLogin, SessionService sessions) {
        this.verifier = verifier;
        this.socialLogin = socialLogin;
        this.sessions = sessions;
    }

    @PostMapping("/auth/firebase")
    public ResponseEntity<Map<String, String>> firebaseLogin(@Valid @RequestBody FirebaseLoginRequest body,
                                                             HttpServletRequest request,
                                                             HttpServletResponse response) {
        if (!verifier.enabled()) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "Đăng nhập Google/Facebook chưa được bật");
        }
        try {
            User user = socialLogin.login(verifier.verify(body.idToken()));
            sessions.start(user, request, response);
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (SocialTokenVerifier.InvalidSocialTokenException e) {
            return error(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập Social không hợp lệ, thử lại nhé");
        } catch (SocialLoginService.UnverifiedEmailException e) {
            return error(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Tài khoản này chưa có email đã xác minh nên không đăng nhập được");
        } catch (com.aloute.exception.user.RegistrationClosedException e) {
            return error(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (AuthService.AccountSuspendedException e) {
            return error(HttpStatus.FORBIDDEN, "Tài khoản của bạn đang bị khóa");
        } catch (AuthService.InvalidCredentialsException e) {
            return error(HttpStatus.UNAUTHORIZED, "Không đăng nhập được bằng tài khoản này");
        }
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }
}
