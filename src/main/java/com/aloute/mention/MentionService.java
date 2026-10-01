package com.aloute.mention;

import com.aloute.notification.NotificationService;
import com.aloute.post.Mentions;
import com.aloute.post.Post;
import com.aloute.post.PostService;
import com.aloute.social.BlockService;
import com.aloute.user.User;
import com.aloute.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Gửi thông báo cho người được nhắc tên ({@code @username}). Không bao giờ thông báo cho chính người viết, người đã được
 * thông báo vì lý do khác trong cùng hành động (ví dụ chủ bài vừa nhận thông báo "đã bình luận"), người không xem được
 * nội dung, hoặc người có quan hệ chặn với người viết.
 */
@Service
public class MentionService {

    private final UserRepository users;
    private final PostService posts;
    private final BlockService blocks;
    private final NotificationService notifications;

    public MentionService(UserRepository users, PostService posts, BlockService blocks, NotificationService notifications) {
        this.users = users;
        this.posts = posts;
        this.blocks = blocks;
        this.notifications = notifications;
    }

    /** Người dùng còn hoạt động ứng với các username được nhắc trong {@code text}. */
    @Transactional(readOnly = true)
    public List<User> resolve(String text) {
        return Mentions.extract(text).stream()
                .map(users::findByUsername)
                .flatMap(java.util.Optional::stream)
                .filter(User::isActive)
                .toList();
    }

    /**
     * Thông báo cho những người được nhắc trong một bình luận.
     *
     * @param alreadyNotified những người đã nhận thông báo khác từ cùng bình luận (được bỏ qua)
     * @return số người được thông báo
     */
    @Transactional
    public int notifyComment(UUID authorId, Post post, String text, Set<UUID> alreadyNotified) {
        Set<UUID> skip = new HashSet<>(alreadyNotified);
        skip.add(authorId);
        int count = 0;
        for (User user : resolve(text)) {
            if (skip.contains(user.getId()) || blocks.isBlockedEitherWay(authorId, user.getId())
                    || !posts.canView(post, user.getId())) {
                continue;
            }
            notifications.mentionedInComment(authorId, user.getId(), post);
            skip.add(user.getId());
            count++;
        }
        return count;
    }

    /**
     * Thông báo cho những người được nhắc trong một tin nhắn nhóm.
     *
     * @param memberIds thành viên của hội thoại (chỉ thành viên mới được thông báo)
     */
    @Transactional
    public int notifyChat(UUID authorId, UUID conversationId, Set<UUID> memberIds, String text) {
        int count = 0;
        for (User user : resolve(text)) {
            if (user.getId().equals(authorId) || !memberIds.contains(user.getId())) {
                continue;
            }
            notifications.mentionedInChat(authorId, user.getId(), conversationId);
            count++;
        }
        return count;
    }
}
