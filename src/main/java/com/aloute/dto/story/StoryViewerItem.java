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
        String reaction,
        String avatarFrame
) {
    public StoryViewerItem(UUID viewerId, String displayName, String username, String avatarUrl, Instant viewedAt, String timeAgo, String reaction) {
        this(viewerId, displayName, username, avatarUrl, viewedAt, timeAgo, reaction, null);
    }

    public StoryViewerItem(
            UUID viewerId,
            String displayName,
            String username,
            String avatarUrl,
            Instant viewedAt,
            String timeAgo
    ) {
        this(viewerId, displayName, username, avatarUrl, viewedAt, timeAgo, null, null);
    }
}

