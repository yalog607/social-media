package com.aloute.creator;

import com.aloute.dto.post.PollView;
import com.aloute.dto.post.PostView;
import com.aloute.exception.post.InvalidPostException;
import com.aloute.exception.post.PostNotFoundException;
import com.aloute.model.post.Post;
import com.aloute.model.post.PostKind;
import com.aloute.model.user.Role;
import com.aloute.model.user.User;
import com.aloute.service.creator.PollService;
import com.aloute.service.notification.NotificationService;
import com.aloute.service.post.PostViewAssembler;
import com.aloute.service.social.BlockService;
import com.aloute.service.social.FollowService;
import com.aloute.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Bình chọn của Creator: tạo, bầu một phiếu, đóng hạn, chặn, thông báo người theo dõi và quyền. */
class PollServiceIT extends IntegrationTest {

    @Autowired PollService polls;
    @Autowired FollowService follows;
    @Autowired BlockService blocks;
    @Autowired NotificationService notifications;
    @Autowired PostViewAssembler assembler;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.aloute.service.post.PostService postService;

    private List<com.aloute.dto.notification.NotificationView> pollNotifications(User user) {
        return notifications.listRecent(user.getId()).stream()
                .filter(n -> n.type() == com.aloute.model.notification.NotificationType.CREATOR_POLL).toList();
    }

    private Post newPoll(User creator) {
        return polls.create(creator.getId(), "Bạn thích gì hơn?", List.of("Mèo", "Chó"), null);
    }

    private UUID optionId(Post poll, String label) {
        return jdbc.queryForObject("select id from poll_options where post_id = ? and label = ?", UUID.class, poll.getId(), label);
    }

    private PollView viewFor(Post poll, User viewer) {
        PostView view = assembler.assemble(List.of(poll), viewer == null ? null : viewer.getId()).get(0);
        assertThat(view.kind()).isEqualTo(PostKind.POLL);
        return view.poll();
    }

    @Test
    void creatorCreatesPollAndOnlyFollowersAreNotified() {
        User creator = createUser(Role.CREATOR);
        User follower = createUser();
        User stranger = createUser();
        follows.follow(follower.getId(), creator.getId());

        Post poll = newPoll(creator);

        assertThat(poll.getPostKind()).isEqualTo(PostKind.POLL);
        assertThat(pollNotifications(follower)).singleElement().satisfies(n -> {
            assertThat(n.text()).contains("bình chọn");
            assertThat(n.postId()).isEqualTo(poll.getId());
        });
        assertThat(pollNotifications(stranger)).isEmpty();
        assertThat(pollNotifications(creator)).isEmpty();
    }

    @Test
    void blockedFollowerIsNotNotified() {
        User creator = createUser(Role.CREATOR);
        User follower = createUser();
        follows.follow(follower.getId(), creator.getId());
        blocks.block(creator.getId(), follower.getId());
        jdbc.update("insert into follows (id, follower_id, followee_id) values (gen_random_uuid(), ?, ?)",
                follower.getId(), creator.getId());

        newPoll(creator);

        assertThat(pollNotifications(follower)).isEmpty();
    }

    @Test
    void nonCreatorCannotCreateAndInputIsValidated() {
        User user = createUser();
        User creator = createUser(Role.CREATOR);

        assertThatThrownBy(() -> polls.create(user.getId(), "Hỏi?", List.of("A", "B"), null))
                .isInstanceOf(InvalidPostException.class);
        assertThatThrownBy(() -> polls.create(creator.getId(), "  ", List.of("A", "B"), null))
                .isInstanceOf(InvalidPostException.class);
        assertThatThrownBy(() -> polls.create(creator.getId(), "Hỏi?", List.of("A", " "), null))
                .as("chỉ 1 lựa chọn hợp lệ").isInstanceOf(InvalidPostException.class);
        assertThatThrownBy(() -> polls.create(creator.getId(), "Hỏi?", List.of("A", "a"), null))
                .as("trùng nhau").isInstanceOf(InvalidPostException.class);
        assertThatThrownBy(() -> polls.create(creator.getId(), "Hỏi?", List.of("1", "2", "3", "4", "5", "6", "7"), null))
                .as("quá 6 lựa chọn").isInstanceOf(InvalidPostException.class);
        assertThatThrownBy(() -> polls.create(creator.getId(), "Hỏi?", List.of("A".repeat(81), "B"), null))
                .as("nhãn quá dài").isInstanceOf(InvalidPostException.class);
        assertThatThrownBy(() -> polls.create(creator.getId(), "Hỏi?", List.of("A", "B"), clock.instant().minusSeconds(5)))
                .as("giờ đóng ở quá khứ").isInstanceOf(InvalidPostException.class);
        assertThatThrownBy(() -> polls.create(creator.getId(), "Hỏi?", List.of("A", "B"), clock.instant().plus(Duration.ofDays(31))))
                .as("giờ đóng quá xa").isInstanceOf(InvalidPostException.class);
    }

