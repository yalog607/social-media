package com.aloute.chat;

import com.aloute.post.PostView;
import com.aloute.social.BlockService;
import com.aloute.social.FriendService;
import com.aloute.user.MessagePermission;
import com.aloute.user.User;
import com.aloute.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tạo và quản lý hội thoại (1-1 và nhóm): ai được bắt đầu nhắn cho ai, thêm/rời nhóm, đánh dấu đã đọc.
 * Gửi/đọc tin nhắn thuộc {@link MessageService}.
 */
@Service
public class ChatService {

    public static final int MAX_NICKNAME = 40;

    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final MessageRepository messages;
    private final UserRepository users;
    private final FriendService friends;
    private final BlockService blocks;
    private final Clock clock;
    private final ChatAttachmentService attachments;
    private final StreakService streaks;
    private final ConversationRequestRepository conversationRequests;
    private final com.aloute.notification.NotificationRepository notifications;

    public ChatService(ConversationRepository conversations, ConversationMemberRepository members,
                       MessageRepository messages, UserRepository users, FriendService friends,
                       BlockService blocks, Clock clock, ChatAttachmentService attachments,
                       StreakService streaks, ConversationRequestRepository conversationRequests,
                       com.aloute.notification.NotificationRepository notifications) {
        this.attachments = attachments;
        this.streaks = streaks;
        this.conversations = conversations;
        this.members = members;
        this.messages = messages;
        this.users = users;
        this.friends = friends;
        this.blocks = blocks;
        this.clock = clock;
        this.conversationRequests = conversationRequests;
        this.notifications = notifications;
    }

    /** So sánh theo chuỗi hex, giống {@code FriendService#pair}, để chuẩn hóa cặp id không phụ thuộc thứ tự gọi. */
    static String directKey(UUID a, UUID b) {
        return a.toString().compareTo(b.toString()) < 0 ? a + "_" + b : b + "_" + a;
    }

    /**
     * Trả về hội thoại 1-1 đã có giữa hai người, hoặc tạo mới nếu chưa có. Quyền riêng tư
     * ({@code messagePermission}) chỉ được kiểm tra khi TẠO MỚI, không áp dụng lại cho hội thoại đã tồn tại.
     *
     * @throws ChatActionException tự nhắn cho chính mình, đã chặn nhau, hoặc người kia không nhận tin từ bạn
     */
    @Transactional
    public Conversation startDirect(UUID actorId, UUID targetId) {
        if (actorId.equals(targetId)) {
            throw new ChatActionException("Không thể tự nhắn tin cho chính mình.");
        }
        String key = directKey(actorId, targetId);
        return conversations.findByDirectKey(key).orElseGet(() -> createDirect(actorId, targetId, key));
    }

