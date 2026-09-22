package com.aloute.security;

import com.aloute.user.Role;
import com.aloute.user.User;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;

/**
 * Người dùng đang đăng nhập, dựng từ JWT (không cần chạm DB ở mỗi request).
 * {@code roles} là vai trò cơ sở; quyền kế thừa do RoleHierarchy xử lý.
 */
public record AlouteUserPrincipal(UUID id, String username, Set<Role> roles) implements Principal {

    public static AlouteUserPrincipal of(User user) {
        return new AlouteUserPrincipal(user.getId(), user.getUsername(), Set.copyOf(user.getRoles()));
    }

    @Override
    public String getName() {
        return id.toString();
    }
}
