package com.aloute.notification;

import com.aloute.post.PostView;

import java.time.Instant;
import java.util.UUID;

/**
 * Một dòng thông báo để hiển thị: {@code text} đã ghép sẵn tên người gây ra hành động bằng tiếng Việt;
 * {@code detail} là nội dung kèm theo (chỉ có với thông báo nhắn hàng loạt của Creator).
 */
public record NotificationView(
        UUID id,
        PostView.AuthorView actor,
        String text,
        UUID postId,
        String detail,
        Instant createdAt,
        boolean read) {
}
