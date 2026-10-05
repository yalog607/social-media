package com.aloute.chat;

import com.aloute.common.RateAction;
import com.aloute.mention.MentionService;
import com.aloute.feed.Cursor;
import com.aloute.common.RateLimiter;
import com.aloute.post.PostTextRenderer;
import com.aloute.post.PostView;
import com.aloute.user.Profile;
import com.aloute.user.User;
import com.aloute.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Gửi và liệt kê tin nhắn trong một hội thoại. Tạo/rời hội thoại thuộc {@link ChatService}. */
@Service
public class MessageService {

    static final int PAGE_SIZE = 30;

    private final MessageRepository messages;
    private final UserRepository users;
    private final ChatService chats;
    private final ChatAttachmentService attachments;
    private final RateLimiter rateLimiter;
    private final Clock clock;
    private final MentionService mentions;

    public MessageService(MessageRepository messages, UserRepository users, ChatService chats,
                          ChatAttachmentService attachments, RateLimiter rateLimiter, Clock clock,
                          MentionService mentions) {
        this.mentions = mentions;
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

        if (conversation.isGroup() && !text.isEmpty()) {
            List<MemberView> members = new java.util.ArrayList<>(chats.memberViews(conversationId));
            members.sort(java.util.Comparator.comparingInt(m -> -(m.nickname() != null ? m.nickname().length() : m.displayName().length())));
            for (MemberView mv : members) {
                String name = mv.nickname() != null ? mv.nickname() : mv.displayName();
                text = text.replaceAll("(?i)@" + java.util.regex.Pattern.quote(name) + "(?![\\p{L}\\p{N}_.@&])", "@" + mv.username());
            }
        }

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
        if (conversation.isGroup() && !text.isEmpty()) {
            mentions.notifyChat(senderId, conversationId,
                    chats.memberViews(conversationId).stream().map(MemberView::id).collect(java.util.stream.Collectors.toSet()), text);
        }
        java.util.Map<String, String> usernameToNickname = getUsernameToNicknameMap(conversationId);
        return toView(saved, chats.nicknames(conversationId), usernameToNickname);
    }

    /**
     * Gửi tin nhắn hệ thống (isSystem = true).
     */
    @Transactional
    public void sendSystemMessage(UUID senderId, UUID conversationId, String text) {
        Conversation conversation = chats.requireMembership(senderId, conversationId);
        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(users.getReferenceById(senderId));
        message.setContent(text);
        message.setCreatedAt(clock.instant());
        message.setSystem(true);
        messages.save(message);
    }

    /**
     * Một trang tin nhắn theo thứ tự thời gian: trang mới nhất khi {@code cursor} rỗng, ngược lại là trang cũ hơn con trỏ.
     * Con trỏ hỏng coi như trang mới nhất.
     *
     * @throws ConversationNotFoundException {@code viewerId} không phải thành viên hội thoại
     */
    @Transactional(readOnly = true)
    public MessagePage history(UUID viewerId, UUID conversationId, String cursor) {
        chats.requireMembership(viewerId, conversationId);
        Cursor from = Cursor.decode(cursor).orElse(Cursor.START);
        List<Message> rows = new ArrayList<>(messages.findOlder(conversationId, from.createdAt(), from.id(),
                PageRequest.of(0, PAGE_SIZE + 1)));
        boolean hasMore = rows.size() > PAGE_SIZE;
        if (hasMore) {
            rows.remove(rows.size() - 1);
        }
        String next = hasMore ? new Cursor(rows.get(rows.size() - 1).getCreatedAt(), rows.get(rows.size() - 1).getId()).encode() : null;
        Collections.reverse(rows);
        Map<UUID, String> nicknames = chats.nicknames(conversationId);
        java.util.Map<String, String> usernameToNickname = getUsernameToNicknameMap(conversationId);
        return new MessagePage(rows.stream().map(m -> toView(m, nicknames, usernameToNickname)).toList(), next);
    }

    private java.util.Map<String, String> getUsernameToNicknameMap(UUID conversationId) {
        java.util.Map<String, String> usernameToNickname = new java.util.HashMap<>();
        chats.memberViews(conversationId).forEach(mv -> {
            usernameToNickname.put(mv.username().toLowerCase(java.util.Locale.ROOT), "@" + (mv.nickname() != null ? mv.nickname() : mv.displayName()));
        });
        return usernameToNickname;
    }

    /** {@code nicknames}: biệt danh trong hội thoại theo id người gửi; có thì dùng thay cho tên thật. */
    private static MessageView toView(Message m, Map<UUID, String> nicknames, java.util.Map<String, String> usernameToNickname) {
        User sender = m.getSender();
        Profile profile = sender.getProfile();
        AttachmentView attachmentView = m.getAttachment() == null ? null : new AttachmentView(
                m.getAttachment().getKind(), m.getAttachment().getUrl(),
                m.getAttachment().getContentType(), m.getAttachment().getOriginalName());
                
        return new MessageView(
                m.getId(),
                m.getConversation().getId(),
                new PostView.AuthorView(sender.getId(), sender.getUsername(),
                        nicknames.getOrDefault(sender.getId(), profile.getDisplayName()),
                        profile.getAvatarUrl(), sender.primaryRole()),
                m.getContent() == null ? "" : PostTextRenderer.toSafeHtml(m.getContent(), username -> usernameToNickname.get(username)),
                attachmentView,
                m.getCreatedAt(),
                m.isPinned(),
                m.isSystem());
    }

