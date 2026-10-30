package com.aloute.seo;

import com.aloute.model.post.Post;
import com.aloute.model.user.User;
import com.aloute.model.user.Visibility;
import com.aloute.service.post.PostService;
import com.aloute.support.IntegrationTest;
import com.aloute.support.TestMedia;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Open Graph: khách và bot xem được thẻ OG, người thật chưa đăng nhập vẫn bị chuyển về đăng nhập. */
class OgMetaIT extends IntegrationTest {

    private static final String BOT = "facebookexternalhit/1.1";
    private static final String DEFAULT_IMAGE_TAG =
            "property=\"og:image\" content=\"http://localhost:8080/img/og-default.png\"";

    @Autowired PostService posts;

    @Test
    void landingAndLoginExposeSiteOg() throws Exception {
        for (String path : new String[]{"/", "/login"}) {
            mvc.perform(get(path)).andExpect(status().isOk())
                    .andExpect(content().string(containsString(DEFAULT_IMAGE_TAG)))
                    .andExpect(content().string(containsString("property=\"og:title\"")));
        }
    }

    @Test
    void botSeesPostImageWithoutLoginButHumanIsRedirected() throws Exception {
        Post post = posts.create(createUser().getId(), "bài có ảnh", Visibility.PUBLIC,
                List.of(TestMedia.image("a.jpg", TestMedia.jpeg(10, 10))), null);

        mvc.perform(get("/posts/" + post.getId()).header("User-Agent", BOT)).andExpect(status().isOk())
                .andExpect(content().string(containsString("property=\"og:image\" content=\"http://localhost:8080/uploads/")))
                .andExpect(content().string(containsString("bài có ảnh")));
        mvc.perform(get("/posts/" + post.getId())).andExpect(status().isFound());
        mvc.perform(get("/posts/" + post.getId()).header("User-Agent", "Mozilla/5.0 Chrome/120"))
                .andExpect(status().isFound());
    }

    @Test
    void textPostFallsBackToDefaultImageAndOtherAreasStayClosedToBots() throws Exception {
        Post post = posts.create(createUser().getId(), "chỉ chữ", Visibility.PUBLIC, List.of(), null);
        mvc.perform(get("/posts/" + post.getId()).header("User-Agent", BOT)).andExpect(status().isOk())
                .andExpect(content().string(containsString(DEFAULT_IMAGE_TAG)));
        mvc.perform(get("/settings").header("User-Agent", BOT)).andExpect(status().isFound());
    }

    @Test
    void botSeesProfileAndHashtagOg() throws Exception {
        User user = createUser();
        mvc.perform(get("/u/" + user.getUsername()).header("User-Agent", BOT)).andExpect(status().isOk())
                .andExpect(content().string(containsString("property=\"og:type\" content=\"profile\"")));
        mvc.perform(get("/tags/mon-an").header("User-Agent", BOT)).andExpect(status().isOk())
                .andExpect(content().string(containsString("property=\"og:title\" content=\"#mon-an trên ALOUTE\"")));
        mvc.perform(get("/u/" + user.getUsername())).andExpect(status().isFound());
    }

    @Test
    void botCannotReadNonPublicPost()throws Exception {
        Post post = posts.create(createUser().getId(), "riêng tư", Visibility.PRIVATE, List.of(), null);
        mvc.perform(get("/posts/" + post.getId()).header("User-Agent", BOT)).andExpect(status().isNotFound());
    }
}
