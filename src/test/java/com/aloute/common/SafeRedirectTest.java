package com.aloute.common;

import com.aloute.util.common.SafeRedirect;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SafeRedirectTest {

    @Test
    void keepsInternalPathsWithQuery() {
        assertThat(SafeRedirect.sanitize("/settings")).isEqualTo("/settings");
        assertThat(SafeRedirect.sanitize("/u/mochi?tab=posts")).isEqualTo("/u/mochi?tab=posts");
    }

    @Test
    void fallsBackToHomeWhenEmpty() {
        assertThat(SafeRedirect.sanitize(null)).isEqualTo("/");
        assertThat(SafeRedirect.sanitize("  ")).isEqualTo("/");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://evil.example/phish",
            "//evil.example",
            "/\\evil.example",
            "javascript:alert(1)",
            "evil.example",
            "/ok\r\nSet-Cookie: x=1",
            "/a\\b",
    })
    void rejectsExternalOrMalformedTargets(String value) {
        assertThat(SafeRedirect.sanitize(value)).isEqualTo("/");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/login", "/login?next=/x", "/register", "/auth/refresh?next=/"})
    void avoidsRedirectLoopsBackToAuthPages(String value) {
        assertThat(SafeRedirect.sanitize(value)).isEqualTo("/");
    }
}
