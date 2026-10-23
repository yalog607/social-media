package com.aloute.post;

import com.aloute.dto.post.PostView;
import com.aloute.exception.post.InvalidPostException;
import com.aloute.exception.post.PostNotFoundException;
import com.aloute.model.post.Post;
import com.aloute.service.post.PostService;
import com.aloute.service.post.ScheduledPostPublisher;
import com.aloute.util.post.ScheduleTime;

import com.aloute.service.feed.FeedService;
import com.aloute.service.notification.NotificationService;
import com.aloute.service.search.SearchService;
import com.aloute.service.social.FriendService;
import com.aloute.support.IntegrationTest;
import com.aloute.support.MutableClock;
import com.aloute.model.user.Role;
import com.aloute.model.user.User;
import com.aloute.model.user.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Hẹn giờ đăng bài: bài ẩn với người khác ở mọi nơi cho tới giờ, rồi tự hiện và gửi thông báo gắn thẻ. */
class ScheduledPostIT extends IntegrationTest {

    @Autowired PostService posts;
    @Autowired ScheduledPostPublisher publisher;
    @Autowired FeedService feed;
    @Autowired SearchService search;
    @Autowired FriendService friends;
    @Autowired NotificationService notifications;
    @Autowired MutableClock clock;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    private Post schedule(User creator, String text, Duration in, List<java.util.UUID> tags) {
        return posts.create(creator.getId(), text, Visibility.PUBLIC, List.of(), null, tags, null, clock.instant().plus(in));
    }

    @Test
    void scheduledPostIsHiddenFromEveryoneElseEverywhere() throws Exception {
        User creator = createUser(Role.CREATOR);
        User other = createUser();
        Post post = schedule(creator, "bài hẹn giờ #bimat", Duration.ofHours(2), List.of());

        assertThat(feed.home(other.getId(), null).posts()).extracting(PostView::id).doesNotContain(post.getId());
        assertThat(feed.home(creator.getId(), null).posts()).extracting(PostView::id).doesNotContain(post.getId());
        assertThat(feed.byAuthor(creator.getId(), other.getId(), null).posts()).isEmpty();
        assertThat(feed.byHashtag("bimat", other.getId(), null).posts()).isEmpty();
        assertThat(search.searchPosts("bài hẹn giờ", other.getId())).isEmpty();
        assertThatThrownBy(() -> posts.getVisible(post.getId(), other.getId())).isInstanceOf(PostNotFoundException.class);
        assertThatThrownBy(() -> posts.share(other.getId(), post.getId(), null)).isInstanceOf(PostNotFoundException.class);
        assertThat(posts.getVisible(post.getId(), creator.getId()).getId()).as("chủ bài vẫn xem được").isEqualTo(post.getId());
        assertThat(posts.scheduledOf(creator.getId())).extracting(Post::getId).containsExactly(post.getId());
        mvc.perform(get("/posts/" + post.getId()).with(asUser(other))).andExpect(status().isNotFound());
    }

    @Test
    void publishesWhenDueOrderedByTheScheduledTimeAndNotifiesTaggedFriends() {
        User creator = createUser(Role.CREATOR);
        User friend = createUser();
        friends.sendRequest(creator.getId(), friend.getId());
        friends.accept(friend.getId(), creator.getId());
        Post post = schedule(creator, "đến giờ rồi", Duration.ofHours(2), List.of(friend.getId()));

        assertThat(publisher.publishDue()).isZero();
        assertThat(notifications.listRecent(friend.getId())).noneMatch(n -> n.text().contains("gắn thẻ"));

        clock.advance(Duration.ofHours(3));
        assertThat(publisher.publishDue()).isGreaterThanOrEqualTo(1);
        assertThat(publisher.publishDue()).as("không đăng lần hai").isZero();

        assertThat(feed.home(friend.getId(), null).posts()).extracting(PostView::id).contains(post.getId());
        assertThat(search.searchPosts("đến giờ rồi", friend.getId())).isNotEmpty();
        assertThat(notifications.listRecent(friend.getId())).filteredOn(n -> n.text().contains("gắn thẻ")).hasSize(1);
        assertThat(posts.scheduledOf(creator.getId())).isEmpty();
    }

    @Test
    void onlyCreatorsMaySchedulesAndTheTimeMustBeInTheNearFuture() {
        User creator = createUser(Role.CREATOR);
        User plain = createUser();

        assertThatThrownBy(() -> schedule(plain, "x", Duration.ofHours(1), List.of())).isInstanceOf(InvalidPostException.class);
        assertThatThrownBy(() -> schedule(creator, "x", Duration.ofMinutes(-5), List.of())).isInstanceOf(InvalidPostException.class);
        assertThatThrownBy(() -> schedule(creator, "x", Duration.ofDays(PostService.MAX_SCHEDULE_DAYS + 1), List.of()))
                .isInstanceOf(InvalidPostException.class);
    }

    @Test
    void rescheduleAndCancelOnlyWorkForTheOwnerOfAPendingPost() {
        User creator = createUser(Role.CREATOR);
        User other = createUser(Role.CREATOR);
        Post post = schedule(creator, "bài", Duration.ofHours(1), List.of());
        Instant later = clock.instant().plus(Duration.ofHours(5));

        assertThatThrownBy(() -> posts.reschedule(other.getId(), post.getId(), later)).isInstanceOf(PostNotFoundException.class);
        assertThat(posts.reschedule(creator.getId(), post.getId(), later).getScheduledAt()).isEqualTo(later);

        posts.delete(creator.getId(), post.getId());
        clock.advance(Duration.ofHours(6));
        publisher.publishDue(); // có thể đăng bài hẹn của test khác dùng chung CSDL, nhưng không được đăng bài đã hủy
        assertThat(jdbc.queryForObject("select scheduled_at is not null from posts where id = ?", Boolean.class, post.getId()))
                .as("bài đã hủy không được đăng").isTrue();
    }

    @Test
    void scheduleTimeParsingUsesTheBrowserTimezone() {
        assertThat(ScheduleTime.parse("2026-10-10T10:00", -420)).isEqualTo(Instant.parse("2026-10-10T03:00:00Z"));
        assertThat(ScheduleTime.parse("2026-10-10T10:00", null)).isEqualTo(Instant.parse("2026-10-10T03:00:00Z"));
        assertThat(ScheduleTime.parse("", 0)).isNull();
        assertThatThrownBy(() -> ScheduleTime.parse("không phải giờ", 0)).isInstanceOf(InvalidPostException.class);
    }

    @Test
    void composerFormSchedulesAndStudioPageListsIt() throws Exception {
        User creator = createUser(Role.CREATOR);
        String when = java.time.LocalDateTime.ofInstant(clock.instant().plus(Duration.ofHours(4)), java.time.ZoneOffset.UTC)
                .withSecond(0).withNano(0).toString();

        mvc.perform(multipart("/posts").param("content", "hẹn qua form").param("visibility", "PUBLIC")
                        .param("scheduledAt", when).param("tzOffset", "0").with(csrf()).with(asUser(creator)))
                .andExpect(status().is3xxRedirection());

        assertThat(posts.scheduledOf(creator.getId())).hasSize(1);
        mvc.perform(get("/creator/scheduled").with(asUser(creator))).andExpect(status().isOk());
        mvc.perform(get("/creator/scheduled").with(asUser(createUser()))).andExpect(status().isForbidden());
    }
}
