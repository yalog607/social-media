package com.aloute.util.post;

import com.aloute.exception.post.InvalidPostException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.Instant;

/**
 * Đổi giờ hẹn người dùng nhập ({@code yyyy-MM-ddTHH:mm}, giờ địa phương của trình duyệt) sang thời điểm tuyệt đối.
 * {@code tzOffset} là {@code Date.getTimezoneOffset()} của trình duyệt (phút, UTC trừ giờ địa phương, nên UTC+7
 * là -420); thiếu thì mặc định giờ Việt Nam (UTC+7).
 */
public final class ScheduleTime {

    private static final int DEFAULT_OFFSET_SECONDS = 7 * 3600;

    private ScheduleTime() {
    }

    /** @return null nếu {@code text} rỗng (không hẹn giờ) @throws InvalidPostException định dạng sai */
    public static Instant parse(String text, Integer tzOffsetMinutes) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            int offset = tzOffsetMinutes == null ? DEFAULT_OFFSET_SECONDS : -tzOffsetMinutes * 60;
            return LocalDateTime.parse(text.trim()).toInstant(ZoneOffset.ofTotalSeconds(offset));
        } catch (java.time.DateTimeException e) {
            throw new InvalidPostException("Giờ hẹn không hợp lệ.");
        }
    }
}
