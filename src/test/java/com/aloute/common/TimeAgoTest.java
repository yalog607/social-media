package com.aloute.common;

import com.aloute.service.common.TimeAgo;

import com.aloute.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TimeAgoTest {

    private MutableClock clock;
    private TimeAgo timeAgo;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        timeAgo = new TimeAgo(clock);
    }

    private String ago(Duration duration) {
        return timeAgo.format(clock.instant().minus(duration));
    }

    @Test
    void underAMinuteIsJustNow() {
        assertThat(ago(Duration.ZERO)).isEqualTo("vừa xong");
        assertThat(ago(Duration.ofSeconds(59))).isEqualTo("vừa xong");
    }

    @Test
    void minutesHoursAndDays() {
        assertThat(ago(Duration.ofMinutes(1))).isEqualTo("1 phút trước");
        assertThat(ago(Duration.ofMinutes(59))).isEqualTo("59 phút trước");
        assertThat(ago(Duration.ofMinutes(60))).isEqualTo("1 giờ trước");
        assertThat(ago(Duration.ofHours(23))).isEqualTo("23 giờ trước");
        assertThat(ago(Duration.ofHours(24))).isEqualTo("1 ngày trước");
        assertThat(ago(Duration.ofDays(6))).isEqualTo("6 ngày trước");
    }

    @Test
    void olderThanAWeekShowsTheDateInVietnamTime() {
        Instant old = Instant.parse("2026-01-05T18:30:00Z"); // 01:30 ngày 6/1 theo giờ Việt Nam (UTC+7)
        clock.reset();

        assertThat(timeAgo.format(old)).isEqualTo("06/01/2026");
    }

    @Test
    void aTimestampSlightlyInTheFutureIsTreatedAsJustNow() {
        assertThat(timeAgo.format(clock.instant().plusSeconds(30))).isEqualTo("vừa xong");
    }

    @Test
    void nullGivesAnEmptyString() {
        assertThat(timeAgo.format(null)).isEmpty();
    }

    @Test
    void fullTimestampForTooltips() {
        assertThat(timeAgo.full(Instant.parse("2026-09-26T03:05:00Z"))).isEqualTo("10:05 26/09/2026");
        assertThat(timeAgo.full(null)).isEmpty();
    }
}
