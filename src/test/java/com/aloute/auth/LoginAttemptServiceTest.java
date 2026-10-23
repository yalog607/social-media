package com.aloute.auth;

import com.aloute.service.auth.LoginAttemptService;

import com.aloute.config.AlouteProperties;
import com.aloute.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private MutableClock clock;
    private LoginAttemptService attempts;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        AlouteProperties props = new AlouteProperties(null, null, null, null, null, null, null,
                new AlouteProperties.Security(3, 15));
        attempts = new LoginAttemptService(props, clock);
    }

    @Test
    void blocksAfterMaxFailuresForSameIpAndIdentifier() {
        for (int i = 0; i < 3; i++) {
            assertThat(attempts.isBlocked("1.1.1.1", "mochi")).isFalse();
            attempts.recordFailure("1.1.1.1", "mochi");
        }
        assertThat(attempts.isBlocked("1.1.1.1", "mochi")).isTrue();
        assertThat(attempts.isBlocked("1.1.1.1", "MOCHI ")).as("không phân biệt hoa thường/khoảng trắng").isTrue();
    }

    @Test
    void otherIdentifierOrIpIsNotAffected() {
        for (int i = 0; i < 3; i++) {
            attempts.recordFailure("1.1.1.1", "mochi");
        }
        assertThat(attempts.isBlocked("1.1.1.1", "another")).isFalse();
        assertThat(attempts.isBlocked("2.2.2.2", "mochi")).isFalse();
    }

    @Test
    void blocksAnIpThatSprayedManyDifferentIdentifiers() {
        for (int i = 0; i < 12; i++) {
            attempts.recordFailure("9.9.9.9", "user" + i);
        }
        assertThat(attempts.isBlocked("9.9.9.9", "brand-new-user")).isTrue();
    }

    @Test
    void unblocksAfterWindowPasses() {
        for (int i = 0; i < 3; i++) {
            attempts.recordFailure("1.1.1.1", "mochi");
        }
        clock.advance(Duration.ofMinutes(16));
        assertThat(attempts.isBlocked("1.1.1.1", "mochi")).isFalse();
    }

    @Test
    void successfulLoginResetsCounter() {
        attempts.recordFailure("1.1.1.1", "mochi");
        attempts.recordFailure("1.1.1.1", "mochi");
        attempts.reset("1.1.1.1", "mochi");
        attempts.recordFailure("1.1.1.1", "mochi");
        assertThat(attempts.isBlocked("1.1.1.1", "mochi")).isFalse();
    }
}
