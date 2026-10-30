package com.aloute.dto.creator;

import java.time.Instant;
import java.util.UUID;

/** Một series để hiển thị: tiêu đề, mô tả, Creator sở hữu và số bài (chỉ số bài được tính theo người xem ở trang xem). */
public record SeriesView(UUID id, String title, String description, UUID creatorId, String creatorUsername,
                         String creatorName, String creatorAvatarUrl, long postCount, Instant createdAt) {

    /** Một bài (rút gọn) để chọn thêm vào series hoặc liệt kê ở trang quản lý. */
    public record PostChoice(UUID id, String snippet, Instant createdAt) {
    }
}
