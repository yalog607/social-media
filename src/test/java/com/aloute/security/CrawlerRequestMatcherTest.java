package com.aloute.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class CrawlerRequestMatcherTest {

    private final CrawlerRequestMatcher matcher = new CrawlerRequestMatcher();

    private MockHttpServletRequest request(String method, String path, String agent) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, path);
        req.setServletPath(path);
        if (agent != null) {
            req.addHeader("User-Agent", agent);
        }
        return req;
    }

    @Test
    void matchesSocialBotsOnShareablePages() {
        assertThat(matcher.matches(request("GET", "/posts/abc", "facebookexternalhit/1.1"))).isTrue();
        assertThat(matcher.matches(request("GET", "/u/lan", "Mozilla/5.0 (compatible; Zalo-Link-Preview)"))).isTrue();
        assertThat(matcher.matches(request("GET", "/tags/mon-an", "Mozilla/5.0 (compatible; Discordbot/2.0)"))).isTrue();
    }

    @Test
    void rejectsNormalBrowsersMissingAgentOtherPathsAndMethods() {
        assertThat(matcher.matches(request("GET", "/posts/abc", "Mozilla/5.0 Chrome/120"))).isFalse();
        assertThat(matcher.matches(request("GET", "/posts/abc", null))).isFalse();
        assertThat(matcher.matches(request("GET", "/settings", "facebookexternalhit/1.1"))).isFalse();
        assertThat(matcher.matches(request("GET", "/admin", "Twitterbot/1.0"))).isFalse();
        assertThat(matcher.matches(request("GET", "/posts/abc/fragment", "Twitterbot/1.0"))).isFalse();
        assertThat(matcher.matches(request("POST", "/posts/abc", "facebookexternalhit/1.1"))).isFalse();
    }
}
