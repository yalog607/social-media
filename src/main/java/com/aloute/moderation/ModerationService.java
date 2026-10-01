package com.aloute.moderation;

import com.aloute.audit.AuditService;
import com.aloute.notification.NotificationService;
import com.aloute.security.RefreshTokenService;
import com.aloute.user.Role;
import com.aloute.user.User;
import com.aloute.user.UserRepository;
import com.aloute.user.UserStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Hàng đợi báo cáo và chế tài của Manager. Mọi thao tác ghi nhật ký (cùng giao dịch) và đóng MỌI báo cáo đang mở
 * về cùng một đối tượng, để nội dung bị báo cáo nhiều lần chỉ phải xử lý một lần. Manager không được xử phạt
 * nhân sự (Manager/Admin) hay chính mình.
 */
@Service
public class ModerationService {

    private static final int QUEUE_LIMIT = 50;
    private static final int MAX_NOTE = 300;

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final RefreshTokenService refreshTokens;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    public ModerationService(JdbcTemplate jdbc, UserRepository users, RefreshTokenService refreshTokens,
                             NotificationService notifications, AuditService audit, Clock clock) {
        this.jdbc = jdbc;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ReportItem> openReports() {
        return jdbc.query("""
                select r.id, r.target_type, r.target_id, r.reason, r.detail, r.created_at, rp.display_name,
                       coalesce(p.content, c.content, tp.display_name) as preview,
                       (p.deleted_at is not null or c.deleted_at is not null) as removed,
                       coalesce(p.author_id, c.author_id, tu.id) as owner_id, op.display_name
                from reports r
                join profiles rp on rp.user_id = r.reporter_id
                left join posts p on r.target_type = 'POST' and p.id = r.target_id
                left join comments c on r.target_type = 'COMMENT' and c.id = r.target_id
                left join users tu on r.target_type = 'USER' and tu.id = r.target_id
                left join profiles tp on tp.user_id = tu.id
                left join profiles op on op.user_id = coalesce(p.author_id, c.author_id, tu.id)
                where r.status = 'OPEN'
                order by r.created_at, r.id limit ?""",
                (rs, i) -> new ReportItem(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class),
                        rs.getString(4), rs.getString(5), rs.getTimestamp(6).toInstant(), rs.getString(7),
                        clip(rs.getString(8)), rs.getBoolean(9), rs.getObject(10, UUID.class), rs.getString(11)),
                QUEUE_LIMIT);
    }

    /**
     * @param note lý do/ghi chú (bắt buộc với cảnh cáo và khóa; hiển thị trong lịch sử chế tài)
     * @throws InvalidModerationException báo cáo không còn mở, thiếu ghi chú, hành động không áp dụng được,
     *                                    hoặc đối tượng là nhân sự/chính Manager
     */
    @Transactional
    public void handle(UUID managerId, UUID reportId, ReportAction action, String note) {
        var rows = jdbc.queryForList("select target_type, target_id from reports where id = ? and status = 'OPEN' for update",
                reportId);
        if (rows.isEmpty()) {
            throw new InvalidModerationException("Báo cáo này đã được xử lý rồi.");
        }
        String type = (String) rows.get(0).get("target_type");
        UUID targetId = (UUID) rows.get(0).get("target_id");
        String cleanNote = cleanNote(note, action == ReportAction.WARN || action == ReportAction.SUSPEND);
        UUID ownerId = ownerOf(type, targetId);

        String status = action == ReportAction.DISMISS ? "DISMISSED" : "RESOLVED";
        switch (action) {
            case DISMISS -> { }
            case REMOVE_CONTENT -> removeContent(type, targetId);
            case WARN -> warn(managerId, requireSanctionable(managerId, ownerId), cleanNote, reportId);
            case SUSPEND -> suspend(managerId, requireSanctionable(managerId, ownerId), cleanNote, reportId);
        }
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("""
                update reports set status = ?, handled_by = ?, handled_at = ?
                where status = 'OPEN' and target_type = ? and target_id = ?""", status, managerId, now, type, targetId);
        audit.log(managerId, "REPORT_" + action.name(), type, targetId, cleanNote);
    }

