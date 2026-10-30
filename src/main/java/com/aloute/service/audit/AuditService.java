package com.aloute.service.audit;

import com.aloute.dto.audit.AuditEntry;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Nhật ký hệ thống (chỉ thêm). Ghi trong CÙNG giao dịch với hành động được ghi, nên hành động bị hoàn tác thì dòng
 * nhật ký cũng không còn — không bao giờ có dòng nhật ký cho việc chưa xảy ra.
 */
import com.aloute.dto.common.PageResult;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;

@Service
public class AuditService {

    public static final int PAGE_SIZE = 50;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AuditService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void log(UUID actorId, String action, String targetType, UUID targetId, String detail) {
        String clipped = detail == null ? null : (detail.length() > 500 ? detail.substring(0, 500) : detail);
        jdbc.update("""
                insert into audit_logs (id, actor_id, action, target_type, target_id, detail, created_at)
                values (?, ?, ?, ?, ?, ?, ?)""", UUID.randomUUID(), actorId, action, targetType, targetId, clipped,
                Timestamp.from(clock.instant()));
    }

    /** Tìm kiếm nhật ký kết hợp người thực hiện, hành động, khoảng ngày và phân trang. */
    @Transactional(readOnly = true)
    public PageResult<AuditEntry> search(int page, int size, String action, String actor, LocalDate from, LocalDate to) {
        int safeSize = size <= 0 ? 20 : size;
        int safePage = Math.max(0, page);
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");

        StringBuilder where = new StringBuilder("where 1=1 ");
        List<Object> args = new ArrayList<>();
        if (action != null && !action.isBlank()) {
            where.append("and l.action = ? ");
            args.add(action.trim());
        }
        if (actor != null && !actor.isBlank()) {
            where.append("and (lower(u.username) like ? or lower(p.display_name) like ?) ");
            String like = "%" + actor.strip().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            args.add(like);
            args.add(like);
        }
        if (from != null) {
            where.append("and l.created_at >= ? ");
            args.add(Timestamp.from(from.atStartOfDay(zone).toInstant()));
        }
        if (to != null) {
            where.append("and l.created_at <= ? ");
            args.add(Timestamp.from(to.atTime(LocalTime.MAX).atZone(zone).toInstant()));
        }

        String countSql = "select count(*) from audit_logs l left join users u on u.id = l.actor_id left join profiles p on p.user_id = l.actor_id " + where;
        Long total = jdbc.queryForObject(countSql, Long.class, args.toArray());
        long count = total == null ? 0 : total;

        String dataSql = """
                select l.created_at, l.action, l.target_type, l.target_id, l.detail, p.display_name, u.username
                from audit_logs l left join users u on u.id = l.actor_id left join profiles p on p.user_id = l.actor_id
                """ + where + "order by l.created_at desc, l.id desc limit ? offset ?";

        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(safeSize);
        queryArgs.add(safePage * safeSize);

        List<AuditEntry> items = jdbc.query(dataSql, (rs, i) -> new AuditEntry(
                rs.getTimestamp(1).toInstant(), rs.getString(2), rs.getString(3),
                rs.getObject(4, UUID.class), rs.getString(5), rs.getString(6), rs.getString(7)),
                queryArgs.toArray());

        return PageResult.of(items, safePage, safeSize, count);
    }

    /** Trang nhật ký, mới nhất trước; {@code page} bắt đầu từ 0 (tương thích ngược). */
    @Transactional(readOnly = true)
    public List<AuditEntry> page(int page, String action) {
        boolean filtered = action != null && !action.isBlank();
        String sql = """
                select l.created_at, l.action, l.target_type, l.target_id, l.detail, p.display_name, u.username
                from audit_logs l left join users u on u.id = l.actor_id left join profiles p on p.user_id = l.actor_id
                """ + (filtered ? "where l.action = ? " : "") + "order by l.created_at desc, l.id desc limit ? offset ?";
        Object[] args = filtered
                ? new Object[]{action, PAGE_SIZE + 1, Math.max(0, page) * PAGE_SIZE}
                : new Object[]{PAGE_SIZE + 1, Math.max(0, page) * PAGE_SIZE};
        return jdbc.query(sql, (rs, i) -> new AuditEntry(rs.getTimestamp(1).toInstant(), rs.getString(2), rs.getString(3),
                rs.getObject(4, UUID.class), rs.getString(5), rs.getString(6), rs.getString(7)), args);
    }
}
