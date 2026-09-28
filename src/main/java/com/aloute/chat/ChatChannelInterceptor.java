package com.aloute.chat;

import com.aloute.security.AlouteUserPrincipal;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chặn SUBSCRIBE vào {@code /topic/conversations/{id}} nếu người gọi không phải thành viên hội thoại đó —
 * phòng trường hợp ai đó tự nối WebSocket và đoán id, vì bản thân đường dẫn topic không có xác thực riêng.
 */
@Component
public class ChatChannelInterceptor implements ChannelInterceptor {

    private static final Pattern TOPIC = Pattern.compile("^/topic/conversations/([0-9a-fA-F-]{36})$");

    private final ChatService chats;

    public ChatChannelInterceptor(ChatService chats) {
        this.chats = chats;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }
        Matcher matcher = TOPIC.matcher(String.valueOf(accessor.getDestination()));
        Principal user = accessor.getUser();
        if (!matcher.matches() || !(user instanceof AlouteUserPrincipal principal)) {
            return null; // đích không đúng dạng hoặc chưa xác thực: âm thầm bỏ qua
        }
        UUID conversationId = UUID.fromString(matcher.group(1));
        return chats.isMember(principal.id(), conversationId) ? message : null;
    }
}
