package com.aloute.dto.chat;

import com.aloute.dto.post.PostView;
import com.aloute.dto.reaction.ReactionSummary;

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
        boolean isSystem,
        ReactionSummary reactions) {

    public MessageView(UUID id, UUID conversationId, PostView.AuthorView sender, String contentHtml,
                       AttachmentView attachment, Instant createdAt, boolean pinned, boolean isSystem) {
        this(id, conversationId, sender, contentHtml, attachment, createdAt, pinned, isSystem, ReactionSummary.EMPTY);
    }
}
