package com.aloute.creator;

import com.aloute.comment.CommentService;
import com.aloute.post.Post;
import com.aloute.post.PostService;
import com.aloute.reaction.ReactionService;
import com.aloute.reaction.ReactionType;
import com.aloute.social.FollowService;
import com.aloute.support.IntegrationTest;
import com.aloute.user.Role;
import com.aloute.user.User;
import com.aloute.user.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Creator Studio: thống kê tương tác theo ngày và việc tự nâng cấp lên Creator. */
class InsightsServiceIT extends IntegrationTest {

    @Autowired InsightsService insights;
    @Autowired PostService posts;
    @Autowired ReactionService reactions;
    @Autowired CommentService comments;
    @Autowired FollowService follows;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    private Post publicPost(User author, String text) {
        return posts.create(author.getId(), text, Visibility.PUBLIC, List.of(), null);
    }

    @Test
    void countsEngagementOnMyPostsOnly() {
        User creator = createUser(Role.CREATOR);
        User fan = createUser();
        Post mine = publicPost(creator, "bài của tôi");
        Post other = publicPost(createUser(), "bài người khác");
        reactions.toggle(fan.getId(), mine.getId(), ReactionType.LOVE);
        reactions.toggle(fan.getId(), other.getId(), ReactionType.LOVE);
        comments.create(fan.getId(), mine.getId(), null, "hay");
        posts.share(fan.getId(), mine.getId(), "chia sẻ");
        follows.follow(fan.getId(), creator.getId());

        InsightsView view = insights.insights(creator.getId(), 7);

        assertThat(view.reactions()).isEqualTo(1);
        assertThat(view.comments()).isEqualTo(1);
        assertThat(view.shares()).isEqualTo(1);
        assertThat(view.followers()).isEqualTo(1);
        assertThat(view.newFollowers()).isEqualTo(1);
        assertThat(view.series()).hasSize(7);
        assertThat(view.series().get(6).date()).isEqualTo(LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC));
        assertThat(view.series().get(6).engagement()).isEqualTo(3);
        assertThat(view.topPosts()).singleElement().satisfies(p -> {
            assertThat(p.id()).isEqualTo(mine.getId());
            assertThat(p.total()).isEqualTo(3);
        });
    }

    @Test
    void ignoresActivityOutsideTheRangeAndDeletedComments() {
        User creator = createUser(Role.CREATOR);
        User fan = createUser();
        Post mine = publicPost(creator, "bài");
        reactions.toggle(fan.getId(), mine.getId(), ReactionType.LOVE);
        jdbc.update("update reactions set created_at = now() - interval '10 days' where post_id = ?", mine.getId());
        var deleted = comments.create(fan.getId(), mine.getId(), null, "sẽ xóa");
        comments.delete(fan.getId(), deleted.getId());

        InsightsView week = insights.insights(creator.getId(), 7);
        InsightsView month = insights.insights(creator.getId(), 30);

        assertThat(week.reactions()).isZero();
        assertThat(week.comments()).isZero();
        assertThat(week.topPosts()).isEmpty();
        assertThat(month.reactions()).isEqualTo(1);
    }

    @Test
    void percentBarsAreProportionalAndVisibleWhenNonZero() {
        assertThat(InsightsView.percent(0, 10)).isZero();
        assertThat(InsightsView.percent(10, 10)).isEqualTo(100);
        assertThat(InsightsView.percent(1, 1000)).isEqualTo(4);
        assertThat(InsightsView.percent(5, 0)).isZero();
    }

    @Test
    void userCanBecomeCreatorAndThenOpenTheStudio() throws Exception {
        User user = createUser();
        mvc.perform(get("/creator").with(asUser(user))).andExpect(status().isForbidden());

        mvc.perform(post("/settings/creator").with(csrf()).with(asUser(user)))
                .andExpect(redirectedUrl("/creator"));

        assertThat(users.findById(user.getId()).orElseThrow().hasRole(Role.CREATOR)).isTrue();
        User upgraded = users.findById(user.getId()).orElseThrow();
        mvc.perform(get("/creator").with(asUser(upgraded))).andExpect(status().isOk());
    }

    @Test
    void guestCannotBecomeCreator() throws Exception {
        mvc.perform(post("/settings/creator").with(csrf())).andExpect(status().is3xxRedirection());
    }
}
