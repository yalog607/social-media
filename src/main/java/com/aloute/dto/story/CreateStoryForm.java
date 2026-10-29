package com.aloute.dto.story;

import com.aloute.model.user.Visibility;
import org.springframework.web.multipart.MultipartFile;

public record CreateStoryForm(
        MultipartFile mediaFile,
        String caption,
        String backgroundColor,
        Visibility visibility,
        String storyType,
        String emoji
) {
    public CreateStoryForm(MultipartFile mediaFile, String caption, String backgroundColor, Visibility visibility, String storyType) {
        this(mediaFile, caption, backgroundColor, visibility, storyType, null);
    }
}