    /** Gỡ khóa. @throws InvalidModerationException người này không đang bị khóa */
    @Transactional
    public void lift(UUID managerId, UUID userId) {
        User user = users.findById(userId).filter(u -> u.getStatus() == UserStatus.SUSPENDED)
                .orElseThrow(() -> new InvalidModerationException("Người này không đang bị khóa."));
        user.setStatus(UserStatus.ACTIVE);
        users.save(user);
        jdbc.update("update sanctions set lifted_at = ?, lifted_by = ? where user_id = ? and type = 'SUSPENSION' and lifted_at is null",
                Timestamp.from(clock.instant()), managerId, userId);
        audit.log(managerId, "USER_UNSUSPENDED", "USER", userId, null);
    }

    @Transactional(readOnly = true)
    public List<SuspendedUser> suspended() {
        return jdbc.query("""
                select u.id, u.username, p.display_name,
                       (select s.reason from sanctions s where s.user_id = u.id and s.type = 'SUSPENSION'
                        order by s.created_at desc limit 1),
                       (select s.created_at from sanctions s where s.user_id = u.id and s.type = 'SUSPENSION'
                        order by s.created_at desc limit 1)
                from users u join profiles p on p.user_id = u.id
                where u.status = 'SUSPENDED' order by u.updated_at desc limit 100""",
                (rs, i) -> new SuspendedUser(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toInstant()));
    }

    // ---------- Nội bộ ----------

    private UUID ownerOf(String type, UUID targetId) {
        String sql = switch (type) {
            case "POST" -> "select author_id from posts where id = ?";
            case "COMMENT" -> "select author_id from comments where id = ?";
            default -> "select id from users where id = ?";
        };
        var found = jdbc.queryForList(sql, UUID.class, targetId);
        return found.isEmpty() ? null : found.get(0);
    }

    private void removeContent(String type, UUID targetId) {
        String table = switch (type) {
            case "POST" -> "posts";
            case "COMMENT" -> "comments";
            default -> throw new InvalidModerationException("Không thể xóa một tài khoản bằng cách này, hãy cảnh cáo hoặc khóa.");
        };
        jdbc.update("update " + table + " set deleted_at = ? where id = ? and deleted_at is null",
                Timestamp.from(clock.instant()), targetId);
    }

    private User requireSanctionable(UUID managerId, UUID ownerId) {
        User owner = ownerId == null ? null : users.findById(ownerId).orElse(null);
        if (owner == null) {
            throw new InvalidModerationException("Không còn tìm thấy người này.");
        }
        if (owner.getId().equals(managerId)) {
            throw new InvalidModerationException("Bạn không thể xử phạt chính mình.");
        }
        if (owner.hasRole(Role.MANAGER) || owner.hasRole(Role.ADMIN)) {
            throw new InvalidModerationException("Không thể xử phạt nhân sự quản trị.");
        }
        return owner;
    }

    private void warn(UUID managerId, User owner, String reason, UUID reportId) {
        recordSanction(owner.getId(), "WARNING", reason, reportId, managerId);
        notifications.warned(managerId, owner.getId());
    }

    private void suspend(UUID managerId, User owner, String reason, UUID reportId) {
        if (owner.getStatus() == UserStatus.ACTIVE) {
            owner.setStatus(UserStatus.SUSPENDED);
            users.save(owner);
            refreshTokens.revokeAll(owner.getId());
        }
        recordSanction(owner.getId(), "SUSPENSION", reason, reportId, managerId);
    }

    private void recordSanction(UUID userId, String type, String reason, UUID reportId, UUID managerId) {
        jdbc.update("""
                insert into sanctions (id, user_id, type, reason, report_id, imposed_by, created_at)
                values (?, ?, ?, ?, ?, ?, ?)""", UUID.randomUUID(), userId, type, reason, reportId, managerId,
                Timestamp.from(clock.instant()));
    }

    private static String cleanNote(String note, boolean required) {
        String text = note == null ? "" : note.replaceAll("[\\p{Cntrl}&&[^\\n\\r\\t]]", "").strip();
        if (required && text.isEmpty()) {
            throw new InvalidModerationException("Hãy ghi lý do để lưu vào lịch sử chế tài.");
        }
        if (text.codePointCount(0, text.length()) > MAX_NOTE) {
            throw new InvalidModerationException("Ghi chú tối đa " + MAX_NOTE + " ký tự.");
        }
        return text.isEmpty() ? null : text;
    }

    private static String clip(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.strip().replaceAll("\\s+", " ");
        return flat.codePointCount(0, flat.length()) <= 200 ? flat : flat.substring(0, flat.offsetByCodePoints(0, 200)) + "…";
    }
}
