package com.aloute.common;

import com.aloute.util.common.LikeEscape;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LikeEscapeTest {

    @Test
    void escapesPercentUnderscoreAndBackslash() {
        assertThat(LikeEscape.escape("50%_off\\path")).isEqualTo("50\\%\\_off\\\\path");
    }

    @Test
    void leavesOrdinaryTextUnchanged() {
        assertThat(LikeEscape.escape("hello world")).isEqualTo("hello world");
    }

    @Test
    void nullBecomesEmpty() {
        assertThat(LikeEscape.escape(null)).isEmpty();
    }
}
