package com.aloute.dto.social;

import com.aloute.dto.post.PostView;
import com.aloute.model.user.User;

/** Dựng {@link PostView.AuthorView} từ {@link User}, dùng chung cho danh sách bạn bè/theo dõi/chặn. */
public final class AuthorViews {

    private AuthorViews() {
    }

    public static PostView.AuthorView of(User user) {
        return new PostView.AuthorView(user.getId(), user.getUsername(), user.getProfile().getDisplayName(),
                user.getProfile().getAvatarUrl(), user.primaryRole());
    }
}
