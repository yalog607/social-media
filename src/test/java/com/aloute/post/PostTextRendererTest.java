package com.aloute.post;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostTextRendererTest {

    @Test
    void escapesHtmlInjection() {
        String html = PostTextRenderer.toSafeHtml("<script>alert(1)</script> \"><img src=x onerror=alert(2)>");

        assertThat(html).doesNotContain("<script").doesNotContain("<img").doesNotContain("\"><");
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }

    @Test
    void turnsHashtagsIntoLinksWithNormalizedTarget() {
        String html = PostTextRenderer.toSafeHtml("Hôm nay #Học_Tập vui, #Vui!");

        assertThat(html).contains("<a class=\"hashtag\" href=\"/search?q=%23hoc_tap\">#Học_Tập</a> vui");
        assertThat(html).contains("<a class=\"hashtag\" href=\"/search?q=%23vui\">#Vui</a>!");
    }

    @Test
    void newlinesBecomeLineBreaksInAnyStyle() {
        assertThat(PostTextRenderer.toSafeHtml("a\nb\r\nc\rd")).isEqualTo("a<br>b<br>c<br>d");
    }

    @Test
    void ampersandsAndQuotesAreEscapedOnce() {
        assertThat(PostTextRenderer.toSafeHtml("Tom & Jerry's \"show\""))
                .isEqualTo("Tom &amp; Jerry&#39;s &quot;show&quot;");
    }

    @Test
    void entityLikeTextIsNotMistakenForAHashtag() {
        assertThat(PostTextRenderer.toSafeHtml("it's fine")).doesNotContain("hashtag");
    }

    @Test
    void nullAndEmptyRenderAsEmpty() {
        assertThat(PostTextRenderer.toSafeHtml(null)).isEmpty();
        assertThat(PostTextRenderer.toSafeHtml("")).isEmpty();
    }

    @Test
    void hashtagInsideInjectionAttemptStaysInert() {
        String html = PostTextRenderer.toSafeHtml("#x\" onmouseover=\"alert(1)");

        assertThat(html).doesNotContain("onmouseover=\"alert").contains("&quot;");
    }
}
