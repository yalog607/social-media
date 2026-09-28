package com.aloute.chat;

import com.aloute.post.PostView;

import java.time.Instant;
import java.util.UUID;

public record MessageView(
        UUID id,
        UUID conversationId,
        PostView.AuthorView sender,
        String contentHtml,
        AttachmentView attachment,
        Instant createdAt) {
}
