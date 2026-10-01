package com.aloute.chat;

import java.time.Instant;
import java.util.UUID;

/**
 * Một dòng trong danh sách hội thoại. {@code title}/{@code avatarUrl} đã được tính sẵn theo góc nhìn người xem:
 * DIRECT hiện tên/avatar người kia, GROUP hiện tên nhóm (hoặc danh sách thành viên nếu nhóm chưa đặt tên).
 */
public record ConversationSummaryView(
        UUID id,
        ConversationType type,
        String title,
        String avatarUrl,
        String lastMessagePreview,
        Instant lastMessageAt,
        long unread,
        Streak streak) {
}
