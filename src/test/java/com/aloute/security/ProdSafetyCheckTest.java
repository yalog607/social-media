package com.aloute.security;

import com.aloute.config.AlouteProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProdSafetyCheckTest {

    private static final String STRONG_SECRET = "Zx9fQ2mVb7Lk0RtYw3NpHs8DcJaE5uGiO1XyBvMqTn4KrWdUeCzA";
    private static final String HTTPS = "https://aloute.example";

    @TempDir Path tmp;
    private String uploads;

    @BeforeEach
    void setUp() {
        uploads = tmp.resolve("uploads").toString();
    }

    /** Cấu hình hợp lệ; mỗi test chỉ đổi đúng một tham số để chứng minh tham số đó bị bắt. */
    private AlouteProperties props(String secret, boolean cookieSecure, String baseUrl, String seedPassword,
                                   boolean demo, String uploadDir, String firebaseCredentials) {
        return new AlouteProperties(baseUrl,
                new AlouteProperties.Jwt(secret, 15, 7),
                new AlouteProperties.Cookie(cookieSecure),
                new AlouteProperties.Seed(seedPassword, "admin@aloute.example", demo),
                new AlouteProperties.Mail("no-reply@aloute.example"),
                new AlouteProperties.Storage(uploadDir),
                new AlouteProperties.Firebase(firebaseCredentials, null),
                new AlouteProperties.Security(5, 15));
    }

    private AlouteProperties valid() {
        return props(STRONG_SECRET, true, HTTPS, "", false, uploads, "");
    }

    @Test
    void validConfigurationHasNoProblems() {
        assertThat(ProdSafetyCheck.problems(valid(), "a-real-db-password")).isEmpty();
    }

    @Test
    void rejectsMissingShortOrPlaceholderJwtSecrets() {
        for (String bad : new String[]{null, "", "too-short",
                "dev-only-secret-change-me-dev-only-secret-change-me",
                "test-only-secret-test-only-secret-test-only-secret",
                "please-change-me-please-change-me-please-change-me"}) {
            var p = props(bad, true, HTTPS, "", false, uploads, "");
            assertThat(ProdSafetyCheck.problems(p, "pw")).as("secret=%s", bad)
                    .anyMatch(s -> s.contains("ALOUTE_JWT_SECRET"));
        }
    }

    @Test
    void rejectsInsecureCookies() {
        var p = props(STRONG_SECRET, false, HTTPS, "", false, uploads, "");
        assertThat(ProdSafetyCheck.problems(p, "pw")).anyMatch(s -> s.contains("ALOUTE_COOKIE_SECURE"));
    }

    @Test
    void requiresHttpsBaseUrl() {
        for (String bad : new String[]{null, "http://aloute.example", "aloute.example", "http://localhost:8080"}) {
            var p = props(STRONG_SECRET, true, bad, "", false, uploads, "");
            assertThat(ProdSafetyCheck.problems(p, "pw")).as("baseUrl=%s", bad)
                    .anyMatch(s -> s.contains("ALOUTE_BASE_URL"));
        }
    }

    @Test
    void rejectsBlankDatabasePassword() {
        assertThat(ProdSafetyCheck.problems(valid(), "")).anyMatch(s -> s.contains("DB_PASSWORD"));
        assertThat(ProdSafetyCheck.problems(valid(), null)).anyMatch(s -> s.contains("DB_PASSWORD"));
    }

    @Test
    void rejectsDemoAccounts() {
        var p = props(STRONG_SECRET, true, HTTPS, "", true, uploads, "");
        assertThat(ProdSafetyCheck.problems(p, "pw")).anyMatch(s -> s.contains("ALOUTE_SEED_DEMO"));
    }

    @Test
    void adminSeedPasswordMustBeStrongWhenProvidedAndIsIgnoredWhenAbsent() {
        for (String weak : new String[]{"Aloute@123", "short1A", "abcdefghijk"}) {
            var p = props(STRONG_SECRET, true, HTTPS, weak, false, uploads, "");
            assertThat(ProdSafetyCheck.problems(p, "pw")).as("seed=%s", weak)
                    .anyMatch(s -> s.contains("ALOUTE_SEED_PASSWORD"));
        }
        var strong = props(STRONG_SECRET, true, HTTPS, "Correct-Horse-Battery-9", false, uploads, "");
        assertThat(ProdSafetyCheck.problems(strong, "pw")).isEmpty();
    }

    @Test
    void firebaseIsOptionalButAReadableCredentialsFileIsRequiredWhenSet() throws IOException {
        Path credentials = Files.writeString(tmp.resolve("firebase.json"), "{}");

        var off = props(STRONG_SECRET, true, HTTPS, "", false, uploads, "");
        assertThat(ProdSafetyCheck.problems(off, "pw")).as("để trống = tắt đăng nhập Social, hợp lệ").isEmpty();

        var on = props(STRONG_SECRET, true, HTTPS, "", false, uploads, credentials.toString());
        assertThat(ProdSafetyCheck.problems(on, "pw")).isEmpty();

        var missing = props(STRONG_SECRET, true, HTTPS, "", false, uploads, tmp.resolve("nope.json").toString());
        assertThat(ProdSafetyCheck.problems(missing, "pw")).anyMatch(s -> s.contains("FIREBASE_CREDENTIALS"));

        var pointsToDirectory = props(STRONG_SECRET, true, HTTPS, "", false, uploads, tmp.toString());
        assertThat(ProdSafetyCheck.problems(pointsToDirectory, "pw")).as("thư mục không phải file khóa")
                .anyMatch(s -> s.contains("FIREBASE_CREDENTIALS"));
    }

    @Test
    void uploadDirectoryIsCreatedWhenMissing() {
        assertThat(Files.exists(Path.of(uploads))).isFalse();

        assertThat(ProdSafetyCheck.problems(valid(), "pw")).isEmpty();

        assertThat(Files.isDirectory(Path.of(uploads))).isTrue();
    }

    @Test
    void refusesToStartWhenUploadDirectoryCannotBeUsed() throws IOException {
        // Một FILE thường nằm chắn đường thì không thể tạo thư mục bên dưới: cách mô phỏng lỗi volume, chạy được trên mọi hệ điều hành
        Path blocker = Files.writeString(tmp.resolve("not-a-dir"), "x");
        var p = props(STRONG_SECRET, true, HTTPS, "", false, blocker.resolve("uploads").toString(), "");

        assertThat(ProdSafetyCheck.problems(p, "pw")).anyMatch(s -> s.contains("thư mục lưu ảnh"));
    }

    @Test
    void reportsEveryProblemAtOnceAndRefusesToStart() throws IOException {
        Path blocker = Files.writeString(tmp.resolve("blocker"), "x");
        var p = props("short", false, "http://x", "Aloute@123", true, blocker.resolve("u").toString(), tmp.resolve("no.json").toString());

        assertThatThrownBy(() -> new ProdSafetyCheck(p, new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("từ chối khởi động")
                .hasMessageContaining("ALOUTE_JWT_SECRET")
                .hasMessageContaining("ALOUTE_COOKIE_SECURE")
                .hasMessageContaining("ALOUTE_BASE_URL")
                .hasMessageContaining("DB_PASSWORD")
                .hasMessageContaining("ALOUTE_SEED_DEMO")
                .hasMessageContaining("ALOUTE_SEED_PASSWORD")
                .hasMessageContaining("FIREBASE_CREDENTIALS")
                .hasMessageContaining("thư mục lưu ảnh");
    }

    @Test
    void safeConfigurationStartsNormally() {
        var env = new MockEnvironment().withProperty("spring.datasource.password", "a-real-db-password");
        new ProdSafetyCheck(valid(), env);
    }

    @Test
    void oneProblemProducesOneLine() {
        var p = props(STRONG_SECRET, false, HTTPS, "", false, uploads, "");
        assertThat(ProdSafetyCheck.problems(p, "pw")).hasSize(1);
    }
}