    @Transactional
    public void deleteMessage(UUID actorId, UUID messageId) {
        Message message = messages.findById(messageId)
                .orElseThrow(() -> new ChatActionException("Không tìm thấy tin nhắn."));
        Conversation conversation = message.getConversation();
        chats.requireMembership(actorId, conversation.getId());
        
        boolean isSender = message.getSender().getId().equals(actorId);
        if (!isSender) {
            if (!conversation.isGroup()) {
                throw new ChatActionException("Không có quyền xóa tin nhắn này.");
            }
            GroupRole role = chats.roleOf(actorId, conversation.getId());
            if (!role.canManage()) {
                throw new ChatActionException("Chỉ quản trị viên mới được xóa tin nhắn của người khác.");
            }
        }
        messages.delete(message);
    }

    @Transactional
    public void editMessage(UUID actorId, UUID messageId, String newContent) {
        Message message = messages.findById(messageId)
                .orElseThrow(() -> new ChatActionException("Không tìm thấy tin nhắn."));
        if (!message.getSender().getId().equals(actorId)) {
            throw new ChatActionException("Chỉ người gửi mới được sửa tin nhắn.");
        }
        String text = clean(newContent);
        if (text.isEmpty() && message.getAttachment() == null) {
            throw new ChatActionException("Tin nhắn không được để trống.");
        }
        message.setContent(text.isEmpty() ? null : text);
    }

    @Transactional
    public void togglePin(UUID userId, UUID messageId, boolean pin) {
        Message message = messages.findById(messageId)
                .orElseThrow(() -> new ChatActionException("Không tìm thấy tin nhắn."));
        Conversation conversation = message.getConversation();
        chats.requireMembership(userId, conversation.getId());
        if (conversation.isGroup()) {
            GroupRole role = chats.roleOf(userId, conversation.getId());
            if (!role.canManage()) {
                throw new ChatActionException("Chỉ quản trị viên mới được ghim tin nhắn.");
            }
        }
        if (pin && messages.countByConversationIdAndPinnedTrue(conversation.getId()) >= 3) {
            throw new ChatActionException("Chỉ được ghim tối đa 3 tin nhắn.");
        }
        message.setPinned(pin);
        
        User actor = users.getReferenceById(userId);
        sendSystemMessage(userId, conversation.getId(), actor.getProfile().getDisplayName() + (pin ? " đã ghim" : " đã bỏ ghim") + " một tin nhắn.");
    }

    @Transactional(readOnly = true)
    public List<MessageView> getPinnedMessages(UUID viewerId, UUID conversationId) {
        chats.requireMembership(viewerId, conversationId);
        Map<UUID, String> nicknames = chats.nicknames(conversationId);
        java.util.Map<String, String> usernameToNickname = getUsernameToNicknameMap(conversationId);
        return messages.findByConversationIdAndPinnedTrueOrderByCreatedAtDesc(conversationId)
                .stream().map(m -> toView(m, nicknames, usernameToNickname)).toList();
    }

    @Transactional(readOnly = true)
    public List<MessageView> searchMessages(UUID viewerId, UUID conversationId, String keyword) {
        chats.requireMembership(viewerId, conversationId);
        Map<UUID, String> nicknames = chats.nicknames(conversationId);
        if (keyword == null || keyword.trim().isEmpty()) return Collections.emptyList();
        java.util.Map<String, String> usernameToNickname = getUsernameToNicknameMap(conversationId);
        return messages.searchByContent(conversationId, keyword.trim())
                .stream().map(m -> toView(m, nicknames, usernameToNickname)).toList();
    }

    @Transactional(readOnly = true)
    public List<MessageView> getMediaMessages(UUID viewerId, UUID conversationId) {
        chats.requireMembership(viewerId, conversationId);
        Map<UUID, String> nicknames = chats.nicknames(conversationId);
        java.util.Map<String, String> usernameToNickname = getUsernameToNicknameMap(conversationId);
        return messages.findMediaMessages(conversationId)
                .stream().map(m -> toView(m, nicknames, usernameToNickname)).toList();
    }

    @Transactional(readOnly = true)
    public List<MessageView> getLinkMessages(UUID viewerId, UUID conversationId) {
        chats.requireMembership(viewerId, conversationId);
        Map<UUID, String> nicknames = chats.nicknames(conversationId);
        java.util.Map<String, String> usernameToNickname = getUsernameToNicknameMap(conversationId);
        return messages.findLinkMessages(conversationId)
                .stream().map(m -> toView(m, nicknames, usernameToNickname)).toList();
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
