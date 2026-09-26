package com.aloute.post;

import com.aloute.support.IntegrationTest;
import com.aloute.support.TestMedia;
import com.aloute.user.User;
import com.aloute.user.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phần 2a, task 8: đăng/xem/sửa/xóa bài và các trang bảng tin qua HTTP. */
class PostWebIT extends IntegrationTest {

    @Autowired PostService postService;
    @Autowired PostRepository postRepository;

    private Post publicPost(User author, String text) {
        return postService.create(author.getId(), text, Visibility.PUBLIC, null, null);
    }

    @Test
    void createsATextPost() throws Exception {
        User user = createUser();

        mvc.perform(multipart("/posts").with(csrf()).with(asUser(user)).param("content", "Xin chào #ALOUTE").param("visibility", "PUBLIC"))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("notice", "Đã đăng bài!"));

        assertThat(postRepository.findAll())
                .anyMatch(p -> p.getAuthor().getId().equals(user.getId()) && p.getContent().equals("Xin chào #ALOUTE"));
    }

    @Test
    void createsAPostWithAnImage() throws Exception {
        User user = createUser();
        MockMultipartFile image = new MockMultipartFile("images", "a.jpg", "image/jpeg", TestMedia.jpeg(80, 60));

        mvc.perform(multipart("/posts").file(image).with(csrf()).with(asUser(user)).param("content", "").param("visibility", "PUBLIC"))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("notice", "Đã đăng bài!"));
    }

    @Test
    void createsAPostWithAVideo() throws Exception {
        User user = createUser();
        MockMultipartFile video = new MockMultipartFile("video", "v.mp4", "video/mp4", TestMedia.mp4(2048));

        mvc.perform(multipart("/posts").file(video).with(csrf()).with(asUser(user)).param("content", "clip").param("visibility", "PUBLIC"))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("notice", "Đã đăng bài!"));
    }

    @Test
    void invalidPostKeepsWhatTheUserTyped() throws Exception {
        mvc.perform(multipart("/posts").with(csrf()).with(asUser(createUser())).param("content", "   ").param("visibility", "PUBLIC"))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("composerError"))
                .andExpect(flash().attribute("composerVisibility", "PUBLIC"));
    }

    @Test
    void rejectsPostWithoutCsrfToken() throws Exception {
        mvc.perform(multipart("/posts").with(asUser(createUser())).param("content", "x"))
                .andExpect(status().isForbidden());
    }

    @Test
    void guestPostingIsSentToLogin() throws Exception {
        mvc.perform(multipart("/posts").with(csrf()).param("content", "x"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void detailShowsPublicPostToGuests() throws Exception {
        Post post = publicPost(createUser(), "Bài công khai #vui");

        mvc.perform(get("/posts/" + post.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bài công khai")))
                .andExpect(content().string(containsString("class=\"hashtag\"")));
    }

    @Test
    void detailOfSomeoneElsesPrivatePostIs404ButOwnerSeesIt() throws Exception {
        User owner = createUser();
        Post secret = postService.create(owner.getId(), "riêng tư", Visibility.PRIVATE, null, null);

        mvc.perform(get("/posts/" + secret.getId()).with(asUser(createUser()))).andExpect(status().isNotFound());
        mvc.perform(get("/posts/" + secret.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/posts/" + secret.getId()).with(asUser(owner))).andExpect(status().isOk());
    }

    @Test
    void scriptTagsInPostsAreEscaped() throws Exception {
        Post post = publicPost(createUser(), "<script>alert(1)</script>");

        mvc.perform(get("/posts/" + post.getId()))
                .andExpect(content().string(not(containsString("<script>alert(1)"))))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")));
    }

    @Test
    void onlyTheOwnerCanEditAndDelete() throws Exception {
        User owner = createUser();
        Post post = publicPost(owner, "gốc");
        User stranger = createUser();

        mvc.perform(post("/posts/" + post.getId() + "/edit").with(csrf()).with(asUser(stranger)).param("content", "hack").param("visibility", "PUBLIC"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/posts/" + post.getId() + "/delete").with(csrf()).with(asUser(stranger)))
                .andExpect(status().isNotFound());
        assertThat(postRepository.findById(post.getId()).orElseThrow().getContent()).isEqualTo("gốc");

        mvc.perform(post("/posts/" + post.getId() + "/edit").with(csrf()).with(asUser(owner))
                        .param("content", "đã sửa").param("visibility", "PUBLIC").param("next", "/"))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("notice", "Đã lưu chỉnh sửa!"));
        assertThat(postRepository.findById(post.getId()).orElseThrow().getContent()).isEqualTo("đã sửa");

        mvc.perform(post("/posts/" + post.getId() + "/delete").with(csrf()).with(asUser(owner)).param("next", "/posts/" + post.getId()))
                .andExpect(redirectedUrl("/"));
        mvc.perform(get("/posts/" + post.getId())).andExpect(status().isNotFound());
    }

    @Test
    void feedFragmentHasNoPageChrome() throws Exception {
        User user = createUser();
        publicPost(user, "mảnh HTML");

        MvcResult result = mvc.perform(get("/feed").with(asUser(user))).andExpect(status().isOk()).andReturn();
        String html = result.getResponse().getContentAsString();

        assertThat(html).doesNotContain("<html").doesNotContain("<nav");
        assertThat(html).contains("class=\"post\"");
    }

    @Test
    void profilePostsFragmentShowsOnlyPublicPostsToGuests() throws Exception {
        User owner = createUser();
        publicPost(owner, "thấy được");
        postService.create(owner.getId(), "ẩn với khách", Visibility.PRIVATE, null, null);

        mvc.perform(get("/u/" + owner.getUsername() + "/posts"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("thấy được")))
                .andExpect(content().string(not(containsString("ẩn với khách"))))
                .andExpect(content().string(not(containsString("Xem thêm"))));
    }

    @Test
    void eleventhPostInTenMinutesIsRateLimited() throws Exception {
        User user = createUser();
        for (int i = 0; i < 10; i++) {
            mvc.perform(multipart("/posts").with(csrf()).with(asUser(user)).param("content", "bài " + i).param("visibility", "PUBLIC"))
                    .andExpect(flash().attribute("notice", "Đã đăng bài!"));
        }

        mvc.perform(multipart("/posts").with(csrf()).with(asUser(user)).param("content", "bài 11").param("visibility", "PUBLIC"))
                .andExpect(flash().attributeExists("composerError"));
    }
}
