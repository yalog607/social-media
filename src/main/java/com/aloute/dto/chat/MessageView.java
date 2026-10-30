package com.aloute.dto.chat;

import com.aloute.dto.post.PostView;
import com.aloute.dto.reaction.ReactionSummary;
import com.aloute.dto.sticker.StickerView;

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
        ReactionSummary reactions,
        ReplyView reply,
        StickerView sticker,
        String giphyId) {

    public MessageView(UUID id, UUID conversationId, PostView.AuthorView sender, String contentHtml,
                       AttachmentView attachment, Instant createdAt, boolean pinned, boolean isSystem,
                       ReactionSummary reactions, ReplyView reply, StickerView sticker) {
        this(id, conversationId, sender, contentHtml, attachment, createdAt, pinned, isSystem, reactions, reply, sticker, null);
    }

    public MessageView(UUID id, UUID conversationId, PostView.AuthorView sender, String contentHtml,
                       AttachmentView attachment, Instant createdAt, boolean pinned, boolean isSystem) {
        this(id, conversationId, sender, contentHtml, attachment, createdAt, pinned, isSystem, ReactionSummary.EMPTY, null, null);
    }

    public MessageView(UUID id, UUID conversationId, PostView.AuthorView sender, String contentHtml,
                       AttachmentView attachment, Instant createdAt, boolean pinned, boolean isSystem, ReactionSummary reactions) {
        this(id, conversationId, sender, contentHtml, attachment, createdAt, pinned, isSystem, reactions, null, null);
    }

    public MessageView(UUID id, UUID conversationId, PostView.AuthorView sender, String contentHtml,
                       AttachmentView attachment, Instant createdAt, boolean pinned, boolean isSystem,
                       ReactionSummary reactions, ReplyView reply) {
        this(id, conversationId, sender, contentHtml, attachment, createdAt, pinned, isSystem, reactions, reply, null);
    }

    public String plainText() {
        if (sticker != null || giphyId != null) {
            return "[Nhãn dán]";
        }
        if (contentHtml == null || contentHtml.isBlank()) {
            return attachment != null ? (attachment.originalName() != null ? attachment.originalName() : "Đính kèm") : "";
        }
        return contentHtml.replaceAll("<[^>]*>", "").trim();
    }

    public record ReplyView(UUID messageId, String senderName, String snippet) {
        public ReplyView(String senderName, String snippet) {
            this(null, senderName, snippet);
        }
    }
}
