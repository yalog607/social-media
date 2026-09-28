package com.aloute.chat;

import com.aloute.common.RateAction;
import com.aloute.common.RateLimiter;
import com.aloute.post.PostTextRenderer;
import com.aloute.post.PostView;
import com.aloute.user.Profile;
import com.aloute.user.User;
import com.aloute.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** Gửi và liệt kê tin nhắn trong một hội thoại. Tạo/rời hội thoại thuộc {@link ChatService}. */
@Service
public class MessageService {

    private final MessageRepository messages;
    private final UserRepository users;
    private final ChatService chats;
    private final ChatAttachmentService attachments;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public MessageService(MessageRepository messages, UserRepository users, ChatService chats,
                          ChatAttachmentService attachments, RateLimiter rateLimiter, Clock clock) {
        this.messages = messages;
        this.users = users;
        this.chats = chats;
        this.attachments = attachments;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    /**
     * @throws ConversationNotFoundException {@code senderId} không phải thành viên hội thoại
     * @throws ChatActionException           chữ và file đều rỗng, chữ quá dài, hoặc file không hợp lệ
     */
    @Transactional
    public MessageView send(UUID senderId, UUID conversationId, String content, MultipartFile attachment) {
        rateLimiter.check(RateAction.CHAT_MESSAGE, senderId);
        Conversation conversation = chats.requireMembership(senderId, conversationId);
        String text = clean(content);
        ChatAttachmentService.Stored stored = attachments.store(attachment);
        if (text.isEmpty() && stored == null) {
            throw new ChatActionException("Nhắn gì đó hoặc gửi kèm file nhé.");
        }

        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(users.getReferenceById(senderId));
        message.setContent(text.isEmpty() ? null : text);
        message.setCreatedAt(clock.instant());
        if (stored != null) {
            MessageAttachment entity = new MessageAttachment();
            entity.setKind(stored.kind());
            entity.setUrl(stored.url());
            entity.setContentType(stored.contentType());
            entity.setSizeBytes(stored.sizeBytes());
            entity.setOriginalName(stored.originalName());
            message.setAttachment(entity);
        }
        Message saved = messages.save(message);
        return toView(saved);
    }

    /** @throws ConversationNotFoundException {@code viewerId} không phải thành viên hội thoại */
    @Transactional(readOnly = true)
    public List<MessageView> history(UUID viewerId, UUID conversationId) {
        chats.requireMembership(viewerId, conversationId);
        return messages.findByConversation(conversationId).stream().map(MessageService::toView).toList();
    }

    private static MessageView toView(Message m) {
        User sender = m.getSender();
        Profile profile = sender.getProfile();
        AttachmentView attachmentView = m.getAttachment() == null ? null : new AttachmentView(
                m.getAttachment().getKind(), m.getAttachment().getUrl(),
                m.getAttachment().getContentType(), m.getAttachment().getOriginalName());
        return new MessageView(
                m.getId(),
                m.getConversation().getId(),
                new PostView.AuthorView(sender.getId(), sender.getUsername(), profile.getDisplayName(),
                        profile.getAvatarUrl(), sender.primaryRole()),
                m.getContent() == null ? "" : PostTextRenderer.toSafeHtml(m.getContent()),
                attachmentView,
                m.getCreatedAt());
    }

    /** Bỏ ký tự điều khiển, cắt khoảng trắng hai đầu, kiểm tra độ dài — cùng quy tắc với bình luận/bài viết. */
    private static String clean(String content) {
        String text = content == null ? "" : content.replaceAll("[\\p{Cntrl}&&[^\\n\\r\\t]]", "").strip();
        if (text.codePointCount(0, text.length()) > Message.MAX_CONTENT_LENGTH) {
            throw new ChatActionException("Tin nhắn tối đa " + Message.MAX_CONTENT_LENGTH + " ký tự.");
        }
        return text;
    }
}