    private Conversation createDirect(UUID actorId, UUID targetId, String key) {
        if (blocks.isBlockedEitherWay(actorId, targetId)) {
            throw new ChatActionException("Không thể nhắn tin cho người này.");
        }
        User target = users.findById(targetId).orElseThrow(() -> new ChatActionException("Không tìm thấy người này."));
        MessagePermission permission = target.getProfile().getMessagePermission();
        boolean allowed = switch (permission) {
            case EVERYONE -> true;
            case FRIENDS -> friends.areFriends(actorId, targetId);
            case NOBODY -> false;
        };
        if (!allowed) {
            throw new ChatActionException("Người này chỉ nhận tin nhắn từ bạn bè hoặc không nhận tin nhắn mới.");
        }

        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.DIRECT);
        conversation.setDirectKey(key);
        conversation.setCreatedBy(users.getReferenceById(actorId));
        conversation.setCreatedAt(clock.instant());
        conversations.save(conversation);
        addMemberRow(conversation, actorId, GroupRole.MEMBER);
        addMemberRow(conversation, targetId, GroupRole.MEMBER);
        return conversation;
    }

    /**
     * @param memberIds bạn bè của {@code actorId} sẽ tham gia nhóm cùng actor (actorId tự thêm vào, không cần liệt kê)
     * @throws ChatActionException tiêu đề rỗng, danh sách thành viên rỗng, hoặc có người không phải bạn bè/đã chặn nhau
     */
    @Transactional
    public Conversation createGroup(UUID actorId, String title, List<UUID> memberIds) {
        String cleanTitle = title == null ? "" : title.strip();
        if (cleanTitle.isEmpty()) {
            throw new ChatActionException("Nhóm cần có tên.");
        }
        if (cleanTitle.codePointCount(0, cleanTitle.length()) > Conversation.MAX_TITLE_LENGTH) {
            throw new ChatActionException("Tên nhóm tối đa " + Conversation.MAX_TITLE_LENGTH + " ký tự.");
        }
        Set<UUID> others = new LinkedHashSet<>(memberIds == null ? List.of() : memberIds);
        others.remove(actorId);
        if (others.isEmpty()) {
            throw new ChatActionException("Chọn ít nhất một người bạn để tạo nhóm.");
        }
        for (UUID otherId : others) {
            if (!friends.areFriends(actorId, otherId) || blocks.isBlockedEitherWay(actorId, otherId)) {
                throw new ChatActionException("Chỉ có thể thêm bạn bè vào nhóm.");
            }
        }

        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.GROUP);
        conversation.setTitle(cleanTitle);
        conversation.setCreatedBy(users.getReferenceById(actorId));
        conversation.setCreatedAt(clock.instant());
        conversations.save(conversation);
        addMemberRow(conversation, actorId, GroupRole.OWNER);
        for (UUID otherId : others) {
            addMemberRow(conversation, otherId, GroupRole.MEMBER);
        }
        return conversation;
    }

    /** @throws ChatActionException {@code newMemberId} đã trong nhóm, hoặc không phải bạn bè/đã chặn với {@code actorId} */
    @Transactional
    public void addMember(UUID actorId, UUID conversationId, UUID newMemberId) {
        Conversation conversation = requireGroupMembership(actorId, conversationId);
        if (members.existsByConversationIdAndUserId(conversationId, newMemberId)) {
            throw new ChatActionException("Người này đã ở trong nhóm rồi.");
        }
        if (!friends.areFriends(actorId, newMemberId) || blocks.isBlockedEitherWay(actorId, newMemberId)) {
            throw new ChatActionException("Chỉ có thể thêm bạn bè vào nhóm.");
        }

        User newMember = users.findById(newMemberId).orElseThrow();
        User actor = users.findById(actorId).orElseThrow();

        if (conversation.isApprovalRequired() && !requireRole(actorId, conversationId).canManage()) {
            if (conversationRequests.existsByConversationIdAndUserId(conversationId, newMemberId)) {
                throw new ChatActionException("Đã có yêu cầu thêm người này vào nhóm đang chờ duyệt.");
            }
            conversationRequests.save(new ConversationRequest(conversation, newMember, actor, clock.instant()));
            systemMessage(conversation, actorId, actor.getProfile().getDisplayName() + " đã yêu cầu thêm " + newMember.getProfile().getDisplayName() + " vào nhóm. Chờ quản trị viên duyệt.");
        } else {
            addMemberRow(conversation, newMemberId, GroupRole.MEMBER);
            conversationRequests.deleteByConversationIdAndUserId(conversationId, newMemberId);
            systemMessage(conversation, actorId, actor.getProfile().getDisplayName() + " đã thêm " + newMember.getProfile().getDisplayName() + " vào nhóm.");
        }
    }
    
    @Transactional(readOnly = true)
    public List<ConversationRequest> getPendingRequests(UUID actorId, UUID conversationId) {
        requireManager(actorId, conversationId, "Chỉ quản trị viên mới xem được danh sách chờ duyệt.");
        return conversationRequests.findByConversationIdWithUsers(conversationId);
    }

    @Transactional
    public void approveRequest(UUID actorId, UUID conversationId, UUID targetUserId) {
        Conversation conversation = requireGroupMembership(actorId, conversationId);
        requireManager(actorId, conversationId, "Chỉ quản trị viên mới có thể duyệt thành viên.");
        if (members.existsByConversationIdAndUserId(conversationId, targetUserId)) {
            conversationRequests.deleteByConversationIdAndUserId(conversationId, targetUserId);
            return;
        }
        ConversationRequest req = conversationRequests.findById(new ConversationRequest.ConversationRequestId(conversationId, targetUserId)).orElseThrow(() -> new ChatActionException("Yêu cầu không tồn tại hoặc đã bị hủy."));
        addMemberRow(conversation, targetUserId, GroupRole.MEMBER);
        conversationRequests.delete(req);
        
        User target = users.findById(targetUserId).orElseThrow();
        User actor = users.findById(actorId).orElseThrow();
        systemMessage(conversation, actorId, actor.getProfile().getDisplayName() + " đã phê duyệt cho " + target.getProfile().getDisplayName() + " tham gia nhóm.");
    }
    
    @Transactional
    public void rejectRequest(UUID actorId, UUID conversationId, UUID targetUserId) {
        requireManager(actorId, conversationId, "Chỉ quản trị viên mới có thể từ chối thành viên.");
        conversationRequests.deleteByConversationIdAndUserId(conversationId, targetUserId);
    }

    /** @throws ChatActionException hội thoại là DIRECT (không thể rời, chỉ có thể chặn/xóa quan hệ) */
    @Transactional
    public void leave(UUID actorId, UUID conversationId, UUID nextOwnerId) {
        Conversation conversation = conversations.findGroupById(conversationId)
                .orElseThrow(() -> new ChatActionException("Chỉ có thể rời nhóm, không thể rời hội thoại 1-1."));
        if (!members.existsByConversationIdAndUserId(conversation.getId(), actorId)) {
            throw new ConversationNotFoundException();
        }
        ConversationMember leaving = members.findByConversationIdAndUserId(conversation.getId(), actorId).orElseThrow();
        boolean wasOwner = leaving.getRole() == GroupRole.OWNER;

        List<ConversationMember> rest = members.findMembers(conversation.getId());

        if (wasOwner && rest.size() > 1) {
            if (nextOwnerId == null) {
                throw new ChatActionException("Bạn phải chọn một thành viên khác làm chủ nhóm trước khi rời đi.");
            }
            if (nextOwnerId.equals(actorId)) {
                throw new ChatActionException("Bạn không thể chọn chính mình làm chủ nhóm mới.");
            }
            ConversationMember nextOwner = members.findByConversationIdAndUserId(conversationId, nextOwnerId)
                    .orElseThrow(() -> new ChatActionException("Người được chọn làm chủ nhóm mới không ở trong nhóm."));
            nextOwner.setRole(GroupRole.OWNER);

            User actorUser = users.findById(actorId).orElseThrow();
            systemMessage(conversation, actorId, actorUser.getProfile().getDisplayName() + " đã nhường quyền chủ nhóm cho " + nextOwner.getUser().getProfile().getDisplayName() + " trước khi rời đi.");
        }

        members.deleteByConversationIdAndUserId(conversation.getId(), actorId);
        
        User actor = users.findById(actorId).orElseThrow();
        systemMessage(conversation, actorId, actor.getProfile().getDisplayName() + " đã rời khỏi nhóm.");
    }

    @Transactional
    public void disband(UUID actorId, UUID conversationId) {
        Conversation conversation = requireGroupMembership(actorId, conversationId);
        requireManager(actorId, conversationId, "Chỉ quản trị viên hoặc chủ nhóm mới có thể giải tán nhóm.");
        messages.deleteByConversationId(conversationId);
        members.deleteByConversationId(conversationId);
        conversationRequests.deleteByConversationId(conversationId);
        notifications.deleteByConversationId(conversationId);
        conversations.delete(conversation);
    }

    @Transactional
    public void toggleApproval(UUID actorId, UUID conversationId, boolean req) {
        Conversation conversation = requireGroupMembership(actorId, conversationId);
        requireManager(actorId, conversationId, "Chỉ quản trị viên hoặc chủ nhóm mới có thể thiết lập quyền duyệt.");
        conversation.setApprovalRequired(req);
        
        User actor = users.findById(actorId).orElseThrow();
        systemMessage(conversation, actorId, actor.getProfile().getDisplayName() + (req ? " đã bật" : " đã tắt") + " tính năng duyệt thành viên.");
    }

    // ---------- Quản lý nhóm: tên, ảnh, vai trò, thành viên, biệt danh ----------

    /** @throws ChatActionException không đủ quyền hoặc tên không hợp lệ */
    @Transactional
    public void rename(UUID actorId, UUID conversationId, String title) {
        Conversation conversation = requireGroupMembership(actorId, conversationId);
        requireManager(actorId, conversationId, "Chỉ chủ nhóm và quản trị viên mới đổi được tên nhóm.");
        String clean = title == null ? "" : title.replaceAll("[\\p{Cntrl}]", " ").strip().replaceAll("\\s+", " ");
        if (clean.isEmpty()) {
            throw new ChatActionException("Nhóm cần có tên.");
        }
        if (clean.codePointCount(0, clean.length()) > Conversation.MAX_TITLE_LENGTH) {
            throw new ChatActionException("Tên nhóm tối đa " + Conversation.MAX_TITLE_LENGTH + " ký tự.");
        }
        conversation.setTitle(clean);
        
        User actor = users.findById(actorId).orElseThrow();
        systemMessage(conversation, actorId, actor.getProfile().getDisplayName() + " đã đổi tên nhóm thành \"" + clean + "\".");
    }

    /** @throws ChatActionException không đủ quyền, file rỗng hoặc không phải ảnh */
    @Transactional
    public void setAvatar(UUID actorId, UUID conversationId, org.springframework.web.multipart.MultipartFile file) {
        Conversation conversation = requireGroupMembership(actorId, conversationId);
        requireManager(actorId, conversationId, "Chỉ chủ nhóm và quản trị viên mới đổi được ảnh nhóm.");
        ChatAttachmentService.Stored stored = attachments.store(file);
        if (stored == null) {
            throw new ChatActionException("Hãy chọn một ảnh.");
        }
        if (stored.kind() != AttachmentKind.IMAGE) {
            throw new ChatActionException("Ảnh nhóm phải là file ảnh (JPG, PNG, GIF, WEBP).");
        }
        conversation.setAvatarUrl(stored.url());
        
        User actor = users.findById(actorId).orElseThrow();
        systemMessage(conversation, actorId, actor.getProfile().getDisplayName() + " đã thay đổi ảnh đại diện của nhóm.");
    }

    /** Chủ nhóm đặt người khác làm quản trị viên hoặc đưa về thành viên. */
    @Transactional
    public void setRole(UUID actorId, UUID conversationId, UUID targetId, GroupRole role) {
        Conversation conversation = requireGroupMembership(actorId, conversationId);
        if (role == GroupRole.OWNER) {
            throw new ChatActionException("Dùng chức năng chuyển quyền chủ nhóm.");
        }
        if (requireRole(actorId, conversationId) != GroupRole.OWNER) {
            throw new ChatActionException("Chỉ chủ nhóm mới phân quyền được.");
        }
        ConversationMember target = requireTarget(conversationId, targetId);
        if (target.getRole() == GroupRole.OWNER) {
            throw new ChatActionException("Không thể đổi vai trò của chủ nhóm.");
        }
        target.setRole(role);
        
        User actorUser = users.findById(actorId).orElseThrow();
        User targetUser = users.findById(targetId).orElseThrow();
        systemMessage(conversation, actorId, actorUser.getProfile().getDisplayName() + " đã đặt " + targetUser.getProfile().getDisplayName() + " làm " + role.label().toLowerCase() + ".");
    }

    /** Chủ nhóm nhường quyền cho một thành viên; chủ cũ trở thành quản trị viên. */
    @Transactional
    public void transferOwnership(UUID actorId, UUID conversationId, UUID targetId) {
        Conversation conversation = requireGroupMembership(actorId, conversationId);
        ConversationMember actor = members.findByConversationIdAndUserId(conversationId, actorId).orElseThrow();
        if (actor.getRole() != GroupRole.OWNER) {
            throw new ChatActionException("Chỉ chủ nhóm mới nhường quyền được.");
        }
        if (targetId.equals(actorId)) {
            throw new ChatActionException("Bạn đã là chủ nhóm rồi.");
        }
        ConversationMember target = requireTarget(conversationId, targetId);
        target.setRole(GroupRole.OWNER);
        actor.setRole(GroupRole.ADMIN);
        
        User actorUser = users.findById(actorId).orElseThrow();
        User targetUser = users.findById(targetId).orElseThrow();
        systemMessage(conversation, actorId, actorUser.getProfile().getDisplayName() + " đã nhường quyền chủ nhóm cho " + targetUser.getProfile().getDisplayName() + ".");
    }

    /** Chủ nhóm xóa được mọi người khác; quản trị viên chỉ xóa được thành viên thường. Tự rời nhóm dùng {@link #leave}. */
    @Transactional
    public void removeMember(UUID actorId, UUID conversationId, UUID targetId) {
        Conversation conversation = requireGroupMembership(actorId, conversationId);
        GroupRole actorRole = requireRole(actorId, conversationId);
        if (targetId.equals(actorId)) {
            throw new ChatActionException("Muốn rời nhóm hãy dùng nút \"Rời nhóm\".");
        }
        ConversationMember target = requireTarget(conversationId, targetId);
        if (!actorRole.canManage() || !actorRole.outranks(target.getRole())) {
            throw new ChatActionException("Bạn không có quyền xóa người này khỏi nhóm.");
        }
        members.delete(target);
        
        User actorUser = users.findById(actorId).orElseThrow();
        User targetUser = users.findById(targetId).orElseThrow();
        systemMessage(conversation, actorId, actorUser.getProfile().getDisplayName() + " đã xóa " + targetUser.getProfile().getDisplayName() + " khỏi nhóm.");
    }

    /**
     * Đặt biệt danh (rỗng = xóa). Hội thoại 1-1: ai cũng đặt được cho cả hai người. Nhóm: ai cũng đặt được cho mình;
     * chủ nhóm đặt được cho mọi người, quản trị viên đặt được cho thành viên thường.
     */
    @Transactional
    public void setNickname(UUID actorId, UUID conversationId, UUID targetId, String nickname) {
        Conversation conversation = requireMembership(actorId, conversationId);
        ConversationMember target = requireTarget(conversationId, targetId);
        if (conversation.isGroup() && !targetId.equals(actorId)) {
            GroupRole actorRole = requireRole(actorId, conversationId);
            boolean canChange = conversation.isAllowAnyoneChangeNickname() || actorRole.canManage();
            if (!canChange) {
                throw new ChatActionException("Chỉ quản trị viên mới được phép đặt biệt danh.");
            }
            if (!conversation.isAllowAnyoneChangeNickname() && !actorRole.outranks(target.getRole())) {
                throw new ChatActionException("Bạn không có quyền đặt biệt danh cho người này.");
            }
        }
        String clean = nickname == null ? "" : nickname.replaceAll("[\\p{Cntrl}]", " ").strip().replaceAll("\\s+", " ");
        if (clean.codePointCount(0, clean.length()) > MAX_NICKNAME) {
            throw new ChatActionException("Biệt danh tối đa " + MAX_NICKNAME + " ký tự.");
        }
        target.setNickname(clean.isEmpty() ? null : clean);
        
        User actorUser = users.findById(actorId).orElseThrow();
        User targetUser = users.findById(targetId).orElseThrow();
        if (clean.isEmpty()) {
            systemMessage(conversation, actorId, actorUser.getProfile().getDisplayName() + " đã xóa biệt danh của " + targetUser.getProfile().getDisplayName() + ".");
        } else {
            systemMessage(conversation, actorId, actorUser.getProfile().getDisplayName() + " đã đặt biệt danh cho " + targetUser.getProfile().getDisplayName() + " là " + clean + ".");
        }
    }

    @Transactional
    public void setAllowAnyoneChangeNickname(UUID actorId, UUID conversationId, boolean allow) {
        Conversation conversation = requireGroupMembership(actorId, conversationId);
        if (!conversation.isGroup()) throw new ChatActionException("Chỉ nhóm mới có cài đặt này.");
        if (conversation.isAllowAnyoneChangeNickname() == allow) return;
        requireManager(actorId, conversationId, "Chỉ quản trị viên hoặc chủ nhóm mới có thể đổi cài đặt này.");
        conversation.setAllowAnyoneChangeNickname(allow);
        User actor = users.findById(actorId).orElseThrow();
        String status = allow ? "bật" : "tắt";
        systemMessage(conversation, actorId, actor.getProfile().getDisplayName() + " đã " + status + " tính năng cho phép ai cũng có thể thay đổi biệt danh.");
    }

    /** Thành viên của hội thoại kèm vai trò và biệt danh, theo thứ tự vào nhóm. */
    @Transactional(readOnly = true)
    public List<MemberView> memberViews(UUID conversationId) {
        return members.findMembers(conversationId).stream()
                .map(m -> new MemberView(m.getUser().getId(), m.getUser().getUsername(), m.getUser().getProfile().getDisplayName(),
                        m.getNickname(), m.getUser().getProfile().getAvatarUrl(), m.getRole()))
                .toList();
    }

    /** Biệt danh trong hội thoại, theo id người dùng (chỉ gồm người đã có biệt danh). */
    @Transactional(readOnly = true)
    public Map<UUID, String> nicknames(UUID conversationId) {
        Map<UUID, String> result = new HashMap<>();
        for (ConversationMember m : members.findMembers(conversationId)) {
            if (m.getNickname() != null) {
                result.put(m.getUser().getId(), m.getNickname());
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public GroupRole roleOf(UUID userId, UUID conversationId) {
        return members.findByConversationIdAndUserId(conversationId, userId).map(ConversationMember::getRole).orElse(GroupRole.MEMBER);
    }

    private GroupRole requireRole(UUID userId, UUID conversationId) {
        return members.findByConversationIdAndUserId(conversationId, userId)
                .map(ConversationMember::getRole).orElseThrow(ConversationNotFoundException::new);
    }

    private void requireManager(UUID userId, UUID conversationId, String message) {
        if (!requireRole(userId, conversationId).canManage()) {
            throw new ChatActionException(message);
        }
    }

    private ConversationMember requireTarget(UUID conversationId, UUID targetId) {
        return members.findByConversationIdAndUserId(conversationId, targetId)
                .orElseThrow(() -> new ChatActionException("Người này không ở trong hội thoại."));
    }

    @Transactional
    public void markRead(UUID userId, UUID conversationId) {
        members.findByConversationIdAndUserId(conversationId, userId).ifPresent(m -> m.setLastReadAt(clock.instant()));
    }

    public boolean isMember(UUID userId, UUID conversationId) {
        return members.existsByConversationIdAndUserId(conversationId, userId);
    }

    /** @throws ConversationNotFoundException hội thoại không tồn tại hoặc {@code userId} không phải thành viên */
    @Transactional(readOnly = true)
    public Conversation requireMembership(UUID userId, UUID conversationId) {
        if (!isMember(userId, conversationId)) {
            throw new ConversationNotFoundException();
        }
        return conversations.findById(conversationId).orElseThrow(ConversationNotFoundException::new);
    }
    
    @Transactional(readOnly = true)
    public boolean exists(UUID conversationId) {
        return conversations.existsById(conversationId);
    }

    private Conversation requireGroupMembership(UUID userId, UUID conversationId) {
        Conversation conversation = requireMembership(userId, conversationId);
        if (!conversation.isGroup()) {
            throw new ChatActionException("Hội thoại 1-1 không có thành viên để thêm/bớt.");
        }
        return conversation;
    }

    @Transactional(readOnly = true)
    public List<PostView.AuthorView> membersOf(UUID conversationId) {
        return members.findMembers(conversationId).stream().map(m -> AuthorViews.of(m.getUser())).toList();
    }

    public long totalUnread(UUID userId) {
        return members.totalUnread(userId);
    }

    /** Danh sách hội thoại của {@code userId}, mới nhất trước, kèm tên/avatar/xem trước đã dựng sẵn theo góc nhìn của họ. */
    @Transactional(readOnly = true)
    public List<ConversationSummaryView> listFor(UUID userId) {
        List<ConversationMember> mine = members.findWithConversationForUser(userId);
        if (mine.isEmpty()) {
            return List.of();
        }
        List<UUID> conversationIds = mine.stream().map(m -> m.getConversation().getId()).toList();
        Map<UUID, Message> latestByConversation = new HashMap<>();
        for (Message m : messages.findLatestForConversations(conversationIds)) {
            latestByConversation.put(m.getConversation().getId(), m);
        }
        Map<UUID, Long> unreadByConversation = new HashMap<>();
        for (ConversationMemberRepository.UnreadRow row : members.unreadCounts(userId)) {
            unreadByConversation.put(row.getConversationId(), row.getUnread());
        }

        Map<UUID, Streak> streakById = streaks.forConversations(mine.stream()
                .map(ConversationMember::getConversation).map(Conversation::getId).toList());
        List<ConversationSummaryView> views = new ArrayList<>();
        for (ConversationMember membership : mine) {
            Conversation conversation = membership.getConversation();
            Message latest = latestByConversation.get(conversation.getId());
            views.add(new ConversationSummaryView(
                    conversation.getId(),
                    conversation.getType(),
                    displayTitle(conversation, userId),
                    displayAvatar(conversation, userId),
                    preview(latest),
                    latest != null ? latest.getCreatedAt() : conversation.getCreatedAt(),
                    unreadByConversation.getOrDefault(conversation.getId(), 0L),
                    streakById.getOrDefault(conversation.getId(), Streak.NONE)));
        }
        views.sort((a, b) -> b.lastMessageAt().compareTo(a.lastMessageAt()));
        return views;
    }

    @Transactional(readOnly = true)
    public ConversationHeader header(UUID viewerId, Conversation conversation) {
        return new ConversationHeader(displayTitle(conversation, viewerId), displayAvatar(conversation, viewerId), conversation.isGroup());
    }

    private String displayTitle(Conversation conversation, UUID viewerId) {
        if (conversation.isGroup()) {
            return conversation.getTitle();
        }
        return otherMember(conversation, viewerId)
                .map(m -> m.getNickname() != null ? m.getNickname() : m.getUser().getProfile().getDisplayName())
                .orElse("Người dùng đã rời ALOUTE");
    }

    private String displayAvatar(Conversation conversation, UUID viewerId) {
        if (conversation.isGroup()) {
            return conversation.getAvatarUrl();
        }
        return otherMember(conversation, viewerId).map(m -> m.getUser().getProfile().getAvatarUrl()).orElse(null);
    }

    private java.util.Optional<ConversationMember> otherMember(Conversation conversation, UUID viewerId) {
        return members.findMembers(conversation.getId()).stream()
                .filter(m -> !m.getUser().getId().equals(viewerId))
                .findFirst();
    }

    private static String preview(Message latest) {
        if (latest == null) {
            return "";
        }
        if (latest.getContent() != null && !latest.getContent().isBlank()) {
            return latest.getContent();
        }
        return latest.getAttachment() != null ? "Đã gửi một tệp đính kèm" : "";
    }

    private void addMemberRow(Conversation conversation, UUID userId, GroupRole role) {
        ConversationMember member = new ConversationMember();
        member.setRole(role);
        member.setConversation(conversation);
        member.setUser(users.getReferenceById(userId));
        member.setJoinedAt(clock.instant());
        members.save(member);
    }

    private void systemMessage(Conversation conversation, UUID actorId, String text) {
        Message msg = new Message();
        msg.setConversation(conversation);
        msg.setSender(users.getReferenceById(actorId));
        msg.setContent(text);
        msg.setCreatedAt(clock.instant());
        msg.setSystem(true);
        messages.save(msg);
    }
}
