package com.aloute.service.group;

import com.aloute.dto.group.GroupMemberView;
import com.aloute.dto.group.GroupReportView;
import com.aloute.dto.group.GroupRequestView;
import com.aloute.dto.group.GroupView;
import com.aloute.exception.chat.ChatActionException;
import com.aloute.model.chat.Conversation;
import com.aloute.model.chat.ConversationMember;
import com.aloute.model.chat.ConversationRequest;
import com.aloute.model.chat.ConversationType;
import com.aloute.model.chat.GroupRole;
import com.aloute.model.chat.Message;
import com.aloute.model.group.GroupPostReport;
import com.aloute.model.post.Post;
import com.aloute.model.user.User;
import com.aloute.repository.chat.ConversationMemberRepository;
import com.aloute.repository.chat.ConversationRepository;
import com.aloute.repository.chat.ConversationRequestRepository;
import com.aloute.repository.chat.MessageRepository;
import com.aloute.repository.group.GroupPostReportRepository;
import com.aloute.repository.post.PostRepository;
import com.aloute.repository.user.UserRepository;
import com.aloute.service.chat.ChatService;
import com.aloute.service.season.SeasonActivityRecorder;
import com.aloute.service.season.SeasonScoring;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GroupService {

    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final ConversationRequestRepository requests;
    private final MessageRepository messages;
    private final UserRepository users;
    private final ChatService chatService;
    private final PostRepository posts;
    private final GroupPostReportRepository groupReports;
    private final Clock clock;
    private final SeasonActivityRecorder seasonActivity;

    public GroupService(ConversationRepository conversations,
                        ConversationMemberRepository members,
                        ConversationRequestRepository requests,
                        MessageRepository messages,
                        UserRepository users,
                        ChatService chatService,
                        PostRepository posts,
                        GroupPostReportRepository groupReports,
                        Clock clock, SeasonActivityRecorder seasonActivity) {
        this.seasonActivity = seasonActivity;
        this.conversations = conversations;
        this.members = members;
        this.requests = requests;
        this.messages = messages;
        this.users = users;
        this.chatService = chatService;
        this.posts = posts;
        this.groupReports = groupReports;
        this.clock = clock;
    }

    /**
     * Danh sách các nhóm mà người dùng đang tham gia.
     */
    @Transactional(readOnly = true)
    public List<GroupView> listMyGroups(UUID userId) {
        List<ConversationMember> myMemberships = members.findWithConversationForUser(userId).stream()
                .filter(cm -> cm.getConversation().isSocialGroup())
                .toList();

        if (myMemberships.isEmpty()) {
            return List.of();
        }

        List<GroupView> views = new ArrayList<>();
        for (ConversationMember cm : myMemberships) {
            Conversation c = cm.getConversation();
            long memberCount = members.countByConversationId(c.getId());
            String ownerName = c.getCreatedBy() != null && c.getCreatedBy().getProfile() != null
                    ? c.getCreatedBy().getProfile().getDisplayName() : "Ẩn danh";

            views.add(new GroupView(
                    c.getId(),
                    c.getTitle(),
                    c.getDescription(),
                    c.getAvatarUrl(),
                    memberCount,
                    true,
                    cm.getRole(),
                    false,
                    c.isApprovalRequired(),
                    c.getCreatedAt(),
                    ownerName,
                    c.isChatEnabled(),
                    c.getRules(),
                    c.getApprovalQuestion()
            ));
        }

        views.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));
        return views;
    }

    /**
     * Danh sách khám phá các nhóm trong toàn hệ thống, kèm từ khóa tìm kiếm.
     */
    @Transactional(readOnly = true)
    public List<GroupView> listDiscoverGroups(UUID userId, String query) {
        List<Conversation> allGroups = conversations.findAllSocialGroups();
        if (allGroups.isEmpty()) {
            return List.of();
        }

        // Lấy danh sách thành viên của user hiện tại
        Map<UUID, GroupRole> userRoles = members.findWithConversationForUser(userId).stream()
                .filter(cm -> cm.getConversation().isSocialGroup())
                .collect(Collectors.toMap(cm -> cm.getConversation().getId(), ConversationMember::getRole, (r1, r2) -> r1));

        // Lấy danh sách các nhóm đang chờ duyệt của user
        Set<UUID> pendingGroupIds = requests.findByUserId(userId).stream()
                .map(r -> r.getConversation().getId())
                .collect(Collectors.toSet());

        String cleanQuery = query == null ? "" : query.trim().toLowerCase();

        List<GroupView> result = new ArrayList<>();
        for (Conversation c : allGroups) {
            boolean matches = cleanQuery.isEmpty()
                    || (c.getTitle() != null && c.getTitle().toLowerCase().contains(cleanQuery))
                    || (c.getDescription() != null && c.getDescription().toLowerCase().contains(cleanQuery));

            if (!matches) {
                continue;
            }

            long memberCount = members.countByConversationId(c.getId());
            boolean isMember = userRoles.containsKey(c.getId());
            GroupRole myRole = userRoles.get(c.getId());
            boolean isPending = pendingGroupIds.contains(c.getId());
            String ownerName = c.getCreatedBy() != null && c.getCreatedBy().getProfile() != null
                    ? c.getCreatedBy().getProfile().getDisplayName() : "Ẩn danh";

            result.add(new GroupView(
                    c.getId(),
                    c.getTitle(),
                    c.getDescription(),
                    c.getAvatarUrl(),
                    memberCount,
                    isMember,
                    myRole,
                    isPending,
                    c.isApprovalRequired(),
                    c.getCreatedAt(),
                    ownerName,
                    c.isChatEnabled(),
                    c.getRules(),
                    c.getApprovalQuestion()
            ));
        }

        return result;
    }

    /**
     * Tạo nhóm mới với mô tả và cài đặt duyệt.
     */
    @Transactional
    public Conversation createGroup(UUID actorId, String title, String description,
                                    boolean approvalRequired, List<UUID> memberIds, MultipartFile avatar) {
        String cleanTitle = title == null ? "" : title.strip();
        if (cleanTitle.isEmpty()) {
            throw new ChatActionException("Nhóm cần có tên.");
        }
        if (cleanTitle.codePointCount(0, cleanTitle.length()) > Conversation.MAX_TITLE_LENGTH) {
            throw new ChatActionException("Tên nhóm tối đa " + Conversation.MAX_TITLE_LENGTH + " ký tự.");
        }

        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.GROUP);
        conversation.setTitle(cleanTitle);
        conversation.setCreatedBy(users.getReferenceById(actorId));
        conversation.setCreatedAt(clock.instant());
        conversation.setSocialGroup(true);
        conversation.setChatEnabled(false);

        String cleanDesc = description == null ? "" : description.trim();
        if (cleanDesc.length() > 500) {
            cleanDesc = cleanDesc.substring(0, 500);
        }
        conversation.setDescription(cleanDesc.isEmpty() ? null : cleanDesc);
        conversation.setApprovalRequired(approvalRequired);
        conversations.save(conversation);

        // Thêm người tạo làm OWNER
        ConversationMember ownerMember = new ConversationMember();
        ownerMember.setConversation(conversation);
        ownerMember.setUser(users.getReferenceById(actorId));
        ownerMember.setRole(GroupRole.OWNER);
        ownerMember.setJoinedAt(clock.instant());
        members.save(ownerMember);

        // Mời bạn bè nếu có
        if (memberIds != null && !memberIds.isEmpty()) {
            for (UUID friendId : memberIds) {
                if (friendId == null || friendId.equals(actorId)) continue;
                ConversationMember cm = new ConversationMember();
                cm.setConversation(conversation);
                cm.setUser(users.getReferenceById(friendId));
                cm.setRole(GroupRole.MEMBER);
                cm.setJoinedAt(clock.instant());
                members.save(cm);
            }
        }

        if (avatar != null && !avatar.isEmpty()) {
            chatService.setAvatar(actorId, conversation.getId(), avatar);
        }

        return conversation;
    }

    /**
     * Người dùng tự xin tham gia hoặc tham gia ngay vào nhóm (kèm câu trả lời nếu có).
     */
    @Transactional
    public boolean joinGroup(UUID userId, UUID conversationId) {
        return joinGroup(userId, conversationId, null);
    }

    @Transactional
    public boolean joinGroup(UUID userId, UUID conversationId, String answer) {
        Conversation conversation = conversations.findGroupById(conversationId)
                .orElseThrow(() -> new ChatActionException("Không tìm thấy nhóm này."));

        if (members.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new ChatActionException("Bạn đã là thành viên của nhóm này rồi.");
        }

        User user = users.findById(userId)
                .orElseThrow(() -> new ChatActionException("Không tìm thấy thông tin người dùng."));

        if (conversation.isApprovalRequired()) {
            if (requests.existsByConversationIdAndUserId(conversationId, userId)) {
                throw new ChatActionException("Yêu cầu tham gia của bạn đang chờ quản trị viên phê duyệt.");
            }
            String cleanAnswer = answer == null ? null : answer.trim();
            if (cleanAnswer != null && cleanAnswer.length() > 500) {
                cleanAnswer = cleanAnswer.substring(0, 500);
            }
            requests.save(new ConversationRequest(conversation, user, user, clock.instant(), cleanAnswer));
            sendSystemMessage(conversation, userId, user.getProfile().getDisplayName() + " đã gửi yêu cầu xin gia nhập nhóm.");
            return true;
        } else {
            ConversationMember cm = new ConversationMember();
            cm.setConversation(conversation);
            cm.setUser(user);
            cm.setRole(GroupRole.MEMBER);
            cm.setJoinedAt(clock.instant());
            members.save(cm);
            requests.deleteByConversationIdAndUserId(conversationId, userId);
            sendSystemMessage(conversation, userId, user.getProfile().getDisplayName() + " đã tham gia nhóm.");
            return false;
        }
    }

    /**
     * Hủy yêu cầu xin gia nhập nhóm.
     */
    @Transactional
    public void cancelRequest(UUID userId, UUID conversationId) {
        requests.deleteByConversationIdAndUserId(conversationId, userId);
    }

    /**
     * Thông tin chi tiết một nhóm theo ID.
     */
    @Transactional(readOnly = true)
    public GroupView getGroup(UUID conversationId, UUID userId) {
        Conversation c = conversations.findGroupById(conversationId)
                .orElseThrow(() -> new ChatActionException("Không tìm thấy nhóm này."));

        long memberCount = members.countByConversationId(conversationId);
        boolean isMember = userId != null && members.existsByConversationIdAndUserId(conversationId, userId);
        GroupRole myRole = isMember ? members.findByConversationIdAndUserId(conversationId, userId)
                .map(ConversationMember::getRole).orElse(null) : null;
        boolean isPending = userId != null && requests.existsByConversationIdAndUserId(conversationId, userId);
        String ownerName = c.getCreatedBy() != null && c.getCreatedBy().getProfile() != null
                    ? c.getCreatedBy().getProfile().getDisplayName() : "Ẩn danh";

        return new GroupView(
                c.getId(),
                c.getTitle(),
                c.getDescription(),
                c.getAvatarUrl(),
                memberCount,
                isMember,
                myRole,
                isPending,
                c.isApprovalRequired(),
                c.getCreatedAt(),
                ownerName,
                c.isChatEnabled(),
                c.getRules(),
                c.getApprovalQuestion()
        );
    }

    /**
     * Cập nhật cài đặt nhóm: nội quy, yêu cầu duyệt, câu hỏi duyệt.
     */
    @Transactional
    public void updateSettings(UUID actorId, UUID groupId, String title, String description,
                               String rules, boolean approvalRequired, String approvalQuestion) {
        Conversation c = conversations.findById(groupId)
                .orElseThrow(() -> new ChatActionException("Không tìm thấy nhóm này."));
        requireRole(actorId, groupId, GroupRole.ADMIN);

        if (title != null && !title.isBlank()) {
            String cleanTitle = title.strip();
            if (cleanTitle.codePointCount(0, cleanTitle.length()) > Conversation.MAX_TITLE_LENGTH) {
                throw new ChatActionException("Tên nhóm tối đa " + Conversation.MAX_TITLE_LENGTH + " ký tự.");
            }
            c.setTitle(cleanTitle);
        }

        String cleanDesc = description == null ? null : description.trim();
        if (cleanDesc != null && cleanDesc.length() > 500) {
            cleanDesc = cleanDesc.substring(0, 500);
        }
        c.setDescription(cleanDesc);

        String cleanRules = rules == null ? null : rules.trim();
        if (cleanRules != null && cleanRules.length() > 2000) {
            cleanRules = cleanRules.substring(0, 2000);
        }
        c.setRules(cleanRules);
        c.setApprovalRequired(approvalRequired);

        String cleanQuestion = approvalQuestion == null ? null : approvalQuestion.trim();
        if (cleanQuestion != null && cleanQuestion.length() > 500) {
            cleanQuestion = cleanQuestion.substring(0, 500);
        }
        c.setApprovalQuestion(approvalRequired ? cleanQuestion : null);

        conversations.save(c);
    }

    /**
     * Danh sách thành viên trong nhóm kèm vai trò.
     */
    @Transactional(readOnly = true)
    public List<GroupMemberView> listMembers(UUID groupId) {
        return members.findMembers(groupId).stream()
                .map(m -> {
                    User u = m.getUser();
                    return new GroupMemberView(
                            u.getId(),
                            u.getUsername(),
                            u.getProfile().getDisplayName(),
                            u.getProfile().getAvatarUrl(),
                            m.getRole(),
                            m.getJoinedAt(),
                            u.getProfile().getAvatarFrame()
                    );
                })
                .sorted((a, b) -> {
                    int diff = Integer.compare(b.role().rank(), a.role().rank());
                    if (diff != 0) return diff;
                    return a.joinedAt().compareTo(b.joinedAt());
                })
                .toList();
    }

    /**
     * Phân quyền thành viên (Chủ nhóm hoặc Quản trị viên).
     */
    @Transactional
    public void changeMemberRole(UUID actorId, UUID groupId, UUID targetUserId, GroupRole newRole) {
        if (actorId.equals(targetUserId)) {
            throw new ChatActionException("Không thể tự thay đổi vai trò của chính mình.");
        }
        ConversationMember actorMembership = members.findByConversationIdAndUserId(groupId, actorId)
                .orElseThrow(() -> new ChatActionException("Bạn không phải thành viên nhóm."));
        ConversationMember targetMembership = members.findByConversationIdAndUserId(groupId, targetUserId)
                .orElseThrow(() -> new ChatActionException("Người này không thuộc nhóm."));

        if (!actorMembership.getRole().canManage()) {
            throw new ChatActionException("Bạn không có quyền quản lý thành viên.");
        }
        if (targetMembership.getRole() == GroupRole.OWNER) {
            throw new ChatActionException("Không thể thay đổi quyền của Chủ nhóm.");
        }
        if (newRole == GroupRole.OWNER) {
            throw new ChatActionException("Không thể thăng làm Chủ nhóm tại đây.");
        }
        // Chỉ OWNER mới được chỉ định ADMIN hoặc hạ quyền ADMIN
        if ((newRole == GroupRole.ADMIN || targetMembership.getRole() == GroupRole.ADMIN) && actorMembership.getRole() != GroupRole.OWNER) {
            throw new ChatActionException("Chỉ Chủ nhóm mới có quyền chỉ định hoặc thay đổi vai trò Quản trị viên.");
        }

        targetMembership.setRole(newRole);
        members.save(targetMembership);
        sendSystemMessage(targetMembership.getConversation(), actorId,
                targetMembership.getUser().getProfile().getDisplayName() + " đã được chuyển vai trò thành " + newRole.label() + ".");
    }

    /**
     * Xóa thành viên ra khỏi nhóm.
     */
    @Transactional
    public void removeMember(UUID actorId, UUID groupId, UUID targetUserId) {
        if (actorId.equals(targetUserId)) {
            throw new ChatActionException("Dùng chức năng rời nhóm thay vì tự xóa.");
        }
        ConversationMember actorMembership = members.findByConversationIdAndUserId(groupId, actorId)
                .orElseThrow(() -> new ChatActionException("Bạn không phải thành viên nhóm."));
        ConversationMember targetMembership = members.findByConversationIdAndUserId(groupId, targetUserId)
                .orElseThrow(() -> new ChatActionException("Người này không thuộc nhóm."));

        if (!actorMembership.getRole().canManage()) {
            throw new ChatActionException("Bạn không có quyền xóa thành viên.");
        }
        if (!actorMembership.getRole().outranks(targetMembership.getRole())) {
            throw new ChatActionException("Bạn không thể xóa người có vai trò ngang hàng hoặc cao hơn mình.");
        }

        members.delete(targetMembership);
        sendSystemMessage(targetMembership.getConversation(), actorId,
                targetMembership.getUser().getProfile().getDisplayName() + " đã bị mời ra khỏi nhóm.");
    }

    /**
     * Danh sách yêu cầu chờ duyệt vào nhóm.
     */
    @Transactional(readOnly = true)
    public List<GroupRequestView> listPendingRequests(UUID actorId, UUID groupId) {
        requireRole(actorId, groupId, GroupRole.MODERATOR);
        return requests.findByConversationIdWithUsers(groupId).stream()
                .map(r -> {
                    User u = r.getUser();
                    return new GroupRequestView(
                            u.getId(),
                            u.getUsername(),
                            u.getProfile().getDisplayName(),
                            u.getProfile().getAvatarUrl(),
                            r.getCreatedAt(),
                            r.getAnswer(),
                            u.getProfile().getAvatarFrame()
                    );
                })
                .sorted((a, b) -> a.createdAt().compareTo(b.createdAt()))
                .toList();
    }

    /**
     * Duyệt yêu cầu vào nhóm.
     */
    @Transactional
    public void approveRequest(UUID actorId, UUID groupId, UUID targetUserId) {
        requireRole(actorId, groupId, GroupRole.MODERATOR);
        Conversation conversation = conversations.findById(groupId)
                .orElseThrow(() -> new ChatActionException("Không tìm thấy nhóm."));
        ConversationRequest req = requests.findByConversationIdAndUserId(groupId, targetUserId)
                .orElseThrow(() -> new ChatActionException("Yêu cầu này không tồn tại hoặc đã được xử lý."));

        ConversationMember cm = new ConversationMember();
        cm.setConversation(conversation);
        cm.setUser(req.getUser());
        cm.setRole(GroupRole.MEMBER);
        cm.setJoinedAt(clock.instant());
        members.save(cm);
        requests.delete(req);

        sendSystemMessage(conversation, actorId, req.getUser().getProfile().getDisplayName() + " đã được phê duyệt vào nhóm.");
    }

    /**
     * Từ chối yêu cầu vào nhóm.
     */
    @Transactional
    public void rejectRequest(UUID actorId, UUID groupId, UUID targetUserId) {
        requireRole(actorId, groupId, GroupRole.MODERATOR);
        requests.deleteByConversationIdAndUserId(groupId, targetUserId);
    }

    /**
     * Ghim bài viết trong nhóm (tối đa 5 bài).
     */
    @Transactional
    public void pinPost(UUID actorId, UUID groupId, UUID postId) {
        requireRole(actorId, groupId, GroupRole.MODERATOR);
        Post post = posts.findById(postId).orElseThrow(() -> new ChatActionException("Không tìm thấy bài viết."));
        if (post.getGroupId() == null || !groupId.equals(post.getGroupId())) {
            throw new ChatActionException("Bài viết này không thuộc nhóm.");
        }
        if (post.isPinned()) {
            return;
        }
        if (posts.countPinnedByGroup(groupId) >= 5) {
            throw new ChatActionException("Nhóm chỉ được ghim tối đa 5 bài viết.");
        }
        post.setPinned(true);
        posts.save(post);
    }

    /**
     * Bỏ ghim bài viết trong nhóm.
     */
    @Transactional
    public void unpinPost(UUID actorId, UUID groupId, UUID postId) {
        requireRole(actorId, groupId, GroupRole.MODERATOR);
        Post post = posts.findById(postId).orElseThrow(() -> new ChatActionException("Không tìm thấy bài viết."));
        if (post.getGroupId() == null || !groupId.equals(post.getGroupId())) {
            throw new ChatActionException("Bài viết này không thuộc nhóm.");
        }
        post.setPinned(false);
        posts.save(post);
    }

    /**
     * Thành viên báo cáo bài đăng vi phạm trong nhóm.
     */
    @Transactional
    public void reportPost(UUID actorId, UUID groupId, UUID postId, String reason) {
        if (!members.existsByConversationIdAndUserId(groupId, actorId)) {
            throw new ChatActionException("Chỉ thành viên nhóm mới có thể gửi báo cáo.");
        }
        Post post = posts.findById(postId).orElseThrow(() -> new ChatActionException("Không tìm thấy bài viết."));
        if (post.getGroupId() == null || !groupId.equals(post.getGroupId())) {
            throw new ChatActionException("Bài viết này không thuộc nhóm.");
        }
        if (groupReports.existsByPostIdAndReporterId(postId, actorId)) {
            throw new ChatActionException("Bạn đã báo cáo bài viết này rồi.");
        }
        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.isEmpty()) {
            throw new ChatActionException("Vui lòng nhập lý do báo cáo.");
        }
        if (cleanReason.length() > 500) {
            cleanReason = cleanReason.substring(0, 500);
        }
        Conversation conversation = conversations.findById(groupId).orElseThrow();
        User reporter = users.findById(actorId).orElseThrow();
        groupReports.save(new GroupPostReport(conversation, post, reporter, cleanReason, clock.instant()));
    }

    /**
     * Danh sách bài viết bị báo cáo trong nhóm (dành cho Ban quản trị).
     */
    @Transactional(readOnly = true)
    public List<GroupReportView> listReports(UUID actorId, UUID groupId) {
        requireRole(actorId, groupId, GroupRole.MODERATOR);
        return groupReports.findByGroupIdWithDetails(groupId).stream()
                .map(r -> {
                    String snippet = r.getPost().getContent();
                    if (snippet != null && snippet.length() > 100) {
                        snippet = snippet.substring(0, 100) + "...";
                    }
                    return new GroupReportView(
                            r.getId(),
                            r.getPost().getId(),
                            snippet,
                            r.getReporter().getProfile().getDisplayName(),
                            r.getReporter().getUsername(),
                            r.getReason(),
                            r.getCreatedAt()
                    );
                })
                .toList();
    }

    /**
     * Bỏ qua báo cáo bài viết.
     */
    @Transactional
    public void dismissReport(UUID actorId, UUID groupId, UUID reportId) {
        requireRole(actorId, groupId, GroupRole.MODERATOR);
        groupReports.deleteById(reportId);
    }

    /**
     * Xóa bài viết bị báo cáo khỏi nhóm.
     */
    @Transactional
    public void deleteReportedPost(UUID actorId, UUID groupId, UUID reportId) {
        requireRole(actorId, groupId, GroupRole.MODERATOR);
        GroupPostReport report = groupReports.findById(reportId)
                .orElseThrow(() -> new ChatActionException("Báo cáo không tồn tại."));
        Post post = report.getPost();
        post.setDeletedAt(clock.instant());
        if (!post.isScheduled() && !post.isShare()) seasonActivity.eligibility(post.getId(), post.getAuthor().getId(), post.getCreatedAt(), false, post.getHashtags());
        if (post.isShare()) seasonActivity.interaction(post.getSharedPost().getId(), post.getAuthor().getId(), SeasonScoring.Kind.SHARE, post.getId(), false);
        posts.save(post);
        groupReports.deleteByPostId(post.getId());
    }

    /**
     * Đếm số yêu cầu chờ duyệt và báo cáo chờ xử lý.
     */
    @Transactional(readOnly = true)
    public long countPendingRequests(UUID groupId) {
        return requests.findByConversationIdWithUsers(groupId).size();
    }

    @Transactional(readOnly = true)
    public long countReports(UUID groupId) {
        return groupReports.countByGroupId(groupId);
    }

    /**
     * Rời khỏi nhóm.
     */
    @Transactional
    public void leaveGroup(UUID userId, UUID conversationId, UUID nextOwnerId) {
        chatService.leave(userId, conversationId, nextOwnerId);
    }

    /**
     * Chủ nhóm hoặc quản trị viên bật/tắt (liên kết) phòng chat cho hội nhóm.
     */
    @Transactional
    public boolean toggleChat(UUID actorId, UUID groupId) {
        Conversation c = conversations.findById(groupId)
                .orElseThrow(() -> new ChatActionException("Không tìm thấy nhóm này."));

        ConversationMember membership = members.findByConversationIdAndUserId(groupId, actorId)
                .orElseThrow(() -> new ChatActionException("Bạn không phải thành viên của nhóm này."));
        if (!membership.getRole().canManage()) {
            throw new ChatActionException("Chỉ chủ nhóm hoặc quản trị viên mới có quyền mở/đóng phòng chat.");
        }

        boolean newState = !c.isChatEnabled();
        c.setChatEnabled(newState);
        conversations.save(c);

        if (newState) {
            sendSystemMessage(c, actorId, "Chủ nhóm đã mở phòng chat cho nhóm.");
        }
        return newState;
    }

    private void requireRole(UUID actorId, UUID groupId, GroupRole minRole) {
        ConversationMember cm = members.findByConversationIdAndUserId(groupId, actorId)
                .orElseThrow(() -> new ChatActionException("Bạn không phải thành viên nhóm."));
        if (!cm.getRole().atLeast(minRole)) {
            throw new ChatActionException("Bạn không đủ quyền thực hiện thao tác này.");
        }
    }

    private void sendSystemMessage(Conversation conversation, UUID actorId, String text) {
        Message m = new Message();
        m.setConversation(conversation);
        m.setSender(users.getReferenceById(actorId));
        m.setContent(text);
        m.setSystem(true);
        m.setCreatedAt(clock.instant());
        messages.save(m);
    }
}
