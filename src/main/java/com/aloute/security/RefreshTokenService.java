package com.aloute.security;

import com.aloute.config.AlouteProperties;
import com.aloute.user.User;
import com.aloute.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Refresh token xoay vòng: mỗi lần dùng sẽ bị thu hồi và cấp token mới.
 * Nếu một token đã bị thu hồi lâu rồi mà vẫn được trình ra, coi như bị đánh cắp
 * và thu hồi toàn bộ phiên của người dùng đó.
 */
@Service
public class RefreshTokenService {

    /** Cho phép request song song (trang + ajax) cùng trình token vừa xoay mà không bị coi là tái sử dụng. */
    static final Duration REUSE_GRACE = Duration.ofSeconds(10);

    private final RefreshTokenRepository tokens;
    private final UserRepository users;
    private final Duration ttl;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository tokens, UserRepository users,
                               AlouteProperties props, Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.ttl = Duration.ofDays(props.jwt().refreshDays());
        this.clock = clock;
    }

    public Duration ttl() {
        return ttl;
    }

    /** Cấp refresh token mới, trả về giá trị thô để đặt vào cookie. */
    @Transactional
    public String issue(User user, String userAgent) {
        String raw = Tokens.random();
        RefreshToken entity = new RefreshToken();
        entity.setUserId(user.getId());
        entity.setTokenHash(Tokens.sha256Hex(raw));
        entity.setExpiresAt(clock.instant().plus(ttl));
        entity.setUserAgent(truncate(userAgent));
        tokens.save(entity);
        return raw;
    }

    public record Rotation(User user, String newRawToken) {
    }

    /** Đổi refresh token cũ lấy cặp (user, token mới). Rỗng nếu token không hợp lệ. */
    @Transactional
    public Optional<Rotation> rotate(String rawToken, String userAgent) {
        Instant now = clock.instant();
        Optional<RefreshToken> found = tokens.findByTokenHash(Tokens.sha256Hex(rawToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        RefreshToken current = found.get();

        if (current.getRevokedAt() != null) {
            boolean withinGrace = current.getRevokedAt().plus(REUSE_GRACE).isAfter(now);
            if (!withinGrace) {
                tokens.revokeAllForUser(current.getUserId(), now);
            }
            return Optional.empty();
        }
        if (!current.getExpiresAt().isAfter(now)) {
            return Optional.empty();
        }

        Optional<User> user = users.findById(current.getUserId()).filter(User::isActive);
        if (user.isEmpty()) {
            tokens.revokeAllForUser(current.getUserId(), now);
            return Optional.empty();
        }

        current.setRevokedAt(now);
        return Optional.of(new Rotation(user.get(), issue(user.get(), userAgent)));
    }

    @Transactional
    public void revoke(String rawToken) {
        tokens.findByTokenHash(Tokens.sha256Hex(rawToken)).ifPresent(t -> {
            if (t.getRevokedAt() == null) {
                t.setRevokedAt(clock.instant());
            }
        });
    }

    @Transactional
    public void revokeAll(UUID userId) {
        tokens.revokeAllForUser(userId, clock.instant());
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 255 ? value.substring(0, 255) : value;
    }
}
