package com.aloute.dto.group;

import java.time.Instant;
import java.util.UUID;

public record GroupRequestView(
        UUID userId,
        String username,
        String displayName,
        String avatarUrl,
        Instant createdAt,
        String answer
) {
}
