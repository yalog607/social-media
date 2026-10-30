package com.aloute.user;

import com.aloute.model.user.Role;
import com.aloute.model.user.User;
import com.aloute.model.user.Visibility;
import com.aloute.service.category.CategoryService;
import com.aloute.service.creator.PollService;
import com.aloute.service.creator.QuestionService;
import com.aloute.service.creator.SeriesService;
import com.aloute.service.creator.TopicService;
import com.aloute.service.post.PostService;
import com.aloute.service.social.BlockService;
import com.aloute.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Trang hồ sơ Creator: khối chủ đề, tab Series và tab Hỏi đáp & Poll, theo đúng quyền xem và chặn. */
class CreatorProfilePageIT extends IntegrationTest {

    @Autowired TopicService topics;
    @Autowired SeriesService series;
    @Autowired PollService polls;
    @Autowired QuestionService questions;
    @Autowired PostService postService;
    @Autowired CategoryService categories;
    @Autowired BlockService blocks;

    private static String shortSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void creatorProfileShowsTopicsSeriesAndQa() throws Exception {
        User creator = createUser(Role.CREATOR);
        User viewer = createUser();
        UUID topic = categories.create(createUser(Role.MANAGER).getId(), "Nau an " + shortSuffix()).id();
        topics.replace(creator.getId(), List.of(topic));
        UUID seriesId = series.create(creator.getId(), "Series cong khai", "");
        var post = postService.create(creator.getId(), "bai trong series", Visibility.PUBLIC, null, null);
        series.addPost(creator.getId(), seriesId, post.getId());
        polls.create(creator.getId(), "Thich gi?", List.of("A", "B"), null);
        questions.create(creator.getId(), "Hoi gi day?");

        mvc.perform(get("/u/" + creator.getUsername()).with(asUser(viewer)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Series (")))
                .andExpect(content().string(containsString("Hỏi đáp")));

        mvc.perform(get("/u/" + creator.getUsername()).with(asUser(viewer)).param("tab", "series"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Series cong khai")));

        mvc.perform(get("/u/" + creator.getUsername()).with(asUser(viewer)).param("tab", "qa"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Thich gi?")))
                .andExpect(content().string(containsString("Hoi gi day?")));
    }

    @Test
    void regularUserProfileHasNoCreatorTabs() throws Exception {
        User user = createUser();
        User viewer = createUser();

        mvc.perform(get("/u/" + user.getUsername()).with(asUser(viewer)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("tab=qa"))))
                .andExpect(content().string(not(containsString("tab=series"))));
    }

    @Test
    void blockedViewerSeesNoCreatorContent() throws Exception {
        User creator = createUser(Role.CREATOR);
        User blockedViewer = createUser();
        UUID topic = categories.create(createUser(Role.MANAGER).getId(), "Du lich " + shortSuffix()).id();
        topics.replace(creator.getId(), List.of(topic));
        UUID seriesId = series.create(creator.getId(), "Series bi chan xem", "");
        var post = postService.create(creator.getId(), "bai", Visibility.PUBLIC, null, null);
        series.addPost(creator.getId(), seriesId, post.getId());
        blocks.block(creator.getId(), blockedViewer.getId());

        mvc.perform(get("/u/" + creator.getUsername()).with(asUser(blockedViewer)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Series bi chan xem"))));
    }
}
