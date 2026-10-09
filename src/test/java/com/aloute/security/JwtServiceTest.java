package com.aloute.security;

import com.aloute.config.AlouteProperties;
import com.aloute.model.user.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-0123456789";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private static AlouteProperties props(String secret) {
        return new AlouteProperties(null, new AlouteProperties.Jwt(secret, 15, 7),
                null, null, null, null, null, null);
    }

    private static JwtService service(String secret, Instant now) {
        return new JwtService(props(secret), Clock.fixed(now, ZoneOffset.UTC));
    }

    private static AlouteUserPrincipal someone() {
        return new AlouteUserPrincipal(UUID.randomUUID(), "mochi", Set.of(Role.USER, Role.CREATOR));
    }

    @Test
    void roundTripKeepsIdentityAndRoles() {
        JwtService jwt = service(SECRET, NOW);
        AlouteUserPrincipal user = someone();

        Optional<AlouteUserPrincipal> parsed = jwt.parse(jwt.generateAccessToken(user));

        assertThat(parsed).contains(user);
    }

    @Test
    void expiredTokenIsRejected() {
        AlouteUserPrincipal user = someone();
        String token = service(SECRET, NOW).generateAccessToken(user);

        JwtService later = service(SECRET, NOW.plus(Duration.ofMinutes(16)));

        assertThat(later.parse(token)).isEmpty();
    }

    @Test
    void tokenIsStillValidJustBeforeExpiry() {
        AlouteUserPrincipal user = someone();
        String token = service(SECRET, NOW).generateAccessToken(user);

        JwtService almost = service(SECRET, NOW.plus(Duration.ofMinutes(14)));

        assertThat(almost.parse(token)).contains(user);
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService jwt = service(SECRET, NOW);
        String token = jwt.generateAccessToken(someone());
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        assertThat(jwt.parse(tampered)).isEmpty();
    }

    /**
     * Hồi quy: jjwt tự chọn HS384/HS512 theo độ dài khóa và bỏ qua lặng lẽ một ký tự base64 dư ở cuối
     * khi chữ ký dài chia hết cho 4 (HS384 = 64 ký tự). Bộ kiểm tra này chạy với khóa 32/48/64+ byte.
     */
    @ParameterizedTest
    @ValueSource(ints = {32, 47, 48, 51, 64, 80})
    void tokenWithExtraTrailingCharactersIsRejectedForAnyKeyLength(int keyBytes) {
        JwtService jwt = service("k".repeat(keyBytes), NOW);
        String token = jwt.generateAccessToken(someone());

        assertThat(jwt.parse(token)).isPresent();
        assertThat(jwt.parse(token + "x")).isEmpty();
        assertThat(jwt.parse(token + "xx")).isEmpty();
        assertThat(jwt.parse(token + ".extra")).isEmpty();
    }

    @Test
    void alwaysSignsWithHs256RegardlessOfKeyLength() {
        JwtService jwt = service("k".repeat(80), NOW);
        String header = new String(java.util.Base64.getUrlDecoder().decode(
                jwt.generateAccessToken(someone()).split("\\.")[0]));

        assertThat(header).contains("HS256");
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String forged = service("another-secret-another-secret-another-secret-99", NOW)
                .generateAccessToken(someone());

        assertThat(service(SECRET, NOW).parse(forged)).isEmpty();
    }

    @Test
    void garbageIsRejectedWithoutThrowing() {
        JwtService jwt = service(SECRET, NOW);

        assertThat(jwt.parse("not-a-jwt")).isEmpty();
        assertThat(jwt.parse("")).isEmpty();
    }

    @Test
    void weakOrMissingSecretFailsFast() {
        assertThatThrownBy(() -> service("too-short", NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service(null, NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service("", NOW)).isInstanceOf(IllegalStateException.class);
    }
}
