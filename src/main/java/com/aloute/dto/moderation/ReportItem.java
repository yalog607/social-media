package com.aloute.dto.moderation;

import java.time.Instant;
import java.util.UUID;

/**
 * Một báo cáo đang chờ trong hàng đợi. {@code preview} là chữ của bài/bình luận (hoặc tên người bị báo cáo);
 * {@code contentRemoved} nghĩa là nội dung đã bị xóa trước đó nên không cần xóa nữa.
 */
public record ReportItem(UUID id, String targetType, UUID targetId, String reason, String detail, Instant createdAt,
                         String reporterName, String preview, boolean contentRemoved, UUID ownerId, String ownerName) {

    public boolean canRemoveContent() {
        return !"USER".equals(targetType) && !contentRemoved;
    }
}
