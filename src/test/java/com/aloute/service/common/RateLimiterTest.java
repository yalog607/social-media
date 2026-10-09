package com.aloute.service.common;

import com.aloute.exception.common.RateLimitExceededException;
import com.aloute.model.common.RateAction;
import com.aloute.service.common.RateLimiter;

import com.aloute.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {

    private MutableClock clock;
    private RateLimiter limiter;
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        limiter = new RateLimiter(clock);
    }

    @Test
    void allowsUpToTheLimitThenRejects() {
        for (int i = 0; i < RateAction.POST.max(); i++) {
            limiter.check(RateAction.POST, alice);
        }

        assertThatThrownBy(() -> limiter.check(RateAction.POST, alice))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("đăng bài");
    }

    @Test
    void allowsAgainOnceTheWindowHasPassed() {
        for (int i = 0; i < RateAction.POST.max(); i++) {
            limiter.check(RateAction.POST, alice);
        }

        clock.advance(RateAction.POST.window().plusSeconds(1));

        assertThatCode(() -> limiter.check(RateAction.POST, alice)).doesNotThrowAnyException();
    }

    @Test
    void slidesInsteadOfResettingAllAtOnce() {
        limiter.check(RateAction.POST, alice); // t=0
        clock.advance(Duration.ofMinutes(6));
        for (int i = 1; i < RateAction.POST.max(); i++) {
            limiter.check(RateAction.POST, alice); // t=6 phút, chạm giới hạn
        }
        assertThatThrownBy(() -> limiter.check(RateAction.POST, alice)).isInstanceOf(RateLimitExceededException.class);

        clock.advance(Duration.ofMinutes(5)); // t=11: lượt đầu (t=0) đã rơi khỏi cửa sổ 10 phút

        assertThatCode(() -> limiter.check(RateAction.POST, alice)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.check(RateAction.POST, alice)).isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void usersAndActionsAreIndependent() {
        for (int i = 0; i < RateAction.POST.max(); i++) {
            limiter.check(RateAction.POST, alice);
        }

        assertThatCode(() -> limiter.check(RateAction.POST, bob)).doesNotThrowAnyException();
        assertThatCode(() -> limiter.check(RateAction.COMMENT, alice)).doesNotThrowAnyException();
    }

    @Test
    void aRejectedAttemptDoesNotExtendTheLockout() {
        for (int i = 0; i < RateAction.POST.max(); i++) {
            limiter.check(RateAction.POST, alice);
        }
        for (int i = 0; i < 50; i++) {
            try {
                limiter.check(RateAction.POST, alice);
            } catch (RateLimitExceededException ignored) {
                // cố ý spam thêm
            }
        }

        clock.advance(RateAction.POST.window().plusSeconds(1));

        assertThatCode(() -> limiter.check(RateAction.POST, alice)).doesNotThrowAnyException();
    }

    @Test
    void forgetsIdleUsersSoMemoryDoesNotGrowForever() {
        RateLimiter small = new RateLimiter(clock, 3);
        for (int i = 0; i < 6; i++) {
            small.check(RateAction.REACTION, UUID.randomUUID());
        }
        assertThat(small.trackedKeys()).isEqualTo(6);

        clock.advance(RateAction.REACTION.window().plusSeconds(1));
        small.check(RateAction.REACTION, UUID.randomUUID());

        assertThat(small.trackedKeys()).isEqualTo(1);
    }

    @Test
    void limitsMatchTheSpec() {
        assertThat(RateAction.POST.max()).isEqualTo(10);
        assertThat(RateAction.POST.window()).isEqualTo(Duration.ofMinutes(10));
        assertThat(RateAction.COMMENT.max()).isEqualTo(30);
        assertThat(RateAction.COMMENT.window()).isEqualTo(Duration.ofMinutes(5));
        assertThat(RateAction.REACTION.max()).isEqualTo(120);
        assertThat(RateAction.REACTION.window()).isEqualTo(Duration.ofMinutes(1));
    }
}
