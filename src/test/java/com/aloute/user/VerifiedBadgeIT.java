package com.aloute.user;

import com.aloute.model.user.Role;
import com.aloute.model.user.User;
import com.aloute.model.user.Visibility;

import com.aloute.service.comment.CommentService;
import com.aloute.model.post.Post;
import com.aloute.service.post.PostService;
import com.aloute.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tích vàng "Creator đã xác minh": chỉ hiện cạnh tên Creator, ở bài viết, bình luận, trang cá nhân và tìm kiếm. */
class VerifiedBadgeIT extends IntegrationTest {

    private static final String BADGE = "class=\"verified-badge\"";

    @Autowired PostService posts;
    @Autowired CommentService comments;

    @Test
    void creatorGetsTheBadgeOnPostsCommentsProfileAndSearch() throws Exception {
        User creator = createUser(Role.CREATOR);
        User viewer = createUser();
        Post post = posts.create(creator.getId(), "bài của creator", Visibility.PUBLIC, List.of(), null);
        comments.create(creator.getId(), post.getId(), null, "creator tự bình luận");

        mvc.perform(get("/posts/" + post.getId()).with(asUser(viewer)))
                .andExpect(status().isOk()).andExpect(content().string(containsString(BADGE)));
        mvc.perform(get("/api/posts/" + post.getId() + "/comments").with(asUser(viewer)))
                .andExpect(status().isOk()).andExpect(content().string(containsString(BADGE)));
        mvc.perform(get("/u/" + creator.getUsername()).with(asUser(viewer)))
                .andExpect(status().isOk()).andExpect(content().string(containsString(BADGE)))
                .andExpect(content().string(containsString("Creator đã xác minh")));
        mvc.perform(get("/search").param("q", creator.getProfile().getDisplayName()).param("tab", "users").with(asUser(viewer)))
                .andExpect(status().isOk()).andExpect(content().string(containsString(BADGE)));
    }

    @Test
    void regularUsersAndStaffDoNotGetTheBadge() throws Exception {
        User viewer = createUser();
        for (User other : List.of(createUser(), createUser(Role.MANAGER), createUser(Role.ADMIN))) {
            Post post = posts.create(other.getId(), "bài thường", Visibility.PUBLIC, List.of(), null);
            comments.create(other.getId(), post.getId(), null, "bình luận");
            mvc.perform(get("/posts/" + post.getId()).with(asUser(viewer)))
                    .andExpect(status().isOk()).andExpect(content().string(not(containsString(BADGE))));
            mvc.perform(get("/u/" + other.getUsername()).with(asUser(viewer)))
                    .andExpect(status().isOk()).andExpect(content().string(not(containsString(BADGE))));
        }
    }
}
