package com.aloute.auth;

import com.aloute.user.AuthProvider;

/** Danh tính đã được nhà cung cấp Social xác thực (qua Firebase ID token). */
public record SocialIdentity(
        String uid,
        String email,
        boolean emailVerified,
        String name,
        String pictureUrl,
        AuthProvider provider) {
}
