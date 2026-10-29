package com.aloute.service.admin;

import com.aloute.dto.admin.StaffMember;
import com.aloute.exception.admin.InvalidAdminActionException;

import com.aloute.service.audit.AuditService;
import com.aloute.security.RefreshTokenService;
import com.aloute.model.user.Role;
import com.aloute.model.user.User;
import com.aloute.repository.user.UserRepository;
import com.aloute.model.user.UserStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aloute.dto.common.PageResult;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Quản trị nhân sự và tài khoản. Admin không thể thao tác lên Admin khác hay chính mình (tránh tự khóa/hạ quyền
 * làm hệ thống mất người quản trị). Quyền nằm trong JWT nên thay đổi vai trò/khóa có hiệu lực đầy đủ khi token
 * truy cập hiện tại hết hạn (tối đa 15 phút); các phiên làm mới đều bị thu hồi ngay.
 */
@Service
public class AdminService {

    public record OverviewData(
            String period,
            String periodLabel,
            LocalDate fromDate,
            LocalDate toDate,
            Map<String, Long> periodStats,
            Map<String, Long> systemStats
    ) {
    }

    private static final int MAX_RESULTS = 50;

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final RefreshTokenService refreshTokens;
    private final AuditService audit;
    private final Clock clock;

    public AdminService(JdbcTemplate jdbc, UserRepository users, RefreshTokenService refreshTokens,
                        AuditService audit, Clock clock) {
        this.jdbc = jdbc;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
        this.clock = clock;
    }

    /** Tìm theo username/email/tên hiển thị có phân trang (rỗng thì trả những tài khoản mới nhất). */
    @Transactional(readOnly = true)
    public PageResult<StaffMember> search(String query, int page, int size) {
        int safeSize = size <= 0 ? 20 : size;
        int safePage = Math.max(0, page);
        String like = "%" + (query == null ? "" : query.strip().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")) + "%";

        String countSql = """
                select count(*) from users u join profiles p on p.user_id = u.id
                where lower(u.username) like ? escape '\\' or lower(u.email) like ? escape '\\'
                   or lower(p.display_name) like ? escape '\\'
                """;
        Long total = jdbc.queryForObject(countSql, Long.class, like, like, like);
        long count = total == null ? 0 : total;

        String dataSql = BASE + """
                where lower(u.username) like ? escape '\\' or lower(u.email) like ? escape '\\'
                   or lower(p.display_name) like ? escape '\\'
                order by u.created_at desc limit ? offset ?""";
        List<StaffMember> items = jdbc.query(dataSql, MAP, like, like, like, safeSize, safePage * safeSize);
        return PageResult.of(items, safePage, safeSize, count);
    }

    @Transactional(readOnly = true)
    public List<StaffMember> search(String query) {
        return search(query, 0, MAX_RESULTS).items();
    }

    @Transactional(readOnly = true)
    public PageResult<StaffMember> managers(int page, int size) {
        int safeSize = size <= 0 ? 20 : size;
        int safePage = Math.max(0, page);

        String countSql = """
                select count(*) from users u
                where exists (select 1 from user_roles r where r.user_id = u.id and r.role = 'MANAGER')
                """;
        Long total = jdbc.queryForObject(countSql, Long.class);
        long count = total == null ? 0 : total;

        String dataSql = BASE + """
                where exists (select 1 from user_roles r where r.user_id = u.id and r.role = 'MANAGER')
                order by p.display_name limit ? offset ?""";
        List<StaffMember> items = jdbc.query(dataSql, MAP, safeSize, safePage * safeSize);
        return PageResult.of(items, safePage, safeSize, count);
    }

    @Transactional(readOnly = true)
    public List<StaffMember> managers() {
        return managers(0, 1000).items();
    }

    /** @throws InvalidAdminActionException không tìm thấy, đã là Manager, hoặc tài khoản không hoạt động */
    @Transactional
    public void grantManager(UUID adminId, String username) {
        User user = users.findByUsername(username == null ? "" : username.strip().toLowerCase())
                .orElseThrow(() -> new InvalidAdminActionException("Không tìm thấy người dùng này."));
        if (!user.isActive()) {
            throw new InvalidAdminActionException("Tài khoản này không hoạt động.");
        }
        if (user.hasRole(Role.MANAGER)) {
            throw new InvalidAdminActionException("Người này đã là Manager rồi.");
        }
        user.getRoles().add(Role.MANAGER);
        users.save(user);
        // Phiên cũ mang quyền cũ: buộc đăng nhập lại để quyền mới được cấp trong token
        refreshTokens.revokeAll(user.getId());
        audit.log(adminId, "MANAGER_GRANTED", "USER", user.getId(), user.getUsername());
    }

    /** @throws InvalidAdminActionException không phải Manager hoặc là Admin */
    @Transactional
    public void revokeManager(UUID adminId, UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> new InvalidAdminActionException("Không tìm thấy người dùng này."));
        if (!user.hasRole(Role.MANAGER) || user.hasRole(Role.ADMIN)) {
            throw new InvalidAdminActionException("Người này không phải Manager.");
        }
        user.getRoles().remove(Role.MANAGER);
        users.save(user);
        refreshTokens.revokeAll(user.getId());
        audit.log(adminId, "MANAGER_REVOKED", "USER", user.getId(), user.getUsername());
    }

