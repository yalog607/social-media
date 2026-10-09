package com.aloute.support;

import com.aloute.security.AlouteUserPrincipal;
import com.aloute.security.JwtService;
import com.aloute.model.user.Profile;
import com.aloute.model.user.Role;
import com.aloute.model.user.User;
import com.aloute.repository.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Nền cho test tích hợp: Spring Boot đầy đủ + PostgreSQL thật (Testcontainers, dùng chung cho mọi lớp test).
 * Mỗi test tự tạo dữ liệu với email/username ngẫu nhiên nên không cần dọn DB.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTest.TestClockConfig.class)
public abstract class IntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    protected static final String PASSWORD = "Aloute123";

    @Autowired protected MockMvc mvc;
    @Autowired protected UserRepository users;
    @Autowired protected PasswordEncoder encoder;
    @Autowired protected JwtService jwt;
    @Autowired protected MutableClock clock;

    @BeforeEach
    void resetClock() {
        clock.reset();
    }

    /** Tạo user có sẵn trong DB với các vai trò cho trước (luôn kèm USER). */
    protected User createUser(Role... roles) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User user = new User();
        user.setEmail("u" + suffix + "@test.local");
        user.setUsername("u" + suffix);
        user.setPasswordHash(encoder.encode(PASSWORD));
        Set<Role> set = EnumSet.of(Role.USER);
        set.addAll(Set.of(roles));
        user.setRoles(set);
        Profile profile = new Profile();
        profile.setDisplayName("Test " + suffix);
        user.attachProfile(profile);
        return users.saveAndFlush(user);
    }

    /** Gắn JWT hợp lệ của user vào request qua header Bearer (không cần đi qua trang đăng nhập). */
    protected RequestPostProcessor asUser(User user) {
        String token = jwt.generateAccessToken(AlouteUserPrincipal.of(user));
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    /** Mỗi test dùng một IP riêng để bộ giới hạn đăng nhập không ảnh hưởng lẫn nhau. */
    protected static RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    protected static String uniqueIp() {
        int n = Math.abs(UUID.randomUUID().hashCode());
        return "10." + (n % 250) + "." + ((n / 250) % 250) + "." + ((n / 62500) % 250 + 1);
    }

    protected static Cookie cookie(org.springframework.test.web.servlet.MvcResult result, String name) {
        return result.getResponse().getCookie(name);
    }
}
