package com.aloute.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Toàn bộ cấu hình tuỳ biến của ALOUTE (tiền tố {@code aloute.*}). */
@ConfigurationProperties(prefix = "aloute")
public record AlouteProperties(
        String baseUrl,
        Jwt jwt,
        Cookie cookie,
        Seed seed,
        Mail mail,
        Storage storage,
        Firebase firebase,
        Security security) {

    public record Jwt(String secret, int accessMinutes, int refreshDays) {
    }

    public record Cookie(boolean secure) {
    }

    /**
     * {@code adminEmail}: email của tài khoản admin khởi tạo (đặt email thật để dùng được "Quên mật khẩu").
     * {@code demoAccounts}: tạo thêm manager/creator/user mẫu (chỉ dùng khi dev).
     */
    public record Seed(String password, String adminEmail, boolean demoAccounts) {
    }

    public record Mail(String from) {
    }

    /** Ảnh người dùng lưu trên đĩa của máy chủ tại {@code localDir} (production: gắn Docker volume vào đó). */
    public record Storage(String localDir) {
    }

    /** Firebase chỉ dùng cho đăng nhập Social (Authentication); không dùng Storage. */
    public record Firebase(String credentials, Web web) {

        public boolean enabled() {
            return credentials != null && !credentials.isBlank();
        }

        /** Đủ thông tin để trang đăng nhập khởi tạo Firebase JS SDK. */
        public boolean webConfigured() {
            return web != null && web.apiKey() != null && !web.apiKey().isBlank();
        }

        public record Web(String apiKey, String authDomain, String projectId) {
        }
    }

    public record Security(int loginMaxAttempts, int loginWindowMinutes) {
    }
}
