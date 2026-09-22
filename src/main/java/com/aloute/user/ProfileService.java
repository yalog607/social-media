package com.aloute.user;

import com.aloute.security.RefreshTokenService;
import com.aloute.storage.StorageService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/** Cập nhật hồ sơ, quyền riêng tư và mật khẩu của chính người dùng. */
@Service
public class ProfileService {

    /** Mật khẩu hiện tại nhập sai khi đổi mật khẩu. */
    public static class WrongCurrentPasswordException extends RuntimeException {
        public WrongCurrentPasswordException() {
            super("Mật khẩu hiện tại chưa đúng");
        }
    }

    private final UserRepository users;
    private final UserService userService;
    private final StorageService storage;
    private final PasswordEncoder encoder;
    private final RefreshTokenService refreshTokens;

    public ProfileService(UserRepository users, UserService userService, StorageService storage,
                          PasswordEncoder encoder, RefreshTokenService refreshTokens) {
        this.users = users;
        this.userService = userService;
        this.storage = storage;
        this.encoder = encoder;
        this.refreshTokens = refreshTokens;
    }

    /**
     * Cập nhật tên hiển thị, giới thiệu và (tuỳ chọn) ảnh đại diện/ảnh bìa.
     *
     * @throws StorageService.InvalidUploadException ảnh không hợp lệ
     */
    @Transactional
    public User updateProfile(UUID userId, String displayName, String bio,
                              MultipartFile avatar, MultipartFile cover) {
        User user = users.findById(userId).orElseThrow();
        Profile profile = user.getProfile();
        profile.setDisplayName(displayName.trim());
        profile.setBio(bio == null || bio.isBlank() ? null : bio.trim());
        if (avatar != null && !avatar.isEmpty()) {
            profile.setAvatarUrl(storage.storeImage(avatar, "avatars", userId));
        }
        if (cover != null && !cover.isEmpty()) {
            profile.setCoverUrl(storage.storeImage(cover, "covers", userId));
        }
        return user;
    }

    @Transactional
    public void updatePrivacy(UUID userId, Visibility profileVisibility, Visibility defaultPostVisibility,
                              MessagePermission messagePermission) {
        Profile profile = users.findById(userId).orElseThrow().getProfile();
        profile.setProfileVisibility(profileVisibility);
        profile.setDefaultPostVisibility(defaultPostVisibility);
        profile.setMessagePermission(messagePermission);
    }

    public boolean hasPassword(User user) {
        return user.getPasswordHash() != null;
    }

    /**
     * Đổi mật khẩu. Tài khoản đã có mật khẩu phải nhập đúng mật khẩu hiện tại;
     * tài khoản Social chưa có mật khẩu thì được đặt lần đầu.
     * Mọi phiên khác bị thu hồi; người gọi cần cấp lại phiên cho thiết bị hiện tại.
     *
     * @throws WrongCurrentPasswordException mật khẩu hiện tại sai
     * @throws IllegalArgumentException mật khẩu mới không đạt chính sách
     */
    @Transactional
    public User changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = users.findById(userId).orElseThrow();
        if (hasPassword(user)
                && (currentPassword == null || !encoder.matches(currentPassword, user.getPasswordHash()))) {
            throw new WrongCurrentPasswordException();
        }
        userService.changePassword(user, newPassword);
        refreshTokens.revokeAll(userId);
        return user;
    }
}
