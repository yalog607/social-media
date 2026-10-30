package com.aloute.creator;

import com.aloute.model.creator.BadgeTier;
import com.aloute.model.user.Role;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Ngưỡng tích theo follower: chỉ Creator đủ 18 tuổi, 3 mốc GOLD/PINK/RED. */
class BadgeTierTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 31);
    private static final LocalDate ADULT_BIRTH = TODAY.minusYears(20);

    @Test
    void thresholds() {
        assertThat(BadgeTier.of(999, ADULT_BIRTH, TODAY, Role.CREATOR)).isEqualTo(BadgeTier.NONE);
        assertThat(BadgeTier.of(1_000, ADULT_BIRTH, TODAY, Role.CREATOR)).isEqualTo(BadgeTier.GOLD);
        assertThat(BadgeTier.of(99_999, ADULT_BIRTH, TODAY, Role.CREATOR)).isEqualTo(BadgeTier.GOLD);
        assertThat(BadgeTier.of(100_000, ADULT_BIRTH, TODAY, Role.CREATOR)).isEqualTo(BadgeTier.PINK);
        assertThat(BadgeTier.of(999_999, ADULT_BIRTH, TODAY, Role.CREATOR)).isEqualTo(BadgeTier.PINK);
        assertThat(BadgeTier.of(1_000_000, ADULT_BIRTH, TODAY, Role.CREATOR)).isEqualTo(BadgeTier.RED);
    }

    @Test
    void notCreatorNeverGetsBadge() {
        assertThat(BadgeTier.of(2_000_000, ADULT_BIRTH, TODAY, Role.USER)).isEqualTo(BadgeTier.NONE);
        assertThat(BadgeTier.of(2_000_000, ADULT_BIRTH, TODAY, Role.ADMIN)).isEqualTo(BadgeTier.NONE);
        assertThat(BadgeTier.of(2_000_000, ADULT_BIRTH, TODAY, Role.MANAGER)).isEqualTo(BadgeTier.NONE);
    }

    @Test
    void missingOrUnderageBirthDateMeansNoBadge() {
        assertThat(BadgeTier.of(2_000_000, null, TODAY, Role.CREATOR)).isEqualTo(BadgeTier.NONE);
        LocalDate seventeen = TODAY.minusYears(18).plusDays(1);
        assertThat(BadgeTier.of(2_000_000, seventeen, TODAY, Role.CREATOR)).isEqualTo(BadgeTier.NONE);
        LocalDate exactlyEighteen = TODAY.minusYears(18);
        assertThat(BadgeTier.of(2_000_000, exactlyEighteen, TODAY, Role.CREATOR)).isEqualTo(BadgeTier.RED);
    }
}
