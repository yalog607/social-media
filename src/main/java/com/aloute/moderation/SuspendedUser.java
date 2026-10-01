package com.aloute.moderation;

import java.time.Instant;
import java.util.UUID;

/** Người đang bị khóa, kèm lý do và thời điểm của lần khóa gần nhất. */
public record SuspendedUser(UUID userId, String username, String displayName, String reason, Instant since) {
}
