package com.aloute.comment;

import com.aloute.post.Post;
import com.aloute.post.PostService;
import com.aloute.support.IntegrationTest;
import com.aloute.user.User;
import com.aloute.user.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phần 2b: thêm/xóa/xem bình luận qua HTTP. */
class CommentControllerIT extends IntegrationTest {

    @Autowired PostService posts;
    @Autowired CommentService comments;

    private Post publicPost(User author) {
        return posts.create(author.getId(), "bài của " + author.getUsername(), Visibility.PUBLIC, List.of(), null);
    }

    @Test
    void addsAComment() throws Exception {
        Post post = publicPost(createUser());

        mvc.perform(post("/api/posts/" + post.getId() + "/comments").param("content", "Hay ghê!")
                        .with(csrf()).with(asUser(createUser())))
                .andExpect(status().isOk());
    }

    @Test
    void guestCannotReadComments() throws Exception {
        Post post = publicPost(createUser());
        comments.create(createUser().getId(), post.getId(), null, "xin chào");

        mvc.perform(get("/api/posts/" + post.getId() + "/comments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void guestCannotComment() throws Exception {
        Post post = publicPost(createUser());

        mvc.perform(post("/api/posts/" + post.getId() + "/comments").param("content", "x").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authorCanDeleteOwnComment() throws Exception {
        Post post = publicPost(createUser());
        User author = createUser();
        Comment comment = comments.create(author.getId(), post.getId(), null, "gốc");

        mvc.perform(post("/api/comments/" + comment.getId() + "/delete").with(csrf()).with(asUser(author)))
                .andExpect(status().isOk());
    }

    @Test
    void aStrangerCannotDeleteSomeoneElsesComment() throws Exception {
        Post post = publicPost(createUser());
        Comment comment = comments.create(createUser().getId(), post.getId(), null, "gốc");

        mvc.perform(post("/api/comments/" + comment.getId() + "/delete").with(csrf()).with(asUser(createUser())))
                .andExpect(status().isForbidden());
    }

    @Test
    void readingCommentsOfAPrivatePostOfSomeoneElseIs404() throws Exception {
        User owner = createUser();
        Post privatePost = posts.create(owner.getId(), "riêng tư", Visibility.PRIVATE, List.of(), null);

        mvc.perform(get("/api/posts/" + privatePost.getId() + "/comments").with(asUser(createUser())))
                .andExpect(status().isNotFound());
    }
}
