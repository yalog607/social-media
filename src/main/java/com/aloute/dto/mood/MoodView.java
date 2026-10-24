package com.aloute.dto.mood;

import com.aloute.dto.post.PostView.AuthorView;

import java.time.Instant;
import java.util.UUID;

/** DTO hiển thị tâm trạng của một người dùng. */
public record MoodView(
        UUID id,
        AuthorView user,
        String emoji,
        String moodText,
        String colorTheme,
        Instant updatedAt,
        Instant expiresAt
) {
    public long hoursRemaining(Instant now) {
        if (expiresAt == null || now.isAfter(expiresAt)) {
            return 0;
        }
        return Math.max(1, java.time.Duration.between(now, expiresAt).toHours());
    }
}
