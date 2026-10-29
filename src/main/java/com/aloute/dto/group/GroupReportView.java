package com.aloute.dto.group;

import java.time.Instant;
import java.util.UUID;

public record GroupReportView(
        UUID id,
        UUID postId,
        String postSnippet,
        String reporterName,
        String reporterUsername,
        String reason,
        Instant createdAt
) {
}
