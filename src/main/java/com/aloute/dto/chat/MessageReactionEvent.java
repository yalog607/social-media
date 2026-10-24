package com.aloute.dto.chat;

import java.util.Map;
import java.util.UUID;

/**
 * Payload sự kiện phát sóng qua WebSocket STOMP (/topic/conversations/{id})
 * khi có người thả/đổi/hủy cảm xúc tin nhắn.
 */
public record MessageReactionEvent(
        String event,
        UUID messageId,
        UUID conversationId,
        UUID actorId,
        String actorReaction,
        Map<String, Long> counts,
        long total) {
}
