package com.aloute.dto.story;

import com.aloute.dto.post.PostView.AuthorView;

import java.time.Instant;
import java.util.List;

public record StoryGroupView(
        AuthorView author,
        List<StoryItemView> stories,
        boolean allSeen,
        Instant latestCreatedAt,
        boolean isMe,
        String vibeEmoji,
        String vibeText,
        String vibeColor
) {
    public StoryGroupView(AuthorView author, List<StoryItemView> stories, boolean allSeen, Instant latestCreatedAt, boolean isMe) {
        this(author, stories, allSeen, latestCreatedAt, isMe, null, null, null);
    }

    public boolean hasStories() {
        return stories != null && !stories.isEmpty();
    }

    public boolean hasVibe() {
        return vibeEmoji != null && !vibeEmoji.isBlank();
    }

    public String vibeTooltip() {
        if (!hasVibe()) return "";
        return (vibeText != null && !vibeText.isBlank()) ? vibeEmoji + " " + vibeText : vibeEmoji;
    }
}

