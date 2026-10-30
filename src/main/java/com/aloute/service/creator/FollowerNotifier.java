package com.aloute.service.creator;

import com.aloute.model.notification.NotificationType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

/**
 * Gửi một thông báo về bài của Creator cho mọi người theo dõi bằng MỘT câu INSERT ... SELECT (không lặp từng người).
 * Loại trừ chính Creator, tài khoản không còn hoạt động và người bị chặn theo cả hai chiều.
 */
@Component
public class FollowerNotifier {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public FollowerNotifier(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** @return số người nhận */
    public int notifyFollowers(UUID creatorId, UUID postId, NotificationType type) {
        return jdbc.update("""
                insert into notifications (id, recipient_id, actor_id, type, post_id, created_at)
                select gen_random_uuid(), r.id, ?, ?, ?, ?
                from users r
                where r.status = 'ACTIVE' and r.id <> ?
                  and r.id in (select f.follower_id from follows f where f.followee_id = ?)
                  and not exists (select 1 from blocks b
                                  where (b.blocker_id = r.id and b.blocked_id = ?) or (b.blocker_id = ? and b.blocked_id = r.id))""",
                creatorId, type.name(), postId, Timestamp.from(clock.instant()), creatorId, creatorId, creatorId, creatorId);
    }
}
