package com.aloute.admin;

import com.aloute.audit.AuditService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

/** Cấu hình hệ thống do Admin chỉnh (lưu CSDL nên có hiệu lực ngay, không cần khởi động lại). Mọi thay đổi được ghi nhật ký. */
@Service
public class SystemSettings {

    public static final String REGISTRATION_OPEN = "registration_open";

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final Clock clock;

    public SystemSettings(JdbcTemplate jdbc, AuditService audit, Clock clock) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean registrationOpen() {
        var values = jdbc.queryForList("select value from system_settings where key = ?", String.class, REGISTRATION_OPEN);
        return values.isEmpty() || Boolean.parseBoolean(values.get(0));
    }

    @Transactional
    public void setRegistrationOpen(UUID adminId, boolean open) {
        if (registrationOpen() == open) {
            return;
        }
        jdbc.update("""
                insert into system_settings (key, value, updated_by, updated_at) values (?, ?, ?, ?)
                on conflict (key) do update set value = excluded.value, updated_by = excluded.updated_by,
                                                updated_at = excluded.updated_at""",
                REGISTRATION_OPEN, Boolean.toString(open), adminId, Timestamp.from(clock.instant()));
        audit.log(adminId, "SETTING_CHANGED", "SETTING", null, REGISTRATION_OPEN + "=" + open);
    }
}
