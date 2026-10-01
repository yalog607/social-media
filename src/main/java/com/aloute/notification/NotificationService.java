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

    @Transactional
    public void markAllRead(UUID userId) {
        notifications.markAllRead(userId, clock.instant());
    }

    private static NotificationView toView(Notification n) {
        User actor = n.getActor();
        Profile profile = actor.getProfile();
        return new NotificationView(
                n.getId(),
                new PostView.AuthorView(actor.getId(), actor.getUsername(), profile.getDisplayName(),
                        profile.getAvatarUrl(), actor.primaryRole()),
                text(n),
                n.getPost() != null ? n.getPost().getId() : null,
                n.getCreatedAt(),
                n.isRead());
    }

    private static String text(Notification n) {
        String name = n.getActor().getProfile().getDisplayName();
        return switch (n.getType()) {
            case FRIEND_REQUEST -> name + " đã gửi cho bạn một lời mời kết bạn";
            case FRIEND_ACCEPTED -> name + " đã chấp nhận lời mời kết bạn của bạn";
            case NEW_FOLLOWER -> name + " đã bắt đầu theo dõi bạn";
            case POST_REACTION -> name + " đã bày tỏ cảm xúc về bài viết của bạn";
            case POST_COMMENT -> name + " đã bình luận về bài viết của bạn";
            case COMMENT_REPLY -> name + " đã trả lời bình luận của bạn";
            case POST_SHARED -> name + " đã chia sẻ bài viết của bạn";
            case POST_TAGGED -> name + " đã gắn thẻ bạn trong một bài viết";
        };
    }
}
