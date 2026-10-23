package com.aloute.service.common;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Thời gian tương đối bằng tiếng Việt cho giao diện ("vừa xong", "5 phút trước", "06/01/2026").
 * Dùng trong template: {@code ${@timeAgo.format(post.createdAt)}}.
 */
@Component("timeAgo")
public class TimeAgo {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(VIETNAM);
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(VIETNAM);

    private final Clock clock;

    public TimeAgo(Clock clock) {
        this.clock = clock;
    }

    public String format(Instant instant) {
        if (instant == null) {
            return "";
        }
        Duration age = Duration.between(instant, clock.instant());
        if (age.toMinutes() < 1) {
            return "vừa xong"; // gồm cả mốc nằm hơi trong tương lai do lệch đồng hồ
        }
        if (age.toHours() < 1) {
            return age.toMinutes() + " phút trước";
        }
        if (age.toDays() < 1) {
            return age.toHours() + " giờ trước";
        }
        if (age.toDays() < 7) {
            return age.toDays() + " ngày trước";
        }
        return DATE.format(instant);
    }

    /** Ngày giờ đầy đủ theo giờ Việt Nam, dùng cho tooltip. */
    public String full(Instant instant) {
        return instant == null ? "" : FULL.format(instant);
    }
}
