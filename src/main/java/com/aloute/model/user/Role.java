package com.aloute.model.user;

/**
 * Vai trò cơ sở lưu trong DB. Quan hệ kế thừa (ADMIN > MANAGER > USER, CREATOR > USER)
 * được khai báo một lần ở {@code SecurityConfig#roleHierarchy}.
 */
public enum Role {
    USER("Người dùng"),
    CREATOR("Nhà sáng tạo"),
    MANAGER("Quản lý"),
    ADMIN("Quản trị viên");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Tên authority dùng trong Spring Security, ví dụ ROLE_ADMIN. */
    public String authority() {
        return "ROLE_" + name();
    }
}
