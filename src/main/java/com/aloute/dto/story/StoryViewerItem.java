package com.aloute.dto.story;

import java.time.Instant;
import java.util.UUID;

public record StoryViewerItem(
        UUID viewerId,
        String displayName,
        String username,
        String avatarUrl,
        Instant viewedAt,
        String timeAgo,
        String reaction
) {
    public StoryViewerItem(
            UUID viewerId,
            String displayName,
            String username,
            String avatarUrl,
            Instant viewedAt,
            String timeAgo
    ) {
        this(viewerId, displayName, username, avatarUrl, viewedAt, timeAgo, null);
    }
}

