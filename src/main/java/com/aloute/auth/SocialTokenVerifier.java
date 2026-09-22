package com.aloute.auth;

/** Xác thực ID token Social. Tách thành interface để test không cần Firebase thật. */
public interface SocialTokenVerifier {

    /** {@code false} khi máy chủ chưa cấu hình Firebase. */
    boolean enabled();

    /** @throws InvalidSocialTokenException token sai, hết hạn hoặc không đọc được */
    SocialIdentity verify(String idToken);

    class InvalidSocialTokenException extends RuntimeException {
        public InvalidSocialTokenException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
