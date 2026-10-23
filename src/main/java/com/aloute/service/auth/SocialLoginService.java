package com.aloute.service.auth;

import com.aloute.dto.auth.SocialIdentity;

import com.aloute.model.user.User;
import com.aloute.repository.user.UserRepository;
import com.aloute.service.user.UserService;
import com.aloute.model.user.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

/** Đăng nhập bằng Google/Facebook: tìm hoặc tạo tài khoản theo danh tính đã được nhà cung cấp xác thực. */
@Service
public class SocialLoginService {

    /** Danh tính Social không có email đã xác minh nên không thể liên kết an toàn. */
    public static class UnverifiedEmailException extends RuntimeException {
        public UnverifiedEmailException() {
            super("Tài khoản Social này chưa có email đã xác minh");
        }
    }

    private final UserRepository users;
    private final UserService userService;
    private final Clock clock;

    public SocialLoginService(UserRepository users, UserService userService, Clock clock) {
        this.users = users;
        this.userService = userService;
        this.clock = clock;
    }

    /**
     * @throws UnverifiedEmailException thiếu email hoặc email chưa xác minh
     * @throws AuthService.AccountSuspendedException tài khoản bị khóa
     */
    @Transactional
    public User login(SocialIdentity identity) {
        if (identity.email() == null || identity.email().isBlank() || !identity.emailVerified()) {
            throw new UnverifiedEmailException();
        }

        User user = users.findByFirebaseUid(identity.uid())
                .or(() -> linkToExistingByEmail(identity))
                .orElseGet(() -> userService.registerSocial(displayNameOf(identity), identity.email(),
                        identity.provider(), identity.uid(), identity.pictureUrl()));

        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new AuthService.AccountSuspendedException();
        }
        if (user.getStatus() == UserStatus.DELETED) {
            throw new AuthService.InvalidCredentialsException();
        }
        user.setLastLoginAt(clock.instant());
        return user;
    }

    /**
     * Email đã có tài khoản: liên kết danh tính Social vào đó.
     * Nếu tài khoản cũ là Local chưa xác minh email, ta vô hiệu hóa mật khẩu cũ. Nếu không, kẻ tạo trước tài khoản
     * bằng email của nạn nhân (pre-hijacking) vẫn giữ được mật khẩu sau khi nạn nhân đăng nhập Google.
     * Chủ email thật đặt lại mật khẩu qua "Quên mật khẩu" khi cần.
     */
    private Optional<User> linkToExistingByEmail(SocialIdentity identity) {
        return users.findByEmail(identity.email()).map(existing -> {
            if (!existing.isEmailVerified()) {
                existing.setPasswordHash(null);
            }
            existing.setEmailVerified(true);
            existing.setFirebaseUid(identity.uid());
            if (existing.getPasswordHash() == null) {
                existing.setAuthProvider(identity.provider());
            }
            return existing;
        });
    }

    private static String displayNameOf(SocialIdentity identity) {
        if (identity.name() != null && !identity.name().isBlank()) {
            String name = identity.name().trim();
            return name.length() > 50 ? name.substring(0, 50) : name;
        }
        return identity.email().substring(0, identity.email().indexOf('@'));
    }
}
