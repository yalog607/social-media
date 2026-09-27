package com.aloute.social;

import com.aloute.post.PostView;
import com.aloute.user.User;

/** Dựng {@link PostView.AuthorView} từ {@link User}, dùng chung cho danh sách bạn bè/theo dõi/chặn. */
final class AuthorViews {

    private AuthorViews() {
    }

    static PostView.AuthorView of(User user) {
        return new PostView.AuthorView(user.getId(), user.getUsername(), user.getProfile().getDisplayName(),
                user.getProfile().getAvatarUrl(), user.primaryRole());
    }
}
