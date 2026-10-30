package com.aloute.moderation;

import com.aloute.dto.moderation.ManagerPage;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ManagerPageTest {
    @Test void normalizesInvalidAndOverflowInputs() {
        assertThat(ManagerPage.of("bad", "999", 21).number()).isEqualTo(1);
        assertThat(ManagerPage.of("-1", "bad", 21).size()).isEqualTo(20);
        assertThat(ManagerPage.of("999999999999999999999999", "20", 21).number()).isEqualTo(1);
        assertThat(ManagerPage.of("999", "20", 21).number()).isEqualTo(2);
        assertThat(ManagerPage.of("2", "20", 21).offset()).isEqualTo(20);
        assertThat(ManagerPage.of("2", "100", 0).number()).isEqualTo(1);
    }
    @Test void supportsApprovedSizesAndSafeOffsets() {
        for (String size : new String[]{"20", "50", "100"}) {
            var page = ManagerPage.of("2", size, 101);
            assertThat(page.size()).isEqualTo(Integer.parseInt(size));
            assertThat(page.offset()).isEqualTo(Integer.parseInt(size));
        }
    }
}
