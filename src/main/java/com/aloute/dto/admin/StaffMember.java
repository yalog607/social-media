package com.aloute.dto.admin;

import com.aloute.model.user.UserStatus;

import java.util.UUID;

/** Một dòng trong danh sách người dùng/nhân sự của Admin. */
public record StaffMember(UUID id, String username, String displayName, String email, String roles, UserStatus status) {

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isSuspended() {
        return status == UserStatus.SUSPENDED;
    }

    public boolean isDeleted() {
        return status == UserStatus.DELETED;
    }

    public boolean isManager() {
        return roles.contains("MANAGER");
    }

    public boolean isAdmin() {
        return roles.contains("ADMIN");
    }
}
