package com.aloute.notification;

import com.aloute.post.PostView;

import java.time.Instant;
import java.util.UUID;

/** Một dòng thông báo để hiển thị: {@code text} đã ghép sẵn tên người gây ra hành động bằng tiếng Việt. */
public record NotificationView(
        UUID id,
        PostView.AuthorView actor,
        String text,
        UUID postId,
        Instant createdAt,
        boolean read) {
}
