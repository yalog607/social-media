package com.aloute.admin;

import com.aloute.audit.AuditService;
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

    /** Tìm theo username/email/tên hiển thị (rỗng thì trả những tài khoản mới nhất). */
    @Transactional(readOnly = true)
    public List<StaffMember> search(String query) {
        String like = "%" + (query == null ? "" : query.strip().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")) + "%";
        return jdbc.query(BASE + """
                where lower(u.username) like ? escape '\\' or lower(u.email) like ? escape '\\'
                   or lower(p.display_name) like ? escape '\\'
                order by u.created_at desc limit ?""", MAP, like, like, like, MAX_RESULTS);
    }

    @Transactional(readOnly = true)
    public List<StaffMember> managers() {
        return jdbc.query(BASE + """
                where exists (select 1 from user_roles r where r.user_id = u.id and r.role = 'MANAGER')
                order by p.display_name""", MAP);
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

    /** Số liệu tổng thể của hệ thống. */
    @Transactional(readOnly = true)
    public Map<String, Long> overview() {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("Tổng người dùng", count("select count(*) from users where status <> 'DELETED'"));
        stats.put("Đăng ký 7 ngày qua", count("select count(*) from users where created_at > now() - interval '7 days'"));
        stats.put("Creator", count("select count(*) from user_roles where role = 'CREATOR'"));
        stats.put("Manager", count("select count(*) from user_roles where role = 'MANAGER'"));
        stats.put("Đang bị khóa", count("select count(*) from users where status = 'SUSPENDED'"));
        stats.put("Bài viết", count("select count(*) from posts where deleted_at is null and scheduled_at is null"));
        stats.put("Bài mới 7 ngày qua", count("select count(*) from posts where deleted_at is null and scheduled_at is null and created_at > now() - interval '7 days'"));
        stats.put("Bình luận", count("select count(*) from comments where deleted_at is null"));
        stats.put("Tin nhắn", count("select count(*) from messages"));
        stats.put("Xu đang lưu hành", count("select coalesce(sum(balance), 0) from wallets"));
        return stats;
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
