package com.aloute.dto.moderation;

import java.time.Instant;
import java.util.UUID;

/** DTO cho danh sách báo cáo đã xử lý (Resolved / Dismissed) */
public record ResolvedReportItem(
        UUID id,
        String targetType,
        UUID targetId,
        String reason,
        String detail,
        Instant createdAt,
        String status,
        String action,
        String handlingNote,
        Instant handledAt,
        UUID handledBy,
        String handlerName,
        String reporterName,
        UUID targetOwnerId,
        String ownerName,
        String contentSnapshot,
        boolean contentRemoved
) {
}
