package com.aloute.service.user;

import com.aloute.exception.user.AccountExistsException;
import com.aloute.exception.user.RegistrationClosedException;
import com.aloute.model.user.AuthProvider;
import com.aloute.model.user.Profile;
import com.aloute.model.user.Role;
import com.aloute.model.user.User;
import com.aloute.repository.user.UserRepository;
import com.aloute.util.user.PasswordPolicy;

import com.aloute.service.admin.SystemSettings;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class UserService {

    /** Username dành riêng, tránh giả mạo đội ngũ vận hành. */
    private static final Set<String> RESERVED = Set.of(
            "admin", "administrator", "aloute", "support", "system", "root",
            "moderator", "manager", "creator", "help", "staff", "null", "undefined");

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final SystemSettings settings;

    public UserService(UserRepository users, PasswordEncoder encoder, SystemSettings settings) {
        this.settings = settings;
        this.users = users;
        this.encoder = encoder;
    }

    /**
     * Tạo tài khoản Local với vai trò USER.
     *
     * @throws AccountExistsException email/username đã tồn tại hoặc username bị dành riêng
     * @throws IllegalArgumentException mật khẩu không đạt {@link PasswordPolicy}
     */
    @Transactional
    public User registerLocal(String displayName, String username, String email, String rawPassword) {
        requireRegistrationOpen();
        String error = PasswordPolicy.validate(rawPassword);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
        String normalizedUsername = username.trim().toLowerCase(Locale.ROOT);
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        if (RESERVED.contains(normalizedUsername)) {
            throw new AccountExistsException("username", "Username này đã được dành riêng, chọn cái khác nhé");
        }
        if (users.existsByEmail(normalizedEmail)) {
            throw new AccountExistsException("email", "Email này đã có tài khoản rồi");
        }
        if (users.existsByUsername(normalizedUsername)) {
            throw new AccountExistsException("username", "Username này có người dùng mất rồi");
        }

        User user = newUser(displayName, normalizedUsername, normalizedEmail);
        user.setPasswordHash(encoder.encode(rawPassword));
        return saveTranslatingConflicts(user);
    }

    /** Tạo tài khoản từ đăng nhập Social; email đã được nhà cung cấp xác thực. */
    @Transactional
    public User registerSocial(String displayName, String email, AuthProvider provider,
                               String firebaseUid, String avatarUrl) {
        requireRegistrationOpen();
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        User user = newUser(displayName, uniqueUsernameFrom(normalizedEmail), normalizedEmail);
        user.setAuthProvider(provider);
        user.setFirebaseUid(firebaseUid);
        user.setEmailVerified(true);
        user.getProfile().setAvatarUrl(avatarUrl);
        return saveTranslatingConflicts(user);
    }

    @Transactional
    public void changePassword(User user, String rawPassword) {
        String error = PasswordPolicy.validate(rawPassword);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
        user.setPasswordHash(encoder.encode(rawPassword));
        users.save(user);
    }

    private void requireRegistrationOpen() {
        if (!settings.registrationOpen()) {
            throw new RegistrationClosedException();
        }
    }

    /**
     * Tự nâng cấp lên Creator (không cần duyệt). Quyền nằm trong JWT nên người gọi phải cấp lại phiên để có hiệu lực ngay.
     *
     * @return người dùng sau khi cập nhật
     */
    @Transactional
    public User becomeCreator(UUID userId) {
        User user = users.findById(userId).filter(User::isActive).orElseThrow();
        user.getRoles().add(Role.CREATOR);
        return users.save(user);
    }

    /** Sinh username duy nhất từ phần trước dấu @ của email, thêm hậu tố số nếu trùng. */
    String uniqueUsernameFrom(String email) {
        String base = email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.]", "");
        if (base.length() < 3) {
            base = "user" + base;
        }
        if (base.length() > 24) {
            base = base.substring(0, 24);
        }
        String candidate = base;
        while (RESERVED.contains(candidate) || users.existsByUsername(candidate)) {
            candidate = base + ThreadLocalRandom.current().nextInt(100, 10_000);
        }
        return candidate;
    }

    private static User newUser(String displayName, String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        Profile profile = new Profile();
        profile.setDisplayName(displayName.trim());
        user.attachProfile(profile);
        return user;
    }

    /** Hai người đăng ký cùng lúc có thể lọt qua bước kiểm tra; ràng buộc unique của DB là chốt chặn cuối. */
    private User saveTranslatingConflicts(User user) {
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new AccountExistsException("email", "Email hoặc username này vừa có người dùng mất rồi");
        }
    }
}
