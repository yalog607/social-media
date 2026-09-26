package com.aloute.feed;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CursorTest {

    @Test
    void encodesAndDecodesBackToTheSameValue() {
        Cursor original = new Cursor(Instant.parse("2026-09-26T10:15:30.123456Z"), UUID.randomUUID());

        assertThat(Cursor.decode(original.encode())).contains(original);
    }

    @Test
    void keepsMicrosecondPrecisionWhichIsWhatPostgresStores() {
        Instant micros = Instant.parse("2026-01-01T00:00:00Z").plus(123_456, ChronoUnit.MICROS);
        Cursor cursor = new Cursor(micros, UUID.randomUUID());

        Cursor decoded = Cursor.decode(cursor.encode()).orElseThrow();

        assertThat(decoded.createdAt()).isEqualTo(micros);
    }

    @Test
    void theTokenIsUrlSafe() {
        String token = new Cursor(Instant.now(), UUID.randomUUID()).encode();

        assertThat(token).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void garbageInputGivesEmptyInsteadOfAnError() {
        assertThat(Cursor.decode(null)).isEmpty();
        assertThat(Cursor.decode("")).isEmpty();
        assertThat(Cursor.decode("   ")).isEmpty();
        assertThat(Cursor.decode("not base64 !!!")).isEmpty();
        assertThat(Cursor.decode(b64("chi-mot-phan"))).isEmpty();
        assertThat(Cursor.decode(b64("abc|" + UUID.randomUUID()))).as("micros không phải số").isEmpty();
        assertThat(Cursor.decode(b64("123|khong-phai-uuid"))).isEmpty();
        assertThat(Cursor.decode(b64("-5|" + UUID.randomUUID()))).as("thời điểm âm").isEmpty();
        assertThat(Cursor.decode(b64("99999999999999999999999|" + UUID.randomUUID()))).as("tràn số").isEmpty();
        assertThat(Cursor.decode("x".repeat(500))).as("quá dài").isEmpty();
    }

    @Test
    void startCursorIsAfterEverythingSoTheFirstPageNeedsNoNullHandling() {
        Cursor start = Cursor.START;

        assertThat(start.createdAt()).isAfter(Instant.parse("3000-01-01T00:00:00Z"));
        assertThat(start.id()).isEqualTo(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"));
    }

    private static String b64(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes());
    }
}
