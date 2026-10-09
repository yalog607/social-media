package com.aloute.util.user;

import java.nio.charset.StandardCharsets;

/** Quy tắc mật khẩu dùng chung cho đăng ký, đặt lại và đổi mật khẩu. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    /** BCrypt chỉ dùng 72 byte đầu, nên chặn cả trường hợp ký tự đa byte (tiếng Việt, emoji). */
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    /** @return thông báo lỗi tiếng Việt, hoặc {@code null} nếu mật khẩu hợp lệ. */
    public static String validate(String raw) {
        if (raw == null || raw.length() < MIN_LENGTH) {
            return "Mật khẩu cần ít nhất " + MIN_LENGTH + " ký tự";
        }
        if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return "Mật khẩu quá dài (tối đa " + MAX_BYTES + " byte)";
        }
        boolean hasLetter = raw.chars().anyMatch(Character::isLetter);
        boolean hasDigit = raw.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            return "Mật khẩu cần có cả chữ và số";
        }
        return null;
    }
}
