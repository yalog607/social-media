package com.aloute.util.common;

/** Chống open-redirect: chỉ chấp nhận đường dẫn nội bộ dạng "/abc?x=y". */
public final class SafeRedirect {

    private static final String HOME = "/";

    private SafeRedirect() {
    }

    public static String sanitize(String next) {
        if (next == null || next.isBlank()) {
            return HOME;
        }
        String value = next.trim();
        boolean internal = value.startsWith("/")
                && !value.startsWith("//")
                && !value.startsWith("/\\")
                && value.chars().noneMatch(c -> c < 0x20 || c == 0x7f || c == '\\');
        if (!internal) {
            return HOME;
        }
        // Tránh vòng lặp chuyển hướng quay về trang xác thực
        if (value.startsWith("/login") || value.startsWith("/register") || value.startsWith("/auth/")) {
            return HOME;
        }
        return value;
    }
}
