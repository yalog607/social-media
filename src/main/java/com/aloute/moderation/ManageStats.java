package com.aloute.moderation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/** Thống kê cho Manager: khối lượng công việc và xu hướng vi phạm. */
@Service
public class ManageStats {

    private final JdbcTemplate jdbc;

    public ManageStats(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Nhãn hiển thị → số liệu, theo thứ tự hiển thị. */
    @Transactional(readOnly = true)
    public Map<String, Long> overview() {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("Báo cáo đang chờ", count("select count(*) from reports where status = 'OPEN'"));
        stats.put("Báo cáo 7 ngày qua", count("select count(*) from reports where created_at > now() - interval '7 days'"));
        stats.put("Đã xử lý 7 ngày qua", count("select count(*) from reports where status <> 'OPEN' and handled_at > now() - interval '7 days'"));
        stats.put("Phiếu hỗ trợ đang chờ", count("select count(*) from support_tickets where status = 'OPEN'"));
        stats.put("Cảnh cáo (tổng)", count("select count(*) from sanctions where type = 'WARNING'"));
        stats.put("Tài khoản đang bị khóa", count("select count(*) from users where status = 'SUSPENDED'"));
        stats.put("Hashtag bị cấm", count("select count(*) from banned_hashtags"));
        return stats;
    }

    /** Số báo cáo trong 30 ngày qua theo lý do, nhiều nhất trước. */
    @Transactional(readOnly = true)
    public Map<String, Long> reasons() {
        Map<String, Long> result = new LinkedHashMap<>();
        jdbc.query("""
                select reason, count(*) c from reports where created_at > now() - interval '30 days'
                group by reason order by c desc, reason""", rs -> {
            result.put(rs.getString(1), rs.getLong(2));
        });
        return result;
    }

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }
}
