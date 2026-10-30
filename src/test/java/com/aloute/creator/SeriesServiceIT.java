package com.aloute.creator;

import com.aloute.dto.creator.SeriesView;
import com.aloute.dto.post.PostView;
import com.aloute.exception.creator.InvalidSeriesException;
import com.aloute.exception.post.PostNotFoundException;
import com.aloute.model.post.Post;
import com.aloute.model.user.Role;
import com.aloute.model.user.User;
import com.aloute.model.user.Visibility;
import com.aloute.service.creator.PollService;
import com.aloute.service.creator.SeriesService;
import com.aloute.service.post.PostService;
import com.aloute.service.post.PostViewAssembler;
import com.aloute.service.social.BlockService;
import com.aloute.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Series của Creator: tạo/sửa/xóa mềm, thêm bài đủ điều kiện, quyền xem từng bài, chặn và quyền truy cập. */
class SeriesServiceIT extends IntegrationTest {

    @Autowired SeriesService series;
    @Autowired PostService postService;
    @Autowired PollService polls;
    @Autowired BlockService blocks;
    @Autowired PostViewAssembler assembler;
    @Autowired JdbcTemplate jdbc;

    private Post newPost(User author, String text, Visibility visibility) {
        return postService.create(author.getId(), text, visibility, null, null);
    }

    @Test
    void creatorCreatesEditsAndSoftDeletesWithoutDeletingPosts() {
        User creator = createUser(Role.CREATOR);
        UUID id = series.create(creator.getId(), "  Học Java  ", "Từ cơ bản");
        Post p = newPost(creator, "Bài 1", Visibility.PUBLIC);
        series.addPost(creator.getId(), id, p.getId());

        series.update(creator.getId(), id, "Học Java 21", "");
        assertThat(series.owned(creator.getId(), id)).satisfies(v -> {
            assertThat(v.title()).isEqualTo("Học Java 21");
            assertThat(v.postCount()).isEqualTo(1);
        });
        assertThat(assembler.assemble(List.of(postService.getVisible(p.getId(), creator.getId())), creator.getId()).get(0).series())
                .satisfies(s -> assertThat(s.title()).isEqualTo("Học Java 21"));

        series.delete(creator.getId(), id);

        assertThat(series.mine(creator.getId())).isEmpty();
        assertThatThrownBy(() -> series.owned(creator.getId(), id)).isInstanceOf(PostNotFoundException.class);
        Post kept = postService.getVisible(p.getId(), creator.getId());
        assertThat(kept.isDeleted()).isFalse();
        assertThat(kept.getSeriesId()).isNull();
    }

    @Test
    void validationAndLimits() {
        User user = createUser();
        User creator = createUser(Role.CREATOR);

        assertThatThrownBy(() -> series.create(user.getId(), "X", "")).isInstanceOf(InvalidSeriesException.class);
        assertThatThrownBy(() -> series.create(creator.getId(), "  ", "")).isInstanceOf(InvalidSeriesException.class);
        assertThatThrownBy(() -> series.create(creator.getId(), "a".repeat(81), "")).isInstanceOf(InvalidSeriesException.class);
        assertThatThrownBy(() -> series.create(creator.getId(), "ok", "d".repeat(301))).isInstanceOf(InvalidSeriesException.class);
        for (int i = 0; i < SeriesService.MAX_SERIES_PER_CREATOR; i++) {
            series.create(creator.getId(), "S" + i, "");
        }
        assertThatThrownBy(() -> series.create(creator.getId(), "quá nhiều", "")).isInstanceOf(InvalidSeriesException.class);
    }

    @Test
    void onlyOwnEligiblePostsCanBeAdded() {
        User creator = createUser(Role.CREATOR);
        User other = createUser(Role.CREATOR);
        User sharer = createUser();
        UUID id = series.create(creator.getId(), "S", "");
        Post mine = newPost(creator, "mine", Visibility.PUBLIC);
        Post foreign = newPost(other, "foreign", Visibility.PUBLIC);
        Post poll = polls.create(creator.getId(), "Poll?", List.of("A", "B"), null);
        Post share = postService.share(creator.getId(), foreign.getId(), "chia sẻ");

        series.addPost(creator.getId(), id, mine.getId());
        for (Post bad : List.of(foreign, poll, share)) {
            assertThatThrownBy(() -> series.addPost(creator.getId(), id, bad.getId()))
                    .isInstanceOf(InvalidSeriesException.class);
        }
        assertThatThrownBy(() -> series.owned(other.getId(), id)).as("không quản lý được series của người khác")
                .isInstanceOf(PostNotFoundException.class);
        assertThatThrownBy(() -> series.addPost(other.getId(), id, foreign.getId())).isInstanceOf(PostNotFoundException.class);
        assertThat(series.members(creator.getId(), id)).extracting(SeriesView.PostChoice::id).containsExactly(mine.getId());
        assertThat(series.candidates(creator.getId(), id)).extracting(SeriesView.PostChoice::id).doesNotContain(mine.getId());

        series.removePost(creator.getId(), id, mine.getId());
        assertThat(series.members(creator.getId(), id)).isEmpty();
    }

