package com.aloute.dto.group;

import com.aloute.model.chat.GroupRole;

import java.time.Instant;
import java.util.UUID;

public record GroupMemberView(
        UUID id,
        String username,
        String displayName,
        String avatarUrl,
        GroupRole role,
        Instant joinedAt,
        String avatarFrame
) {
}
