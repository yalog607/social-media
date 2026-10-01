package com.aloute.audit;

import java.time.Instant;
import java.util.UUID;

/** Một dòng nhật ký để hiển thị; {@code actorName} null nếu người thực hiện đã bị xóa khỏi hệ thống. */
public record AuditEntry(Instant createdAt, String action, String targetType, UUID targetId, String detail,
                         String actorName, String actorUsername) {
}
