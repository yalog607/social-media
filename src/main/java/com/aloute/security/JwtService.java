package com.aloute.security;

import com.aloute.config.AlouteProperties;
import com.aloute.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class JwtService {

    private static final String CLAIM_USERNAME = "uname";
    private static final String CLAIM_ROLES = "roles";
    private static final int MIN_SECRET_BYTES = 32;
    /** HS256 cho chữ ký 32 byte, mã hóa base64url không đệm là đúng 43 ký tự. */
    private static final int HS256_SIGNATURE_CHARS = 43;

    private final SecretKey key;
    private final Duration accessTtl;
    private final Clock clock;

    public JwtService(AlouteProperties props, Clock clock) {
        String secret = props.jwt().secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "aloute.jwt.secret (ALOUTE_JWT_SECRET) phải có ít nhất " + MIN_SECRET_BYTES + " ký tự. "
                            + "Khi chạy local hãy dùng --spring.profiles.active=dev");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = Duration.ofMinutes(props.jwt().accessMinutes());
        this.clock = clock;
    }

    public Duration accessTtl() {
        return accessTtl;
    }

    public String generateAccessToken(AlouteUserPrincipal principal) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(principal.id().toString())
                .claim(CLAIM_USERNAME, principal.username())
                .claim(CLAIM_ROLES, principal.roles().stream().map(Role::name).sorted().toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                // Ghim thuật toán: nếu không, jjwt tự đổi sang HS384/HS512 theo độ dài khóa
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** Trả về rỗng nếu token sai chữ ký, hết hạn hoặc sai định dạng. */
    public Optional<AlouteUserPrincipal> parse(String token) {
        if (!hasCanonicalShape(token)) {
            return Optional.empty();
        }
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token);
            if (!Jwts.SIG.HS256.getId().equals(jws.getHeader().getAlgorithm())) {
                return Optional.empty();
            }
            Claims claims = jws.getPayload();
            return Optional.of(new AlouteUserPrincipal(
                    UUID.fromString(claims.getSubject()),
                    claims.get(CLAIM_USERNAME, String.class),
                    parseRoles(claims.get(CLAIM_ROLES, List.class))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Bộ giải mã base64 của jjwt bỏ qua lặng lẽ một ký tự dư ở cuối chữ ký, nên cùng một token hợp lệ
     * có thể có nhiều chuỗi khác nhau. Chặn ngay từ đầu bằng cách đòi hỏi đúng 3 đoạn và chữ ký đúng độ dài.
     */
    private static boolean hasCanonicalShape(String token) {
        if (token == null) {
            return false;
        }
        int firstDot = token.indexOf('.');
        int lastDot = token.lastIndexOf('.');
        return firstDot > 0 && lastDot > firstDot
                && token.indexOf('.', firstDot + 1) == lastDot
                && token.length() - lastDot - 1 == HS256_SIGNATURE_CHARS;
    }

    private static Set<Role> parseRoles(List<?> raw) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        if (raw != null) {
            for (Object name : raw) {
                roles.add(Role.valueOf(String.valueOf(name)));
            }
        }
        return roles;
    }
}
