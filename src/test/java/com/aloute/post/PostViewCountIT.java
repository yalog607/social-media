package com.aloute.post;

import com.aloute.exception.post.PostNotFoundException;
import com.aloute.model.post.Post;
import com.aloute.service.post.PostService;
import com.aloute.service.post.PostViewService;

import com.aloute.service.creator.InsightsService;
import com.aloute.support.IntegrationTest;
import com.aloute.support.MutableClock;
import com.aloute.model.user.Role;
import com.aloute.model.user.User;
import com.aloute.model.user.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Lượt xem bài: mỗi người một lượt mỗi bài mỗi ngày, không tính chủ bài, hiện trong Insights. */
class PostViewCountIT extends IntegrationTest {

    @Autowired PostViewService views;
    @Autowired PostService posts;
    @Autowired InsightsService insights;
    @Autowired MutableClock clock;

    @Test
    void countsOncePerViewerPerDayAndNeverTheAuthor() {
        User creator = createUser(Role.CREATOR);
        User fan = createUser();
        Post post = posts.create(creator.getId(), "bài", Visibility.PUBLIC, List.of(), null);

        assertThat(views.record(fan.getId(), post.getId())).isTrue();
        assertThat(views.record(fan.getId(), post.getId())).as("cùng ngày: không đếm lại").isFalse();
        assertThat(views.record(creator.getId(), post.getId())).as("chủ bài không tự tính").isFalse();
        assertThat(insights.insights(creator.getId(), 7).views()).isEqualTo(1);

        clock.advance(Duration.ofDays(1));
        assertThat(views.record(fan.getId(), post.getId())).as("ngày hôm sau được đếm tiếp").isTrue();
        InsightsViewHolder holder = new InsightsViewHolder(insights.insights(creator.getId(), 7));
        assertThat(holder.view.views()).isEqualTo(2);
        assertThat(holder.view.topPosts()).isEmpty();
        assertThat(holder.view.series().get(6).views()).isEqualTo(1);
    }

    @Test
    void cannotCountViewsOfPostsYouCannotSee() {
        Post hidden = posts.create(createUser().getId(), "riêng tư", Visibility.PRIVATE, List.of(), null);

        assertThatThrownBy(() -> views.record(createUser().getId(), hidden.getId())).isInstanceOf(PostNotFoundException.class);
    }

    @Test
    void httpEndpointAndDetailPageRecordViews() throws Exception {
        User creator = createUser(Role.CREATOR);
        User fan = createUser();
        User reader = createUser();
        Post post = posts.create(creator.getId(), "bài", Visibility.PUBLIC, List.of(), null);

        mvc.perform(post("/api/posts/" + post.getId() + "/view").with(csrf()).with(asUser(fan))).andExpect(status().isNoContent());
        mvc.perform(get("/posts/" + post.getId()).with(asUser(reader))).andExpect(status().isOk());
        mvc.perform(post("/api/posts/" + post.getId() + "/view").with(csrf())).andExpect(status().isUnauthorized());

        assertThat(insights.insights(creator.getId(), 7).views()).as("fan + reader").isEqualTo(2);
    }

    private record InsightsViewHolder(com.aloute.dto.creator.InsightsView view) {
    }
}
