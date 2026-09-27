package com.aloute.reaction;

import com.aloute.post.Post;
import com.aloute.post.PostService;
import com.aloute.support.IntegrationTest;
import com.aloute.user.User;
import com.aloute.user.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phần 2b: đổi/bỏ cảm xúc qua HTTP. */
class ReactionControllerIT extends IntegrationTest {

    @Autowired PostService posts;

    private Post publicPost(User author) {
        return posts.create(author.getId(), "bài của " + author.getUsername(), Visibility.PUBLIC, List.of(), null);
    }

    @Test
    void reactsAndReturnsUpdatedCounts() throws Exception {
        Post post = publicPost(createUser());
        User viewer = createUser();

        mvc.perform(post("/api/posts/" + post.getId() + "/reaction").param("type", "LOVE").with(csrf()).with(asUser(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mine").value("LOVE"))
                .andExpect(jsonPath("$.counts.LOVE").value(1))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void guestCannotReact() throws Exception {
        Post post = publicPost(createUser());

        mvc.perform(post("/api/posts/" + post.getId() + "/reaction").param("type", "LOVE").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWithoutCsrfToken() throws Exception {
        Post post = publicPost(createUser());

        mvc.perform(post("/api/posts/" + post.getId() + "/reaction").param("type", "LOVE").with(asUser(createUser())))
                .andExpect(status().isForbidden());
    }

    @Test
    void reactingToAPrivatePostOfSomeoneElseIs404() throws Exception {
        User owner = createUser();
        Post privatePost = posts.create(owner.getId(), "riêng tư", Visibility.PRIVATE, List.of(), null);

        mvc.perform(post("/api/posts/" + privatePost.getId() + "/reaction").param("type", "LOVE").with(csrf()).with(asUser(createUser())))
                .andExpect(status().isNotFound());
    }
}
