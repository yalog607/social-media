package com.aloute.dto.notification;

import com.aloute.model.notification.NotificationType;

import com.aloute.dto.post.PostView;

import java.time.Instant;
import java.util.UUID;

/**
 * Một dòng thông báo để hiển thị: {@code text} đã ghép sẵn tên người gây ra hành động bằng tiếng Việt;
 * {@code detail} là nội dung kèm theo (chỉ có với thông báo nhắn hàng loạt của Creator).
 */
public record NotificationView(
        UUID id,
        PostView.AuthorView actor,
        NotificationType type,
        String text,
        UUID postId,
        String detail,
        UUID conversationId,
        UUID referenceId,
        Instant createdAt,
        boolean read) {
}