    @Test
    void oneVotePerUserAndResultsAreCounted() {
        User creator = createUser(Role.CREATOR);
        User a = createUser();
        User b = createUser();
        Post poll = newPoll(creator);

        assertThat(viewFor(poll, a).canVote()).isTrue();
        assertThat(viewFor(poll, a).showResults()).isFalse();

        polls.vote(a.getId(), poll.getId(), optionId(poll, "Mèo"));
        polls.vote(b.getId(), poll.getId(), optionId(poll, "Mèo"));
        assertThatThrownBy(() -> polls.vote(a.getId(), poll.getId(), optionId(poll, "Chó")))
                .isInstanceOf(InvalidPostException.class).hasMessageContaining("đã bình chọn");

        PollView view = viewFor(poll, a);
        assertThat(view.totalVotes()).isEqualTo(2);
        assertThat(view.votedOptionId()).isEqualTo(optionId(poll, "Mèo"));
        assertThat(view.canVote()).isFalse();
        assertThat(view.showResults()).isTrue();
        assertThat(view.options()).extracting(PollView.Option::label, PollView.Option::votes, PollView.Option::percent)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Mèo", 2L, 100),
                        org.assertj.core.groups.Tuple.tuple("Chó", 0L, 0));
        assertThat(viewFor(poll, null).canVote()).as("khách không bầu được").isFalse();
    }

    @Test
    void closedPollRejectsVotesAndShowsResults() {
        User creator = createUser(Role.CREATOR);
        User voter = createUser();
        Post poll = polls.create(creator.getId(), "Hết giờ?", List.of("Có", "Không"), clock.instant().plus(Duration.ofHours(1)));

        clock.advance(Duration.ofHours(2));

        assertThatThrownBy(() -> polls.vote(voter.getId(), poll.getId(), optionId(poll, "Có")))
                .isInstanceOf(InvalidPostException.class).hasMessageContaining("kết thúc");
        PollView view = viewFor(poll, voter);
        assertThat(view.closed()).isTrue();
        assertThat(view.showResults()).isTrue();
    }

    @Test
    void blockedOrForeignOptionOrNonPollIsNotFound() {
        User creator = createUser(Role.CREATOR);
        User blocked = createUser();
        User voter = createUser();
        Post poll = newPoll(creator);
        Post other = newPoll(creator);
        Post plain = postService.create(creator.getId(), "bài thường", null, null, null);

        blocks.block(creator.getId(), blocked.getId());
        assertThatThrownBy(() -> polls.vote(blocked.getId(), poll.getId(), optionId(poll, "Mèo")))
                .isInstanceOf(PostNotFoundException.class);
        assertThatThrownBy(() -> polls.vote(voter.getId(), poll.getId(), optionId(other, "Mèo")))
                .as("lựa chọn của poll khác").isInstanceOf(PostNotFoundException.class);
        assertThatThrownBy(() -> polls.vote(voter.getId(), plain.getId(), optionId(poll, "Mèo")))
                .as("bài thường không phải poll").isInstanceOf(PostNotFoundException.class);
        assertThat(jdbc.queryForObject("select count(*) from poll_votes where post_id = ?", Integer.class, poll.getId())).isZero();
    }

    @Test
    void pollStaysPublicWhenEdited() {
        User creator = createUser(Role.CREATOR);
        Post poll = newPoll(creator);

        assertThatThrownBy(() -> postService.edit(creator.getId(), poll.getId(), "Câu hỏi mới", com.aloute.model.user.Visibility.FRIENDS))
                .isInstanceOf(InvalidPostException.class);
        Post edited = postService.edit(creator.getId(), poll.getId(), "Câu hỏi mới", com.aloute.model.user.Visibility.PUBLIC);
        assertThat(edited.getContent()).isEqualTo("Câu hỏi mới");
    }

    @Test
    void sharedPollIsAssembledWithItsOriginalPollView() {
        User creator = createUser(Role.CREATOR);
        User sharer = createUser();
        Post poll = newPoll(creator);
        Post share = postService.share(sharer.getId(), poll.getId(), "hay đấy");

        PostView view = assembler.assemble(List.of(share), sharer.getId()).get(0);

        assertThat(view.isShare()).isTrue();
        assertThat(view.sharedPost().kind()).isEqualTo(PostKind.POLL);
        assertThat(view.sharedPost().poll().options()).hasSize(2);
    }

    @Test
    void httpRoutesEnforceRoles() throws Exception {
        User creator = createUser(Role.CREATOR);
        User user = createUser();
        User manager = createUser(Role.MANAGER);

        mvc.perform(post("/creator/polls").with(asUser(user)).with(csrf()).param("question", "Q").param("options", "A", "B"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/creator/polls").with(asUser(manager)).with(csrf()).param("question", "Q").param("options", "A", "B"))
                .andExpect(status().isForbidden());

        String location = mvc.perform(post("/creator/polls").with(asUser(creator)).with(csrf())
                        .param("question", "Q?").param("options", "A", "B"))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        assertThat(location).startsWith("/posts/");
        UUID postId = UUID.fromString(location.substring("/posts/".length()));

        mvc.perform(post("/polls/{id}/vote", postId).with(asUser(user)).with(csrf())
                        .param("optionId", jdbc.queryForObject("select id::text from poll_options where post_id = ? and label = 'A'",
                                String.class, postId)))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/posts/" + postId));
        assertThat(jdbc.queryForObject("select count(*) from poll_votes where post_id = ?", Integer.class, postId)).isEqualTo(1);
    }
}
