package com.aloute.moderation;

import com.aloute.audit.AuditService;
import com.aloute.common.RateAction;
import com.aloute.common.RateLimiter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Hỗ trợ người dùng: người dùng gửi phiếu, Manager phản hồi một lần rồi đóng phiếu. */
@Service
public class SupportService {

    public static final int MAX_SUBJECT = 100;
    public static final int MAX_BODY = 1000;

    public record Ticket(UUID id, String subject, String body, boolean open, String reply, Instant createdAt,
                         UUID userId, String userName) {
    }

    private final JdbcTemplate jdbc;
    private final RateLimiter rateLimiter;
    private final AuditService audit;
    private final Clock clock;

    public SupportService(JdbcTemplate jdbc, RateLimiter rateLimiter, AuditService audit, Clock clock) {
        this.jdbc = jdbc;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public void submit(UUID userId, String subject, String body) {
        rateLimiter.check(RateAction.SUPPORT, userId);
        jdbc.update("insert into support_tickets (id, user_id, subject, body, created_at) values (?, ?, ?, ?, ?)",
                UUID.randomUUID(), userId, clean(subject, MAX_SUBJECT, "Tiêu đề"), clean(body, MAX_BODY, "Nội dung"),
                Timestamp.from(clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<Ticket> mine(UUID userId) {
        return query("where t.user_id = ? order by t.created_at desc, t.id desc limit 30", userId);
    }

    @Transactional(readOnly = true)
    public List<Ticket> open() {
        return query("where t.status = 'OPEN' order by t.created_at, t.id limit 50");
    }

    /** @throws InvalidModerationException phiếu đã đóng hoặc phản hồi không hợp lệ */
    @Transactional
    public void reply(UUID managerId, UUID ticketId, String reply) {
        String text = clean(reply, MAX_BODY, "Phản hồi");
        int updated = jdbc.update("""
                update support_tickets set status = 'CLOSED', reply = ?, handled_by = ?, handled_at = ?
                where id = ? and status = 'OPEN'""", text, managerId, Timestamp.from(clock.instant()), ticketId);
        if (updated == 0) {
            throw new InvalidModerationException("Phiếu này đã được xử lý rồi.");
        }
        audit.log(managerId, "TICKET_REPLIED", "TICKET", ticketId, null);
    }

    private List<Ticket> query(String where, Object... args) {
        return jdbc.query("""
                select t.id, t.subject, t.body, t.status, t.reply, t.created_at, t.user_id, p.display_name
                from support_tickets t join profiles p on p.user_id = t.user_id """ + " " + where,
                (rs, i) -> new Ticket(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        "OPEN".equals(rs.getString(4)), rs.getString(5), rs.getTimestamp(6).toInstant(),
                        rs.getObject(7, UUID.class), rs.getString(8)), args);
    }

    private static String clean(String text, int max, String field) {
        String cleaned = text == null ? "" : text.replaceAll("[\\p{Cntrl}&&[^\\n\\r\\t]]", "").strip();
        if (cleaned.isEmpty()) {
            throw new InvalidModerationException(field + " không được để trống.");
        }
        if (cleaned.codePointCount(0, cleaned.length()) > max) {
            throw new InvalidModerationException(field + " tối đa " + max + " ký tự.");
        }
        return cleaned;
    }
}
