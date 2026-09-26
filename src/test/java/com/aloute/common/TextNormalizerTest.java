package com.aloute.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextNormalizerTest {

    @Test
    void stripsVietnameseDiacriticsAndLowercases() {
        assertThat(TextNormalizer.forSearch("Nguyễn Thị Đào")).isEqualTo("nguyen thi dao");
        assertThat(TextNormalizer.forSearch("ĐƯỜNG Ơi, ĂN Ở đâu?")).isEqualTo("duong oi, an o dau?");
    }

    @Test
    void handlesDecomposedInputTheSameAsPrecomposed() {
        String precomposed = "Nhân";
        String decomposed = "Nhân"; // a + dấu mũ rời
        assertThat(TextNormalizer.forSearch(decomposed)).isEqualTo(TextNormalizer.forSearch(precomposed)).isEqualTo("nhan");
    }

    @Test
    void collapsesWhitespaceAndTrims() {
        assertThat(TextNormalizer.forSearch("  Ắ  ằ \t\n x ")).isEqualTo("a a x");
    }

    @Test
    void nullAndBlankBecomeEmpty() {
        assertThat(TextNormalizer.forSearch(null)).isEmpty();
        assertThat(TextNormalizer.forSearch("   ")).isEmpty();
    }

    @Test
    void leavesAsciiAndDigitsAlone() {
        assertThat(TextNormalizer.forSearch("Hello_World 2026")).isEqualTo("hello_world 2026");
    }
}
