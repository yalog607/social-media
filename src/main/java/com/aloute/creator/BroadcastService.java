package com.aloute.creator;

import com.aloute.user.Role;
import com.aloute.user.User;
import com.aloute.user.UserRepository;
import com.aloute.wallet.FanBadge;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Nhắn hàng loạt: tạo một bản ghi {@code broadcasts} rồi chèn thông báo cho mọi người nhận bằng MỘT câu INSERT ... SELECT
 * (không lặp từng người trong Java), nên chi phí không tăng theo số người theo dõi. Người nhận loại trừ người bị chặn
 * theo cả hai chiều và tài khoản không còn hoạt động. Giới hạn theo CSDL (không phải bộ nhớ) nên khởi động lại
 * ứng dụng không làm Creator gửi vượt hạn mức.
 */
@Service
public class BroadcastService {

    public static final int MAX_PER_DAY = 3;
    public static final int MAX_TITLE = 80;
    public static final int MAX_BODY = 500;

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final Clock clock;

    public BroadcastService(JdbcTemplate jdbc, UserRepository users, Clock clock) {
        this.jdbc = jdbc;
        this.users = users;
        this.clock = clock;
    }

    /**
     * @return số người nhận
     * @throws InvalidBroadcastException nội dung không hợp lệ, hết hạn mức trong ngày, không phải Creator,
     *                                   hoặc không có người nhận nào
     */
    @Transactional
    public int send(UUID creatorId, BroadcastAudience audience, String title, String body) {
        User creator = users.findById(creatorId).filter(User::isActive).filter(u -> u.hasRole(Role.CREATOR))
                .orElseThrow(() -> new InvalidBroadcastException("Chỉ Creator mới nhắn hàng loạt được."));
        String cleanTitle = clean(title, MAX_TITLE, "Tiêu đề");
        String cleanBody = clean(body, MAX_BODY, "Nội dung");

        // Khóa dòng người dùng để hai lần gửi đồng thời của cùng một Creator không cùng vượt hạn mức
        jdbc.query("select id from users where id = ? for update", rs -> { }, creator.getId());
        Timestamp now = Timestamp.from(clock.instant());
        Long sentToday = jdbc.queryForObject("select count(*) from broadcasts where creator_id = ? and created_at > ?",
                Long.class, creatorId, Timestamp.from(clock.instant().minus(Duration.ofDays(1))));
        if (sentToday != null && sentToday >= MAX_PER_DAY) {
            throw new InvalidBroadcastException("Mỗi ngày bạn chỉ nhắn hàng loạt được " + MAX_PER_DAY + " lần, mai thử lại nhé.");
        }

        UUID broadcastId = UUID.randomUUID();
        jdbc.update("insert into broadcasts (id, creator_id, audience, title, body, created_at) values (?, ?, ?, ?, ?, ?)",
                broadcastId, creatorId, audience.name(), cleanTitle, cleanBody, now);
        String recipients = audience == BroadcastAudience.FANS ? FANS_SQL : FOLLOWERS_SQL;
        int count = jdbc.update("""
                insert into notifications (id, recipient_id, actor_id, type, broadcast_id, created_at)
                select gen_random_uuid(), r.id, ?, 'BROADCAST', ?, ?
                from users r
                where r.status = 'ACTIVE' and r.id <> ?
                  and r.id in (""" + recipients + """
                  )
                  and not exists (select 1 from blocks b
                                  where (b.blocker_id = r.id and b.blocked_id = ?) or (b.blocker_id = ? and b.blocked_id = r.id))""",
                creatorId, broadcastId, now, creatorId, creatorId, creatorId, creatorId);
        if (count == 0) {
            throw new InvalidBroadcastException("Chưa có ai nhận tin này (chưa có "
                    + (audience == BroadcastAudience.FANS ? "fan" : "người theo dõi") + " nào).");
        }
        jdbc.update("update broadcasts set recipient_count = ? where id = ?", count, broadcastId);
        return count;
    }

    @Transactional(readOnly = true)
    public List<BroadcastView> history(UUID creatorId) {
        return jdbc.query("""
                select title, body, audience, recipient_count, created_at from broadcasts
                where creator_id = ? order by created_at desc, id desc limit 30""",
                (rs, i) -> new BroadcastView(rs.getString(1), rs.getString(2), BroadcastAudience.valueOf(rs.getString(3)),
                        rs.getInt(4), rs.getTimestamp(5).toInstant()),
                creatorId);
    }

    // Truy vấn con chọn người nhận; tham số ? duy nhất của mỗi câu là id của Creator
    private static final String FOLLOWERS_SQL = "select f.follower_id from follows f where f.followee_id = ?";
    private static final String FANS_SQL = "select t.counterparty_id from wallet_transactions t "
            + "where t.user_id = ? and t.type = 'DONATE_RECEIVED' and t.counterparty_id is not null "
            + "group by t.counterparty_id having sum(t.amount) >= " + FanBadge.BRONZE.threshold();

    private static String clean(String text, int max, String field) {
        String cleaned = text == null ? "" : text.replaceAll("[\\p{Cntrl}&&[^\\n\\r\\t]]", "").strip();
        if (cleaned.isEmpty()) {
            throw new InvalidBroadcastException(field + " không được để trống.");
        }
        if (cleaned.codePointCount(0, cleaned.length()) > max) {
            throw new InvalidBroadcastException(field + " tối đa " + max + " ký tự.");
        }
        return cleaned;
    }
}
