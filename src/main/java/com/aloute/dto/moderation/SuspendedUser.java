package com.aloute.dto.moderation;

import java.time.Instant;
import java.util.UUID;

/** Người đang bị khóa, kèm lý do, thời điểm của lần khóa gần nhất và ID báo cáo gốc nếu có. */
public record SuspendedUser(UUID userId, String username, String displayName, String reason, Instant since, UUID reportId) {
    public SuspendedUser(UUID userId, String username, String displayName, String reason, Instant since) {
        this(userId, username, displayName, reason, since, null);
    }
}
