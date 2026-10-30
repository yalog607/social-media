package com.aloute.service.season;

import com.aloute.model.reaction.ReactionType;
import com.aloute.model.user.Role;
import com.aloute.model.user.Visibility;
import com.aloute.service.comment.CommentService;
import com.aloute.service.admin.AdminService;
import com.aloute.service.post.PostService;
import com.aloute.service.post.ScheduledPostPublisher;
import com.aloute.service.reaction.ReactionService;
import com.aloute.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SeasonIT extends IntegrationTest {
    @Autowired SeasonService seasons;
    @Autowired PostService posts;
    @Autowired ReactionService reactions;
    @Autowired CommentService comments;
    @Autowired AdminService admin;
    @Autowired JdbcTemplate jdbc;
    @Autowired ScheduledPostPublisher publisher;

    @Test void activeSeasonCanBeUpdatedAndParticipantBannedAndRestored() throws Exception {
        var owner = createUser(Role.ADMIN);
        var author = createUser();
        var date = clock.instant().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();
        var id = seasons.create(owner.getId(), "Mùa cũ", "", "#TestBan", date, date);
        seasons.publish(id);
        posts.create(author.getId(), "Bài dự thi #TestBan", Visibility.PUBLIC, List.of(), null);
        clock.advance(Duration.ofSeconds(1));
        assertThat(seasons.standings(id)).hasSize(1);
        var later = seasons.get(id).end().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDateTime().plusHours(2);
        seasons.updateActive(id, owner.getId(), "Mùa mới", "Mô tả mới", later);
        assertThat(seasons.get(id).name()).isEqualTo("Mùa mới");
        assertThat(seasons.get(id).end()).isEqualTo(later.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant());
        assertThatThrownBy(() -> seasons.banParticipant(id, author.getId(), owner.getId(), " ")).isInstanceOf(IllegalArgumentException.class);
        seasons.banParticipant(id, author.getId(), owner.getId(), "Gian lận");
        assertThat(seasons.standings(id)).isEmpty();
        assertThat(seasons.bannedParticipants(id)).hasSize(1);
        assertThat(jdbc.queryForObject("select detail from notifications where recipient_id=? and reference_id=? and type='SEASON_BANNED'", String.class, author.getId(), id)).isEqualTo("Lý do: Gian lận");
        mvc.perform(get("/notifications").with(asUser(author))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Lý do: Gian lận")));
        seasons.unbanParticipant(id, author.getId(), owner.getId());
        assertThat(seasons.standings(id)).hasSize(1);
        assertThat(seasons.bannedParticipants(id)).isEmpty();
        var past = clock.instant().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDateTime().minusMinutes(1);
        assertThatThrownBy(() -> seasons.updateActive(id, owner.getId(), "Mùa mới", "", past)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void publicPostScoresAndIsFrozenUntilReviewAndSettlement() throws Exception {
        var manager = createUser(Role.ADMIN);
        var moderator = createUser(Role.MANAGER);
        mvc.perform(get("/admin/seasons").with(asUser(moderator))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/seasons").with(asUser(moderator)).with(csrf())).andExpect(status().isForbidden());
        var author = createUser();
        var reader = createUser();
        var startDate = clock.instant().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate().plusDays(1);
        var seasonId = seasons.create(manager.getId(), "Mùa thử", "Cùng chia sẻ", "#GocHocTapChill", startDate, startDate);
        seasons.publish(seasonId);
        var season = seasons.get(seasonId);
        clock.advance(Duration.between(clock.instant(), season.start()).plusSeconds(1));
        seasons.advance();
        var post = posts.create(author.getId(), "Bàn học #GocHocTapChill", Visibility.PUBLIC, List.of(), null);
        clock.advance(Duration.ofSeconds(1));
        reactions.toggle(reader.getId(), post.getId(), ReactionType.LOVE);
        comments.create(reader.getId(), post.getId(), null, "Hay quá");
        comments.create(reader.getId(), post.getId(), null, "Thêm bình luận");
        posts.share(reader.getId(), post.getId(), "Cùng xem");
        clock.advance(Duration.ofSeconds(1));
        assertThat(seasons.standings(seasonId)).singleElement().satisfies(row -> assertThat(row.score()).isEqualTo(6));
        mvc.perform(get("/seasons/" + seasonId).with(asUser(reader))).andExpect(status().isOk());
        mvc.perform(get("/admin/seasons/" + seasonId).with(asUser(manager))).andExpect(status().isOk());
        mvc.perform(get("/").with(asUser(reader))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Mùa thử")));
        mvc.perform(post("/admin/seasons/" + seasonId + "/settle").with(asUser(reader)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/notifications").with(asUser(author))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Mùa thử")));

        clock.advance(Duration.between(clock.instant(), season.end()).plusSeconds(1));
        seasons.advance();
        assertThat(jdbc.queryForObject("select score from season_posts where season_id=? and post_id=?", Long.class, seasonId, post.getId())).isEqualTo(6);
        reactions.toggle(reader.getId(), post.getId(), ReactionType.LOVE);
        assertThat(seasons.standings(seasonId)).singleElement().satisfies(row -> assertThat(row.score()).isEqualTo(6));
        assertThatThrownBy(() -> seasons.settle(seasonId, manager.getId())).isInstanceOf(IllegalArgumentException.class);
        clock.advance(Duration.ofHours(24));
        seasons.settle(seasonId, manager.getId());
        seasons.settle(seasonId, manager.getId());
        assertThat(jdbc.queryForObject("select count(*) from season_leaderboard_snapshots where season_id=?", Integer.class, seasonId)).isEqualTo(1);
        assertThat(seasons.get(seasonId).status()).isEqualTo("SETTLED");
        assertThat(seasons.standings(seasonId)).singleElement().satisfies(row -> assertThat(row.score()).isEqualTo(6));
        mvc.perform(post("/admin/seasons/" + seasonId + "/delete").with(asUser(reader)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/admin/seasons/" + seasonId + "/delete").with(asUser(manager)).with(csrf())).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/seasons"));
        assertThatThrownBy(() -> seasons.get(seasonId)).isInstanceOf(IllegalArgumentException.class);
        assertThat(posts.getVisible(post.getId(), reader.getId()).getId()).isEqualTo(post.getId());
        assertThat(jdbc.queryForObject("select count(*) from season_leaderboard_snapshots where season_id=?", Integer.class, seasonId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from season_events where season_id=?", Integer.class, seasonId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from season_posts where season_id=?", Integer.class, seasonId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from notifications where reference_id=? and type in ('SEASON_JOIN','SEASON_RESULT')", Integer.class, seasonId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action='SEASON_DELETED' and target_id=?", Integer.class, seasonId)).isEqualTo(1);
    }

    @Test void postCanLoseAndRegainEligibilityAndReviewCanExcludeIt() {
        var manager = createUser(Role.ADMIN);
        var administrator = createUser(Role.ADMIN);
        var author = createUser();
        var reader = createUser();
        var date = clock.instant().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate().plusDays(2);
        var id = seasons.create(manager.getId(), "Kiểm tra", "", "#ThuNghiem", date, date);
        seasons.publish(id);
        var season = seasons.get(id);
        clock.advance(Duration.between(clock.instant(), season.start()).plusSeconds(1));
        seasons.advance();
        var post = posts.create(author.getId(), "Bài #ThuNghiem", Visibility.PUBLIC, List.of(), null);
        clock.advance(Duration.ofSeconds(1));
        comments.create(reader.getId(), post.getId(), null, "Bình luận");
        clock.advance(Duration.ofSeconds(1));
        assertThat(seasons.standings(id)).singleElement().satisfies(row -> assertThat(row.score()).isEqualTo(2));
        posts.edit(author.getId(), post.getId(), "Bỏ thẻ", Visibility.PUBLIC);
        clock.advance(Duration.ofSeconds(1));
        assertThat(seasons.standings(id)).isEmpty();
        posts.edit(author.getId(), post.getId(), "Thêm lại #ThuNghiem", Visibility.PUBLIC);
        clock.advance(Duration.ofSeconds(1));
        assertThat(seasons.standings(id)).singleElement().satisfies(row -> assertThat(row.score()).isEqualTo(2));
        admin.suspend(administrator.getId(), author.getId(), "Rà soát tài khoản");
        clock.advance(Duration.ofSeconds(1));
        assertThat(seasons.standings(id)).isEmpty();
        admin.unsuspend(administrator.getId(), author.getId());
        clock.advance(Duration.ofSeconds(1));
        assertThat(seasons.standings(id)).singleElement().satisfies(row -> assertThat(row.score()).isEqualTo(2));
        admin.deletePermanently(administrator.getId(), reader.getId(), "Kiểm thử xóa tài khoản");
        clock.advance(Duration.ofSeconds(1));
        assertThat(seasons.standings(id)).singleElement().satisfies(row -> assertThat(row.score()).isZero());
        clock.advance(Duration.between(clock.instant(), season.end()).plusSeconds(1));
        seasons.advance();
        seasons.exclude(id, post.getId(), manager.getId(), "Vi phạm thể lệ");
        assertThat(seasons.standings(id)).isEmpty();
        clock.advance(Duration.ofHours(24));
        seasons.settle(id, manager.getId());
    }

    @Test void canPublishSeasonStartingTodayAndPreventOverlappingPublishedSeasons() throws Exception {
        var manager = createUser(Role.ADMIN);
        var date = clock.instant().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();
        var id = seasons.create(manager.getId(), "Hôm nay", "", "#HomNay", date, date);
        mvc.perform(get("/admin/seasons").with(asUser(manager))).andExpect(status().isOk());
        mvc.perform(get("/admin/seasons/" + id).with(asUser(manager))).andExpect(status().isOk());
        seasons.publish(id);
        assertThat(seasons.get(id).status()).isEqualTo("ACTIVE");
        var overlap = seasons.create(manager.getId(), "Trùng lịch", "", "#TrungLich", date, date);
        assertThatThrownBy(() -> seasons.publish(overlap)).isInstanceOf(IllegalArgumentException.class);
        clock.advance(Duration.between(clock.instant(), seasons.get(id).end()).plusSeconds(1));
        seasons.advance();
        clock.advance(Duration.ofHours(24));
        seasons.settle(id, manager.getId());
    }

    @Test void scheduledPostJoinsWhenActuallyPublished() {
        var manager = createUser(Role.ADMIN);
        var creator = createUser(Role.CREATOR);
        var date = clock.instant().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate().plusDays(1);
        var id = seasons.create(manager.getId(), "Hẹn giờ", "", "#HenGio", date, date);
        seasons.publish(id);
        var season = seasons.get(id);
        var post = posts.create(creator.getId(), "Bài hẹn #HenGio", Visibility.PUBLIC, List.of(), null,
                List.of(), null, season.start().plusSeconds(3600));
        clock.advance(Duration.between(clock.instant(), season.start()).plusSeconds(7200));
        seasons.advance();
        publisher.publishDue();
        clock.advance(Duration.ofSeconds(1));
        assertThat(seasons.scores(id)).extracting(SeasonScoring.PostScore::postId).contains(post.getId());
        clock.advance(Duration.between(clock.instant(), season.end()).plusSeconds(1));
        seasons.advance();
        clock.advance(Duration.ofHours(24));
        seasons.settle(id, manager.getId());
    }

    @Test void simultaneousPublicationDoesNotStartOverlappingSeasons() throws Exception {
        var manager = createUser(Role.ADMIN);
        var date = clock.instant().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate().plusDays(1);
        var first = seasons.create(manager.getId(), "A", "", "#A", date, date);
        var second = seasons.create(manager.getId(), "B", "", "#B", date, date);
        var gate = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> one = () -> { gate.await(); try { seasons.publish(first); return true; } catch (IllegalArgumentException e) { return false; } };
            java.util.concurrent.Callable<Boolean> two = () -> { gate.await(); try { seasons.publish(second); return true; } catch (IllegalArgumentException e) { return false; } };
            var a = pool.submit(one); var b = pool.submit(two); gate.countDown();
            assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(true, false);
            var published = seasons.get(first).status().equals("UPCOMING") ? first : second;
            clock.advance(Duration.between(clock.instant(), seasons.get(published).end()).plusSeconds(1));
            seasons.advance();
            clock.advance(Duration.ofHours(24));
            seasons.settle(published, manager.getId());
        } finally { pool.shutdownNow(); }
    }

    @Test void deletingDraftAndActiveSeasonReleasesItsSchedule() {
        var manager = createUser(Role.ADMIN);
        var date = clock.instant().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();
        var draft = seasons.create(manager.getId(), "Nháp", "", "#Nhap", date, date);
        seasons.delete(draft, manager.getId());
        assertThatThrownBy(() -> seasons.get(draft)).isInstanceOf(IllegalArgumentException.class);

        var active = seasons.create(manager.getId(), "Đang chạy", "", "#DangChay", date, date);
        seasons.publish(active);
        seasons.delete(active, manager.getId());
        var replacement = seasons.create(manager.getId(), "Thay thế", "", "#ThayThe", date, date);
        seasons.publish(replacement);
        assertThat(seasons.get(replacement).status()).isEqualTo("ACTIVE");
        seasons.delete(replacement, manager.getId());
    }
}
