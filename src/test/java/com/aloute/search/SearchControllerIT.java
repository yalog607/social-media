package com.aloute.search;

import com.aloute.post.PostService;
import com.aloute.support.IntegrationTest;
import com.aloute.user.User;
import com.aloute.user.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phần 2c: trang tìm kiếm và trang hashtag qua HTTP. Khách dùng được vì chỉ thấy nội dung công khai. */
class SearchControllerIT extends IntegrationTest {

    @Autowired PostService posts;

    @Test
    void guestCanSearchPosts() throws Exception {
        User author = createUser();
        posts.create(author.getId(), "Cà phê sữa đá buổi sáng", Visibility.PUBLIC, List.of(), null);

        mvc.perform(get("/search").param("q", "ca phe sua da").param("tab", "posts"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cà phê sữa đá")));
    }

    @Test
    void searchWithoutQueryShowsHintNotResults() throws Exception {
        mvc.perform(get("/search"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tìm gì đó đi")));
    }

    @Test
    void tooShortQueryShowsHint() throws Exception {
        mvc.perform(get("/search").param("q", "a"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ít nhất 2 ký tự")));
    }

    @Test
    void typingAHashtagRedirectsToTheTagPage() throws Exception {
        mvc.perform(get("/search").param("q", "#Học_Tập"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tags/hoc_tap"));
    }

    @Test
    void usersTabListsMatchingActiveUsers() throws Exception {
        User user = createUser();

        mvc.perform(get("/search").param("q", user.getUsername()).param("tab", "users"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(user.getUsername())));
    }

    @Test
    void hashtagsTabListsMatchingTags() throws Exception {
        posts.create(createUser().getId(), "#reactjs học react", Visibility.PUBLIC, List.of(), null);

        mvc.perform(get("/search").param("q", "react").param("tab", "hashtags"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("#reactjs")));
    }

    @Test
    void guestCanBrowseAHashtagPage() throws Exception {
        posts.create(createUser().getId(), "sáng nay có #cafe ngon", Visibility.PUBLIC, List.of(), null);

        mvc.perform(get("/tags/cafe"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("sáng nay có")))
                .andExpect(content().string(containsString("href=\"/tags/cafe\"")));
    }

    @Test
    void hashtagPageOfAnUnusedTagShowsEmptyState() throws Exception {
        mvc.perform(get("/tags/khongtontai12345"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chưa có bài công khai")));
    }

    @Test
    void hashtagFragmentEndpointHasNoPageChrome() throws Exception {
        posts.create(createUser().getId(), "#feed test", Visibility.PUBLIC, List.of(), null);

        mvc.perform(get("/tags/feed/posts"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("<nav"))));
    }
}
