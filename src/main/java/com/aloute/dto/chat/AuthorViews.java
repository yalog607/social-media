package com.aloute.dto.chat;

import com.aloute.dto.post.PostView;
import com.aloute.model.user.User;

/** Dựng {@link PostView.AuthorView} từ {@link User}, dùng chung cho người gửi/thành viên hội thoại. */
public final class AuthorViews {

    private AuthorViews() {
    }

    public static PostView.AuthorView of(User user) {
        return new PostView.AuthorView(user.getId(), user.getUsername(), user.getProfile().getDisplayName(),
                user.getProfile().getAvatarUrl(), user.primaryRole());
    }
}
