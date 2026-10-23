package com.aloute.service.auth;

import com.aloute.model.auth.PasswordResetToken;
import com.aloute.repository.auth.PasswordResetTokenRepository;

import com.aloute.service.common.MailService;
import com.aloute.config.AlouteProperties;
import com.aloute.security.RefreshTokenService;
import com.aloute.security.Tokens;
import com.aloute.model.user.User;
import com.aloute.repository.user.UserRepository;
import com.aloute.service.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class PasswordResetService {

    static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final UserService userService;
    private final RefreshTokenService refreshTokens;
    private final MailService mail;
    private final AlouteProperties props;
    private final Clock clock;

    public PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens,
                                UserService userService, RefreshTokenService refreshTokens,
                                MailService mail, AlouteProperties props, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.userService = userService;
        this.refreshTokens = refreshTokens;
        this.mail = mail;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Gửi link đặt lại mật khẩu nếu email thuộc một tài khoản đang hoạt động.
     * Luôn trả về bình thường dù email có tồn tại hay không để không lộ danh sách tài khoản.
     */
    @Transactional
    public void request(String email) {
        Optional<User> found = users.findByEmail(email.trim()).filter(User::isActive);
        if (found.isEmpty()) {
            return;
        }
        User user = found.get();
        Instant now = clock.instant();
        tokens.invalidateAllForUser(user.getId(), now);

        String raw = Tokens.random();
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.getId());
        token.setTokenHash(Tokens.sha256Hex(raw));
        token.setExpiresAt(now.plus(TOKEN_TTL));
        tokens.save(token);

        String link = UriComponentsBuilder.fromUriString(props.baseUrl())
                .path("/reset-password").queryParam("token", raw).build().toUriString();
        mail.sendPasswordReset(user.getEmail(), user.getProfile().getDisplayName(), link,
                (int) TOKEN_TTL.toMinutes());
    }

    /** Link còn dùng được (đúng, chưa hết hạn, chưa dùng). */
    @Transactional(readOnly = true)
    public boolean isUsable(String rawToken) {
        return rawToken != null && findUsable(rawToken).isPresent();
    }

    /**
     * Đặt mật khẩu mới rồi đánh dấu token đã dùng và thu hồi mọi phiên đăng nhập cũ.
     *
     * @return {@code false} nếu token không hợp lệ/hết hạn/đã dùng
     * @throws IllegalArgumentException mật khẩu không đạt chính sách
     */
    @Transactional
    public boolean reset(String rawToken, String newPassword) {
        if (rawToken == null) {
            return false;
        }
        Optional<PasswordResetToken> usable = findUsable(rawToken);
        if (usable.isEmpty()) {
            return false;
        }
        PasswordResetToken token = usable.get();
        Optional<User> user = users.findById(token.getUserId()).filter(User::isActive);
        if (user.isEmpty()) {
            return false;
        }
        userService.changePassword(user.get(), newPassword);
        // Nhận được link qua email chính là bằng chứng sở hữu email đó
        user.get().setEmailVerified(true);
        token.setUsedAt(clock.instant());
        refreshTokens.revokeAll(user.get().getId());
        return true;
    }

    private Optional<PasswordResetToken> findUsable(String rawToken) {
        Instant now = clock.instant();
        return tokens.findByTokenHash(Tokens.sha256Hex(rawToken))
                .filter(t -> t.getUsedAt() == null && t.getExpiresAt().isAfter(now));
    }
}
