package com.aloute.service.auth;

import com.aloute.model.user.User;
import com.aloute.repository.user.UserRepository;
import com.aloute.model.user.UserStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;
    /** Băm giả để thời gian phản hồi không lộ việc tài khoản có tồn tại hay không. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
        this.dummyHash = encoder.encode("aloute-dummy-password-1");
    }

    /** Sai email/username/mật khẩu. Cố ý không cho biết sai ở đâu. */
    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("Sai thông tin đăng nhập");
        }
    }

    /** Tài khoản đang bị khóa; chỉ báo sau khi mật khẩu đã đúng để không lộ sự tồn tại của tài khoản. */
    public static class AccountSuspendedException extends RuntimeException {
        public AccountSuspendedException() {
            super("Tài khoản đang bị khóa");
        }
    }

    @Transactional
    public User authenticate(String identifier, String rawPassword) {
        String id = identifier.trim();
        Optional<User> found = id.contains("@") ? users.findByEmail(id) : users.findByUsername(id);

        String storedHash = found.map(User::getPasswordHash).orElse(null);
        boolean passwordOk = matches(rawPassword, storedHash != null ? storedHash : dummyHash) && storedHash != null;
        if (found.isEmpty() || !passwordOk || found.get().getStatus() == UserStatus.DELETED) {
            throw new InvalidCredentialsException();
        }

        User user = found.get();
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new AccountSuspendedException();
        }
        user.setLastLoginAt(clock.instant());
        return user;
    }

    private boolean matches(String raw, String hash) {
        try {
            return encoder.matches(raw, hash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
