package com.aloute.dto.story;

import java.util.List;

public record StoryTrayView(
        StoryGroupView myGroup,
        List<StoryGroupView> friendGroups
) {
    public boolean hasAnyFriendsWithStories() {
        return friendGroups != null && !friendGroups.isEmpty();
    }
}

