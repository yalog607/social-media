package com.aloute.creator;

import com.aloute.dto.creator.InsightsView;
import com.aloute.service.creator.InsightsService;

import com.aloute.service.comment.CommentService;
import com.aloute.model.post.Post;
import com.aloute.service.post.PostService;
import com.aloute.service.reaction.ReactionService;
import com.aloute.model.reaction.ReactionType;
import com.aloute.service.social.FollowService;
import com.aloute.service.social.BlockService;
import com.aloute.exception.social.SocialActionException;
import com.aloute.support.IntegrationTest;
import com.aloute.model.user.Role;
import com.aloute.model.user.User;
import com.aloute.model.user.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @Autowired BlockService blocks;
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
    void newFollowersCountsDistinctPeopleEvenAfterUnfollow() {
        User creator = createUser(Role.CREATOR);
        User first = createUser();
        User second = createUser();
        follows.follow(first.getId(), creator.getId());
        follows.unfollow(first.getId(), creator.getId());
        follows.follow(first.getId(), creator.getId());
        follows.follow(second.getId(), creator.getId());
        follows.unfollow(second.getId(), creator.getId());

        InsightsView result = insights.insights(creator.getId(), 7);
        assertThat(result.followers()).isEqualTo(1);
        assertThat(result.newFollowers()).isEqualTo(2);
    }

    @Test
    void repeatFollowerOnDifferentDaysCountsOnceInRangeAndOncePerDay() {
        User creator = createUser(Role.CREATOR);
        User fan = createUser();
        follows.follow(fan.getId(), creator.getId());
        follows.unfollow(fan.getId(), creator.getId());
        follows.follow(fan.getId(), creator.getId());
        jdbc.update("update follow_events set created_at = ? where follower_id = ? and followee_id = ? and id = (select id from follow_events where follower_id = ? and followee_id = ? order by created_at limit 1)",
                java.sql.Timestamp.from(clock.instant().minus(Duration.ofDays(1))), fan.getId(), creator.getId(), fan.getId(), creator.getId());

        InsightsView result = insights.insights(creator.getId(), 7);
        assertThat(result.newFollowers()).isEqualTo(1);
        assertThat(result.series().stream().mapToLong(InsightsView.DayPoint::followers).sum()).isEqualTo(2);
    }

    @Test
    void duplicateAndBlockedFollowDoNotCreateEventsAndBlockPreservesHistory() {
        User creator = createUser(Role.CREATOR);
        User fan = createUser();
        follows.follow(fan.getId(), creator.getId());
        follows.follow(fan.getId(), creator.getId());
        blocks.block(creator.getId(), fan.getId());
        assertThatThrownBy(() -> follows.follow(fan.getId(), creator.getId())).isInstanceOf(SocialActionException.class);
        assertThat(jdbc.queryForObject("select count(*) from follow_events where follower_id = ? and followee_id = ?", Long.class, fan.getId(), creator.getId())).isEqualTo(1L);
        assertThat(insights.insights(creator.getId(), 7).followers()).isZero();
        assertThat(insights.insights(creator.getId(), 7).newFollowers()).isEqualTo(1);
    }

    @Test
    void migrationBackfillsExistingFollowAndTriggerRollsBackWithTransaction() throws Exception {
        String migration = new ClassPathResource("db/migration/V51__follow_history.sql").getContentAsString(StandardCharsets.UTF_8);
        String schema = "follow_test_" + UUID.randomUUID().toString().replace("-", "");
        UUID follower = UUID.randomUUID(), creator = UUID.randomUUID(), follow = UUID.randomUUID();
        try (var connection = jdbc.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("create schema " + schema);
                statement.execute("set local search_path to " + schema);
                statement.execute("create table users (id uuid primary key)");
                statement.execute("create table follows (id uuid primary key, follower_id uuid not null references users(id), followee_id uuid not null references users(id), created_at timestamptz not null, unique(follower_id, followee_id))");
                statement.execute("insert into users values ('" + follower + "'), ('" + creator + "')");
                statement.execute("insert into follows values ('" + follow + "', '" + follower + "', '" + creator + "', '2020-01-02 03:04:05+00')");
                statement.execute(migration);
                try (var rows = statement.executeQuery("select count(*) from follow_events where id = '" + follow + "' and created_at = '2020-01-02 03:04:05+00'")) {
                    rows.next();
                    assertThat(rows.getLong(1)).isEqualTo(1);
                }
                statement.execute("delete from follows");
                var savepoint = connection.setSavepoint();
                statement.execute("insert into follows values ('" + UUID.randomUUID() + "', '" + follower + "', '" + creator + "', now())");
                connection.rollback(savepoint);
                try (var rows = statement.executeQuery("select count(*) from follow_events")) {
                    rows.next();
                    assertThat(rows.getLong(1)).isEqualTo(1);
                }
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void followerRangeUsesUtcCalendarBoundariesAndExcludesFutureDays() {
        User creator = createUser(Role.CREATOR);
        User fan = createUser();
        follows.follow(fan.getId(), creator.getId());
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        jdbc.update("update follow_events set created_at = ? where follower_id = ? and followee_id = ?",
                java.sql.Timestamp.from(today.minusDays(7).atStartOfDay(ZoneOffset.UTC).toInstant()), fan.getId(), creator.getId());
        assertThat(insights.insights(creator.getId(), 7).newFollowers()).isZero();
        assertThat(insights.insights(creator.getId(), 30).newFollowers()).isEqualTo(1);
        jdbc.update("update follow_events set created_at = ? where follower_id = ? and followee_id = ?",
                java.sql.Timestamp.from(today.minusDays(6).atStartOfDay(ZoneOffset.UTC).toInstant()), fan.getId(), creator.getId());
        assertThat(insights.insights(creator.getId(), 7).newFollowers()).isEqualTo(1);
        jdbc.update("update follow_events set created_at = ? where follower_id = ? and followee_id = ?",
                java.sql.Timestamp.from(today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()), fan.getId(), creator.getId());
        assertThat(insights.insights(creator.getId(), 30).newFollowers()).isZero();
        assertThat(insights.insights(creator.getId(), 30).series()).allMatch(day -> day.followers() == 0);
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
