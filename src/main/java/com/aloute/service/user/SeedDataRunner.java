package com.aloute.service.user;

import com.aloute.model.user.Profile;
import com.aloute.model.user.Role;
import com.aloute.model.user.User;
import com.aloute.repository.user.UserRepository;

import com.aloute.config.AlouteProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

/**
 * Tạo tài khoản khởi tạo khi ứng dụng chạy (idempotent theo email).
 * Mật khẩu lấy từ cấu hình/biến môi trường, không hard-code trong mã nguồn.
 */
@Component
public class SeedDataRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataRunner.class);

    private record Seed(String email, String username, String displayName, Set<Role> roles) {
    }

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AlouteProperties props;

    public SeedDataRunner(UserRepository users, PasswordEncoder encoder, AlouteProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(String... args) {
        String password = props.seed().password();
        if (password == null || password.isBlank()) {
            return;
        }
        String adminEmail = props.seed().adminEmail();
        if (adminEmail == null || adminEmail.isBlank()) {
            adminEmail = "admin@aloute.local";
        }
        seed(new Seed(adminEmail.trim(), "admin", "Quản trị ALOUTE", EnumSet.of(Role.USER, Role.ADMIN)), password);
        if (props.seed().demoAccounts()) {
            seed(new Seed("manager@aloute.local", "manager", "Mod Mint", EnumSet.of(Role.USER, Role.MANAGER)), password);
            seed(new Seed("creator@aloute.local", "creator", "Creator Chanh", EnumSet.of(Role.USER, Role.CREATOR)), password);
            seed(new Seed("user@aloute.local", "mochi", "Mochi", EnumSet.of(Role.USER)), password);
        }
    }

    private void seed(Seed seed, String password) {
        if (users.existsByEmail(seed.email())) {
            return;
        }
        User user = new User();
        user.setEmail(seed.email());
        user.setUsername(seed.username());
        user.setPasswordHash(encoder.encode(password));
        user.setEmailVerified(true);
        user.setRoles(seed.roles());
        Profile profile = new Profile();
        profile.setDisplayName(seed.displayName());
        user.attachProfile(profile);
        users.save(user);
        log.info("Đã tạo tài khoản khởi tạo {} ({})", seed.email(), seed.roles());
    }
}