    @Transactional
    public void suspend(UUID adminId, UUID userId, String reason) {
        User user = requireTarget(adminId, userId);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidAdminActionException("Tài khoản này không đang hoạt động.");
        }
        user.setStatus(UserStatus.SUSPENDED);
        users.save(user);
        refreshTokens.revokeAll(userId);
        audit.log(adminId, "USER_SUSPENDED", "USER", userId, reason);
    }

    @Transactional
    public void unsuspend(UUID adminId, UUID userId) {
        User user = requireTarget(adminId, userId);
        if (user.getStatus() != UserStatus.SUSPENDED) {
            throw new InvalidAdminActionException("Tài khoản này không đang bị khóa.");
        }
        user.setStatus(UserStatus.ACTIVE);
        users.save(user);
        jdbc.update("update sanctions set lifted_at = ?, lifted_by = ? where user_id = ? and type = 'SUSPENSION' and lifted_at is null",
                Timestamp.from(clock.instant()), adminId, userId);
        audit.log(adminId, "USER_UNSUSPENDED", "USER", userId, null);
    }

    /**
     * Xóa vĩnh viễn = đánh dấu DELETED (không đăng nhập được, biến khỏi mọi danh sách) và ẩn toàn bộ bài viết,
     * bình luận của họ. Dữ liệu vẫn nằm trong CSDL để phục vụ điều tra; không có đường quay lại từ giao diện.
     */
    @Transactional
    public void deletePermanently(UUID adminId, UUID userId, String reason) {
        User user = requireTarget(adminId, userId);
        if (user.getStatus() == UserStatus.DELETED) {
            throw new InvalidAdminActionException("Tài khoản này đã bị xóa rồi.");
        }
        Timestamp now = Timestamp.from(clock.instant());
        user.setStatus(UserStatus.DELETED);
        user.getRoles().remove(Role.MANAGER);
        user.getRoles().remove(Role.CREATOR);
        users.save(user);
        refreshTokens.revokeAll(userId);
        jdbc.update("update posts set deleted_at = ? where author_id = ? and deleted_at is null", now, userId);
        jdbc.update("update comments set deleted_at = ? where author_id = ? and deleted_at is null", now, userId);
        audit.log(adminId, "USER_DELETED", "USER", userId, reason);
    }

    /** Số liệu tổng thể của hệ thống theo khoảng thời gian (phát sinh trong kỳ và tích lũy). */
    @Transactional(readOnly = true)
    public OverviewData overview(String period, LocalDate customFrom, LocalDate customTo) {
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate today = LocalDate.now(clock.withZone(zone));
        String selectedPeriod = (period == null || period.isBlank()) ? "7_DAYS" : period.toUpperCase();
        LocalDate from;
        LocalDate to = today;
        String label;

        switch (selectedPeriod) {
            case "TODAY" -> {
                from = today;
                label = "Hôm nay (" + today + ")";
            }
            case "30_DAYS" -> {
                from = today.minusDays(29);
                label = "30 ngày qua (" + from + " đến " + to + ")";
            }
            case "THIS_MONTH" -> {
                from = today.withDayOfMonth(1);
                label = "Tháng này (" + from + " đến " + to + ")";
            }
            case "CUSTOM" -> {
                from = customFrom != null ? customFrom : today.minusDays(6);
                to = customTo != null ? customTo : today;
                label = "Tùy chọn (" + from + " đến " + to + ")";
            }
            case "ALL" -> {
                from = null;
                to = null;
                label = "Toàn thời gian";
            }
            default -> { // 7_DAYS
                selectedPeriod = "7_DAYS";
                from = today.minusDays(6);
                label = "7 ngày qua (" + from + " đến " + to + ")";
            }
        }

        Instant startInstant = from != null ? from.atStartOfDay(zone).toInstant() : null;
        Instant endInstant = to != null ? to.atTime(LocalTime.MAX).atZone(zone).toInstant() : null;

        Map<String, Long> periodStats = new LinkedHashMap<>();
        periodStats.put("Đăng ký mới", countRange("users", "created_at", startInstant, endInstant, "status <> 'DELETED'"));
        periodStats.put("Bài viết mới", countRange("posts", "created_at", startInstant, endInstant, "deleted_at is null and scheduled_at is null"));
        periodStats.put("Bình luận mới", countRange("comments", "created_at", startInstant, endInstant, "deleted_at is null"));
        periodStats.put("Tin nhắn đã gửi", countRange("messages", "created_at", startInstant, endInstant, null));
        periodStats.put("Báo cáo phát sinh", countRange("reports", "created_at", startInstant, endInstant, null));
        periodStats.put("Quyết định xử phạt", countRange("sanctions", "created_at", startInstant, endInstant, null));

        Map<String, Long> systemStats = new LinkedHashMap<>();
        systemStats.put("Tổng người dùng", count("select count(*) from users where status <> 'DELETED'"));
        systemStats.put("Creator", count("select count(*) from user_roles where role = 'CREATOR'"));
        systemStats.put("Manager", count("select count(*) from user_roles where role = 'MANAGER'"));
        systemStats.put("Đang bị khóa", count("select count(*) from users where status = 'SUSPENDED'"));
        systemStats.put("Bài viết hiện có", count("select count(*) from posts where deleted_at is null and scheduled_at is null"));
        systemStats.put("Bình luận hiện có", count("select count(*) from comments where deleted_at is null"));
        systemStats.put("Xu đang lưu hành", count("select coalesce(sum(balance), 0) from wallets"));

        return new OverviewData(selectedPeriod, label, from, to, periodStats, systemStats);
    }

    /** Tương thích ngược với các kiểm thử hiện tại. */
    @Transactional(readOnly = true)
    public Map<String, Long> overview() {
        var data = overview("7_DAYS", null, null);
        Map<String, Long> legacy = new LinkedHashMap<>(data.systemStats());
        legacy.put("Đăng ký 7 ngày qua", data.periodStats().get("Đăng ký mới"));
        legacy.put("Bài viết", data.systemStats().get("Bài viết hiện có"));
        legacy.put("Bài mới 7 ngày qua", data.periodStats().get("Bài viết mới"));
        legacy.put("Bình luận", data.systemStats().get("Bình luận hiện có"));
        legacy.put("Tin nhắn", data.periodStats().get("Tin nhắn đã gửi"));
        return legacy;
    }

    private long countRange(String table, String timeCol, Instant start, Instant end, String extraCondition) {
        StringBuilder sql = new StringBuilder("select count(*) from ").append(table).append(" where 1=1 ");
        List<Object> args = new ArrayList<>();
        if (extraCondition != null && !extraCondition.isBlank()) {
            sql.append("and ").append(extraCondition).append(" ");
        }
        if (start != null) {
            sql.append("and ").append(timeCol).append(" >= ? ");
            args.add(Timestamp.from(start));
        }
        if (end != null) {
            sql.append("and ").append(timeCol).append(" <= ? ");
            args.add(Timestamp.from(end));
        }
        Long n = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    // ---------- Nội bộ ----------

    private User requireTarget(UUID adminId, UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> new InvalidAdminActionException("Không tìm thấy người dùng này."));
        if (userId.equals(adminId)) {
            throw new InvalidAdminActionException("Bạn không thể thao tác lên chính mình.");
        }
        if (user.hasRole(Role.ADMIN)) {
            throw new InvalidAdminActionException("Không thể thao tác lên tài khoản Admin.");
        }
        return user;
    }

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    private static final String BASE = """
            select u.id, u.username, p.display_name, u.email, u.status,
                   coalesce((select string_agg(r.role, ',' order by r.role) from user_roles r where r.user_id = u.id), '') as roles
            from users u join profiles p on p.user_id = u.id
            """;

    private static final org.springframework.jdbc.core.RowMapper<StaffMember> MAP = (rs, i) -> new StaffMember(
            rs.getObject("id", UUID.class), rs.getString("username"), rs.getString("display_name"), rs.getString("email"),
            rs.getString("roles"), UserStatus.valueOf(rs.getString("status")));
}
