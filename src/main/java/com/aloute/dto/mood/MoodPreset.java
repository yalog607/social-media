package com.aloute.dto.mood;

/** Mẫu tâm trạng định sẵn cho phép người dùng chọn 1 chạm. */
public record MoodPreset(
        String emoji,
        String moodText,
        String colorTheme
) {
}
