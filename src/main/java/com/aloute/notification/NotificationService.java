package com.aloute.notification;

import com.aloute.post.Post;
import com.aloute.post.PostView;
import com.aloute.user.Profile;
import com.aloute.user.User;
import com.aloute.user.UserRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Tạo và đọc thông báo trong ứng dụng. Không bao giờ tự thông báo cho chính mình (ví dụ tự thả cảm xúc bài
 * mình, hoặc bình luận gốc rồi tự trả lời) — {@link #notify} lọc trường hợp này ở một chỗ duy nhất.
 */
@Service
public class NotificationService {

    private static final int RECENT_LIMIT = 30;

    private final NotificationRepository notifications;
    private final UserRepository users;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, UserRepository users, Clock clock) {
        this.notifications = notifications;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public void friendRequested(UUID actorId, UUID recipientId) {
        notify(recipientId, actorId, NotificationType.FRIEND_REQUEST, null);
    }

    @Transactional
    public void friendAccepted(UUID actorId, UUID recipientId) {
        notify(recipientId, actorId, NotificationType.FRIEND_ACCEPTED, null);
    }

    @Transactional
    public void newFollower(UUID actorId, UUID recipientId) {
        notify(recipientId, actorId, NotificationType.NEW_FOLLOWER, null);
    }

    @Transactional
    public void postReacted(UUID actorId, Post post) {
        notify(post.getAuthor().getId(), actorId, NotificationType.POST_REACTION, post);
    }

    @Transactional
    public void postCommented(UUID actorId, Post post) {
        notify(post.getAuthor().getId(), actorId, NotificationType.POST_COMMENT, post);
    }

    @Transactional
    public void commentReplied(UUID actorId, UUID parentAuthorId, Post post) {
        notify(parentAuthorId, actorId, NotificationType.COMMENT_REPLY, post);
    }

    @Transactional
    public void postShared(UUID actorId, Post originalPost) {
        notify(originalPost.getAuthor().getId(), actorId, NotificationType.POST_SHARED, originalPost);
    }

    @Transactional
    public void postTagged(UUID actorId, UUID recipientId, Post post) {
        notify(recipientId, actorId, NotificationType.POST_TAGGED, post);
    }

    @Transactional
    public void donated(UUID actorId, UUID recipientId) {
        notify(recipientId, actorId, NotificationType.DONATION, null);
    }

    @Transactional
    public void supportReplied(UUID managerId, UUID recipientId, UUID ticketId) {
        Notification notification = new Notification();
        notification.setRecipient(users.getReferenceById(recipientId));
        notification.setActor(users.getReferenceById(managerId));
        notification.setType(NotificationType.SUPPORT);
        notification.setReferenceId(ticketId);
        notifications.save(notification);
    }

    @Transactional
    public void reportResolved(UUID managerId, UUID recipientId, UUID reportId) {
        reportResolved(managerId, recipientId, reportId, "Báo cáo của bạn đã được xem xét và xác nhận có vi phạm.", null);
    }

    @Transactional
    public void reportResolved(UUID managerId, UUID recipientId, UUID reportId, String detail) {
        reportResolved(managerId, recipientId, reportId, "Báo cáo của bạn đã được xem xét và xác nhận có vi phạm.", detail);
    }

    @Transactional
    public void reportResolved(UUID managerId, UUID recipientId, UUID reportId, String customText, String detail) {
        Notification notification = new Notification();
        notification.setRecipient(users.getReferenceById(recipientId));
        notification.setActor(users.getReferenceById(managerId));
        notification.setType(NotificationType.REPORT);
        notification.setReferenceId(reportId);
        notification.setCustomText(customText != null ? customText : "Báo cáo của bạn đã được xem xét và xác nhận có vi phạm.");
        notification.setDetail(detail);
        notifications.save(notification);
    }

    @Transactional
    public void reportOwnerNotified(UUID managerId, UUID ownerId, Post post, UUID referenceId, String customText, String detail) {
        Notification notification = new Notification();
        notification.setRecipient(users.getReferenceById(ownerId));
        notification.setActor(users.getReferenceById(managerId));
        notification.setType(NotificationType.REPORT);
        if (post != null) {
            notification.setPost(post);
        }
        notification.setReferenceId(referenceId);
        notification.setCustomText(customText);
        notification.setDetail(detail);
        notifications.save(notification);
    }

    @Transactional
    public void warned(UUID managerId, UUID recipientId) {
        notify(recipientId, managerId, NotificationType.WARNING, null);
    }

    @Transactional
    public void mentionedInComment(UUID actorId, UUID recipientId, Post post) {
        notify(recipientId, actorId, NotificationType.MENTION, post);
    }

    @Transactional
    public void mentionedInChat(UUID actorId, UUID recipientId, UUID conversationId) {
        if (recipientId.equals(actorId)) {
            return;
        }
        Notification notification = new Notification();
        notification.setRecipient(users.getReferenceById(recipientId));
        notification.setActor(users.getReferenceById(actorId));
        notification.setType(NotificationType.MENTION);
        notification.setConversationId(conversationId);
        notifications.save(notification);
    }

    private void notify(UUID recipientId, UUID actorId, NotificationType type, Post post) {
        if (recipientId.equals(actorId)) {
            return;
        }
        Notification notification = new Notification();
        notification.setRecipient(users.getReferenceById(recipientId));
        notification.setActor(users.getReferenceById(actorId));
        notification.setType(type);
        if (post != null) {
            notification.setPost(post);
        }
        notifications.save(notification);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notifications.countByRecipientIdAndReadAtIsNull(userId);
    }

    /** 30 thông báo gần nhất, kèm trạng thái đã đọc TẠI THỜI ĐIỂM gọi (gọi trước khi {@link #markAllRead}). */
    @Transactional(readOnly = true)
    public List<NotificationView> listRecent(UUID userId) {
        return notifications.findRecentFor(userId, Limit.of(RECENT_LIMIT)).stream().map(NotificationService::toView).toList();
    }

    private static final List<NotificationType> PERSISTENT_UNREAD_TYPES = List.of(NotificationType.SUPPORT, NotificationType.REPORT);

    @Transactional
    public void markAllRead(UUID userId) {
        notifications.markAllNormalRead(userId, clock.instant(), PERSISTENT_UNREAD_TYPES);
    }

    @Transactional
    public void markRead(UUID notificationId, UUID userId) {
        notifications.findById(notificationId)
                .filter(n -> n.getRecipient().getId().equals(userId))
                .ifPresent(n -> {
                    if (n.getReadAt() == null) {
                        n.setReadAt(clock.instant());
                    }
                });
    }

    private static NotificationView toView(Notification n) {
        User actor = n.getActor();
        Profile profile = actor.getProfile();
        String detail = (n.getDetail() != null && !n.getDetail().isBlank()) ? n.getDetail() : n.getBroadcastBody();
        return new NotificationView(
                n.getId(),
                new PostView.AuthorView(actor.getId(), actor.getUsername(), profile.getDisplayName(),
                        profile.getAvatarUrl(), actor.primaryRole()),
                n.getType(),
                text(n),
                n.getPost() != null ? n.getPost().getId() : null,
                detail,
                n.getConversationId(),
                n.getReferenceId(),
                n.getCreatedAt(),
                n.isRead());
    }

    private static String text(Notification n) {
        if (n.getCustomText() != null && !n.getCustomText().isBlank()) {
            return n.getCustomText();
        }
        String name = n.getActor().getProfile().getDisplayName();
        return switch (n.getType()) {
            case FRIEND_REQUEST -> name + " đã gửi lời mời kết bạn cho bạn.";
            case FRIEND_ACCEPTED -> name + " đã chấp nhận lời mời kết bạn của bạn";
            case NEW_FOLLOWER -> name + " đã bắt đầu theo dõi bạn";
            case POST_REACTION -> name + " đã bày tỏ cảm xúc về bài viết của bạn";
            case POST_COMMENT -> name + " đã bình luận về bài viết của bạn";
            case COMMENT_REPLY -> name + " đã trả lời bình luận của bạn";
            case POST_SHARED -> name + " đã chia sẻ bài viết của bạn";
            case MENTION -> n.getConversationId() != null
                    ? name + " đã nhắc đến bạn trong nhóm " + (n.getConversationTitle() == null ? "chat" : n.getConversationTitle())
                    : name + " đã nhắc đến bạn trong một bình luận";
            case WARNING -> "Bạn đã nhận được cảnh báo về việc vi phạm Tiêu chuẩn cộng đồng.";
            case BROADCAST -> name + " gửi thông báo: " + (n.getBroadcastTitle() == null ? "" : n.getBroadcastTitle());
            case DONATION -> name + " đã tặng Xu cho bạn";
            case POST_TAGGED -> name + " đã gắn thẻ bạn trong một bài viết";
            case SUPPORT -> "Yêu cầu hỗ trợ của bạn đã có phản hồi mới.";
            case REPORT -> "Báo cáo của bạn đã được xem xét và xác nhận có vi phạm.";
        };
    }
}
