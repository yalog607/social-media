package com.aloute.post;

import com.aloute.notification.NotificationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Đăng các bài hẹn giờ đã đến hạn: bỏ dấu "hẹn giờ" và đặt {@code created_at} bằng giờ hẹn để bài nằm đúng chỗ trên
 * bảng tin, rồi gửi thông báo gắn thẻ đã hoãn. {@link ScheduledPostRunner} gọi mỗi phút; test gọi thẳng
 * {@link #publishDue()} với đồng hồ giả.
 */
@Component
public class ScheduledPostPublisher {

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final PostTagRepository tags;
    private final PostRepository posts;
    private final NotificationService notifications;

    public ScheduledPostPublisher(JdbcTemplate jdbc, Clock clock, PostTagRepository tags, PostRepository posts,
                                  NotificationService notifications) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.tags = tags;
        this.posts = posts;
        this.notifications = notifications;
    }

    /** @return số bài vừa được đăng */
    @Transactional
    public int publishDue() {
        // Một câu UPDATE ... RETURNING nên hai tiến trình chạy trùng không đăng (và thông báo) một bài hai lần
        List<UUID> published = jdbc.query("""
                update posts set created_at = scheduled_at, scheduled_at = null
                where scheduled_at is not null and scheduled_at <= ? and deleted_at is null
                returning id""", (rs, i) -> rs.getObject(1, UUID.class), Timestamp.from(clock.instant()));
        if (published.isEmpty()) {
            return 0;
        }
        for (PostTag tag : tags.findByPostIds(published)) {
            Post post = posts.findLive(tag.getPost().getId()).orElse(null);
            if (post != null && post.getVisibility() != com.aloute.user.Visibility.PRIVATE) {
                notifications.postTagged(post.getAuthor().getId(), tag.getTaggedUser().getId(), post);
            }
        }
        return published.size();
    }
}
