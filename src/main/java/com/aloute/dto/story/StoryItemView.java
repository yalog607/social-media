package com.aloute.dto.story;

import com.aloute.model.story.StoryMediaType;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StoryItemView(
        UUID id,
        UUID authorId,
        String authorDisplayName,
        String authorUsername,
        String authorAvatarUrl,
        StoryMediaType mediaType,
        String mediaUrl,
        String caption,
        String emoji,
        String backgroundColor,
        Instant createdAt,
        String timeAgo,
        boolean viewed,
        long viewCount,
        @JsonProperty("isOwner") boolean isOwner,
        List<String> reactions
) {
    public StoryItemView(
            UUID id,
            UUID authorId,
            String authorDisplayName,
            String authorUsername,
            String authorAvatarUrl,
            StoryMediaType mediaType,
            String mediaUrl,
            String caption,
            String emoji,
            String backgroundColor,
            Instant createdAt,
            String timeAgo,
            boolean viewed,
            long viewCount,
            boolean isOwner
    ) {
        this(id, authorId, authorDisplayName, authorUsername, authorAvatarUrl, mediaType, mediaUrl, caption, emoji, backgroundColor, createdAt, timeAgo, viewed, viewCount, isOwner, List.of());
    }

    public StoryItemView(
            UUID id,
            UUID authorId,
            String authorDisplayName,
            String authorUsername,
            String authorAvatarUrl,
            StoryMediaType mediaType,
            String mediaUrl,
            String caption,
            String backgroundColor,
            Instant createdAt,
            String timeAgo,
            boolean viewed,
            long viewCount,
            boolean isOwner
    ) {
        this(id, authorId, authorDisplayName, authorUsername, authorAvatarUrl, mediaType, mediaUrl, caption, null, backgroundColor, createdAt, timeAgo, viewed, viewCount, isOwner, List.of());
    }

    public boolean isVibe() {
        return mediaType == StoryMediaType.VIBE
                || (emoji != null && !emoji.isBlank() && mediaType == StoryMediaType.TEXT);
    }

    public boolean getIsVibe() {
        return isVibe();
    }
}