    @Test
    void deletedPostsDoNotUseUpSeriesSlots() {
        User creator = createUser(Role.CREATOR);
        UUID id = series.create(creator.getId(), "S", "");
        Post gone = newPost(creator, "sẽ xóa", Visibility.PUBLIC);
        series.addPost(creator.getId(), id, gone.getId());
        postService.delete(creator.getId(), gone.getId());
        jdbc.update("update posts set series_id = ? where id = ?", id, gone.getId());
        for (int i = 0; i < SeriesService.MAX_POSTS_PER_SERIES - 1; i++) {
            jdbc.update("""
                    insert into posts (id, author_id, content, visibility, search_text, created_at, updated_at, post_kind, series_id)
                    values (gen_random_uuid(), ?, 'x', 'PUBLIC', 'x', now(), now(), 'POST', ?)""", creator.getId(), id);
        }
        Post last = newPost(creator, "bài cuối", Visibility.PUBLIC);

        series.addPost(creator.getId(), id, last.getId());

        assertThat(series.owned(creator.getId(), id).postCount()).isEqualTo(SeriesService.MAX_POSTS_PER_SERIES);
        Post extra = newPost(creator, "vượt giới hạn", Visibility.PUBLIC);
        assertThatThrownBy(() -> series.addPost(creator.getId(), id, extra.getId())).isInstanceOf(InvalidSeriesException.class);
    }

    @Test
    void pageRespectsPostVisibilityAndBlocks() {
        User creator = createUser(Role.CREATOR);
        User friend = createUser();
        User stranger = createUser();
        User blocked = createUser();
        UUID id = series.create(creator.getId(), "S", "mô tả");
        Post pub = newPost(creator, "công khai", Visibility.PUBLIC);
        Post friends = newPost(creator, "bạn bè", Visibility.FRIENDS);
        Post priv = newPost(creator, "riêng tư", Visibility.PRIVATE);
        for (Post p : List.of(pub, friends, priv)) {
            series.addPost(creator.getId(), id, p.getId());
        }
        jdbc.update("insert into friendships (id, user_a_id, user_b_id, status, requested_by) values (gen_random_uuid(), ?, ?, 'ACCEPTED', ?)",
                min(creator, friend), max(creator, friend), creator.getId());

        assertThat(series.page(id, stranger.getId()).posts()).extracting(Post::getId).containsExactly(pub.getId());
        assertThat(series.page(id, friend.getId()).posts()).extracting(Post::getId).containsExactly(pub.getId(), friends.getId());
        assertThat(series.page(id, creator.getId()).posts()).hasSize(3);

        blocks.block(creator.getId(), blocked.getId());
        assertThatThrownBy(() -> series.page(id, blocked.getId())).isInstanceOf(PostNotFoundException.class);
    }

    @Test
    void deletedSeriesPageIsNotFoundAndHttpRolesAreEnforced() throws Exception {
        User creator = createUser(Role.CREATOR);
        User user = createUser();

        mvc.perform(post("/creator/series").with(asUser(user)).with(csrf()).param("title", "S")).andExpect(status().isForbidden());
        String location = mvc.perform(post("/creator/series").with(asUser(creator)).with(csrf()).param("title", "Series HTTP"))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        UUID id = UUID.fromString(location.substring("/creator/series/".length()));

        mvc.perform(get("/creator/series/{id}", id).with(asUser(creator))).andExpect(status().isOk());
        mvc.perform(get("/creator/series/{id}", id).with(asUser(user))).andExpect(status().isForbidden());
        mvc.perform(get("/series/{id}", id).with(asUser(user))).andExpect(status().isOk());

        series.delete(creator.getId(), id);
        mvc.perform(get("/series/{id}", id).with(asUser(user))).andExpect(status().isNotFound());
    }

    private static UUID min(User a, User b) {
        return a.getId().compareTo(b.getId()) < 0 ? a.getId() : b.getId();
    }

    private static UUID max(User a, User b) {
        return a.getId().compareTo(b.getId()) < 0 ? b.getId() : a.getId();
    }
}
