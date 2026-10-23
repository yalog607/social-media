package com.aloute.security;

import com.aloute.model.post.Post;
import com.aloute.service.post.PostService;
import com.aloute.support.IntegrationTest;
import com.aloute.model.user.User;
import com.aloute.model.user.Visibility;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hồi quy: POST thành công bằng fetch KHÔNG được xóa cookie XSRF-TOKEN. Trước đây Spring coi mọi request JWT là "xác thực
 * mới" và xóa cookie, nên POST fetch thứ hai liên tiếp (gửi tin, thả cảm xúc...) gửi header rỗng, bị 403 rồi mới thử lại.
 * <p>
 * Lớp này cố ý dùng một Spring context RIÊNG (thuộc tính {@code test.isolated-context} làm khóa context khác đi): bộ xử lý
 * {@code csrf()} của spring-security-test thay kho CSRF thật bằng kho giả trong bộ lọc dùng chung, nên trong context chung
 * với các test khác thì hành vi cookie thật không còn quan sát được.
 */
@TestPropertySource(properties = "test.isolated-context=csrf-cookie")
class CsrfCookieStabilityIT extends IntegrationTest {

    private static final String TOKEN = "11111111-2222-3333-4444-555555555555";

    @Autowired PostService posts;

    @Test
    void aSuccessfulPostDoesNotClearTheCsrfCookie() throws Exception {
        User author = createUser();
        User viewer = createUser();
        Post post = posts.create(author.getId(), "bài", Visibility.PUBLIC, List.of(), null);

        for (int i = 1; i <= 3; i++) {
            MvcResult result = mvc.perform(post("/api/posts/" + post.getId() + "/view").with(asUser(viewer))
                            .cookie(new Cookie("XSRF-TOKEN", TOKEN)).header("X-XSRF-TOKEN", TOKEN))
                    .andExpect(status().isNoContent()).andReturn();
            assertThat(result.getResponse().getHeaders("Set-Cookie"))
                    .as("lần %d: phản hồi không được xóa/đổi cookie CSRF", i)
                    .noneMatch(header -> header.startsWith("XSRF-TOKEN=") && header.contains("Max-Age=0"));
        }
    }

    @Test
    void aRequestWithoutTheTokenIsStillRejected() throws Exception {
        User viewer = createUser();
        Post post = posts.create(createUser().getId(), "bài", Visibility.PUBLIC, List.of(), null);

        mvc.perform(post("/api/posts/" + post.getId() + "/view").with(asUser(viewer))).andExpect(status().isForbidden());
        mvc.perform(post("/api/posts/" + post.getId() + "/view").with(asUser(viewer))
                        .cookie(new Cookie("XSRF-TOKEN", TOKEN)).header("X-XSRF-TOKEN", "sai-token"))
                .andExpect(status().isForbidden());
    }
}
