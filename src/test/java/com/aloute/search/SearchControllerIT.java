package com.aloute.search;

import com.aloute.service.post.PostService;
import com.aloute.support.IntegrationTest;
import com.aloute.model.user.User;
import com.aloute.model.user.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phần 2c: trang tìm kiếm và trang hashtag qua HTTP. Bắt buộc đăng nhập, khách không dùng được. */
class SearchControllerIT extends IntegrationTest {

    @Autowired PostService posts;

    @Test
    void findsPublicPosts() throws Exception {
        User author = createUser();
        posts.create(author.getId(), "Cà phê sữa đá buổi sáng", Visibility.PUBLIC, List.of(), null);

        mvc.perform(get("/search").param("q", "ca phe sua da").param("tab", "posts").with(asUser(createUser())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cà phê sữa đá")));
    }

    @Test
    void guestSearchingIsSentToLogin() throws Exception {
        mvc.perform(get("/search").param("q", "gi bat ky"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void searchWithoutQueryShowsHintNotResults() throws Exception {
        mvc.perform(get("/search").with(asUser(createUser())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tìm gì đó đi")));
    }

    @Test
    void tooShortQueryShowsHint() throws Exception {
        mvc.perform(get("/search").param("q", "a").with(asUser(createUser())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ít nhất 2 ký tự")));
    }

    @Test
    void typingAHashtagRedirectsToTheTagPage() throws Exception {
        mvc.perform(get("/search").param("q", "#Học_Tập").with(asUser(createUser())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tags/hoc_tap"));
    }

    @Test
    void usersTabListsMatchingActiveUsers() throws Exception {
        User user = createUser();

        mvc.perform(get("/search").param("q", user.getUsername()).param("tab", "users").with(asUser(createUser())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(user.getUsername())));
    }

    @Test
    void hashtagsTabListsMatchingTags() throws Exception {
        posts.create(createUser().getId(), "#reactjs học react", Visibility.PUBLIC, List.of(), null);

        mvc.perform(get("/search").param("q", "react").param("tab", "hashtags").with(asUser(createUser())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("#reactjs")));
    }

    @Test
    void browsesAHashtagPage() throws Exception {
        posts.create(createUser().getId(), "sáng nay có #cafe ngon", Visibility.PUBLIC, List.of(), null);

        mvc.perform(get("/tags/cafe").with(asUser(createUser())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("sáng nay có")))
                .andExpect(content().string(containsString("href=\"/tags/cafe\"")));
    }

    @Test
    void guestBrowsingAHashtagPageIsSentToLogin() throws Exception {
        mvc.perform(get("/tags/cafe")).andExpect(status().is3xxRedirection());
    }

    @Test
    void hashtagPageOfAnUnusedTagShowsEmptyState() throws Exception {
        mvc.perform(get("/tags/khongtontai12345").with(asUser(createUser())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chưa có bài công khai")));
    }

    @Test
    void hashtagFragmentEndpointHasNoPageChrome() throws Exception {
        posts.create(createUser().getId(), "#feed test", Visibility.PUBLIC, List.of(), null);

        mvc.perform(get("/tags/feed/posts").with(asUser(createUser())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("<nav"))));
    }
}
