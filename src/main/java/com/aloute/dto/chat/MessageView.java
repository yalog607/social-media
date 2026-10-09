package com.aloute.dto.chat;

import com.aloute.dto.post.PostView;

import java.time.Instant;
import java.util.UUID;

public record MessageView(
        UUID id,
        UUID conversationId,
        PostView.AuthorView sender,
        String contentHtml,
        AttachmentView attachment,
        Instant createdAt,
        boolean pinned,
        boolean isSystem) {
}
