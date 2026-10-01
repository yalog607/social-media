package com.aloute.moderation;

import com.aloute.audit.AuditService;
import com.aloute.post.Hashtags;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** Hashtag bị cấm: bài mới (và lời nhắn chia sẻ) chứa các thẻ này bị từ chối. Bài cũ giữ nguyên, chỉ bị loại khỏi "thịnh hành". */
@Service
public class BannedHashtags {

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final Clock clock;

    public BannedHashtags(JdbcTemplate jdbc, AuditService audit, Clock clock) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    /** Những thẻ trong {@code text} đang bị cấm (đã chuẩn hóa), rỗng nếu không có. */
    @Transactional(readOnly = true)
    public List<String> bannedIn(String text) {
        List<String> tags = Hashtags.extract(text);
        if (tags.isEmpty()) {
            return List.of();
        }
        String marks = String.join(",", java.util.Collections.nCopies(tags.size(), "?"));
        return jdbc.queryForList("select tag from banned_hashtags where tag in (" + marks + ") order by tag",
                String.class, tags.toArray());
    }

    @Transactional(readOnly = true)
    public List<String> list() {
        return jdbc.queryForList("select tag from banned_hashtags order by tag", String.class);
    }

    /** @throws InvalidModerationException thẻ không hợp lệ */
    @Transactional
    public void ban(UUID managerId, String raw) {
        String tag = normalize(raw);
        if (jdbc.update("insert into banned_hashtags (tag, banned_by, created_at) values (?, ?, ?) on conflict do nothing",
                tag, managerId, Timestamp.from(clock.instant())) == 1) {
            audit.log(managerId, "HASHTAG_BANNED", "HASHTAG", null, tag);
        }
    }

    @Transactional
    public void unban(UUID managerId, String raw) {
        String tag = normalize(raw);
        if (jdbc.update("delete from banned_hashtags where tag = ?", tag) == 1) {
            audit.log(managerId, "HASHTAG_UNBANNED", "HASHTAG", null, tag);
        }
    }

    private static String normalize(String raw) {
        String body = raw == null ? "" : raw.strip().replaceFirst("^#+", "");
        List<String> tags = body.matches(".*[\\s#].*") ? List.of() : Hashtags.extract("#" + body);
        if (tags.size() != 1) {
            throw new InvalidModerationException("Hashtag không hợp lệ.");
        }
        return tags.get(0);
    }
}
