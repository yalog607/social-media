package com.aloute.post;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class HashtagsTest {

    @Test
    void extractsNormalizedDistinctTagsInOrder() {
        assertThat(Hashtags.extract("hi #Nhân #nhan #Học_Tập #a-b x#no"))
                .containsExactly("nhan", "hoc_tap", "a");
    }

    @Test
    void acceptsDigitsOnlyTagsButNotALoneHash() {
        assertThat(Hashtags.extract("# và #123 và ## nữa")).containsExactly("123");
    }

    @Test
    void ignoresTagsGluedToLettersAndHtmlEntities() {
        assertThat(Hashtags.extract("email@x.com#abc abc#def &#39;")).isEmpty();
    }

    @Test
    void dropsTagsLongerThanTheLimitInsteadOfTruncatingThem() {
        assertThat(Hashtags.extract("#" + "a".repeat(51))).isEmpty();
        assertThat(Hashtags.extract("#" + "a".repeat(50))).hasSize(1);
    }

    @Test
    void keepsAtMostTenTags() {
        String text = IntStream.rangeClosed(1, 15).mapToObj(i -> "#tag" + i).reduce("", (a, b) -> a + " " + b);
        assertThat(Hashtags.extract(text)).hasSize(Hashtags.MAX_PER_POST).startsWith("tag1", "tag2");
    }

    @Test
    void nullAndEmptyGiveNoTags() {
        assertThat(Hashtags.extract(null)).isEmpty();
        assertThat(Hashtags.extract("")).isEmpty();
    }
}
