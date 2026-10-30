package com.aloute.creator;

import com.aloute.model.creator.BadgeTier;
import com.aloute.model.post.Post;
import com.aloute.model.user.Role;
import com.aloute.model.user.User;
import com.aloute.model.user.Visibility;
import com.aloute.repository.social.FollowRepository;
import com.aloute.service.post.PostService;
import com.aloute.service.post.PostViewAssembler;
import com.aloute.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Tích theo follower trong AuthorView: batch đúng theo từng tác giả, không rò rỉ sang tác giả khác. */
class BadgeBatchIT extends IntegrationTest {

    @Autowired PostService postService;
    @Autowired PostViewAssembler assembler;
    @Autowired FollowRepository followRepository;
    @Autowired JdbcTemplate jdbc;

    private void giveFollowers(User followee, int count) {
        for (int i = 0; i < count; i++) {
            User follower = createUser();
            jdbc.update("insert into follows (id, follower_id, followee_id, created_at) values (gen_random_uuid(), ?, ?, now())",
                    follower.getId(), followee.getId());
        }
    }

    private void setBirthDate(User user, LocalDate birthDate) {
        jdbc.update("update profiles set birth_date = ? where user_id = ?", birthDate, user.getId());
    }

    @Test
    void badgeIsComputedPerAuthorFromBatchedFollowerCount() {
        User goldCreator = createUser(Role.CREATOR);
        User plainCreator = createUser(Role.CREATOR);
        User regularUser = createUser();
        setBirthDate(goldCreator, LocalDate.now().minusYears(20));
        setBirthDate(plainCreator, LocalDate.now().minusYears(20));
        giveFollowers(goldCreator, 1_000);

        Post goldPost = postService.create(goldCreator.getId(), "bai cua gold creator", Visibility.PUBLIC, null, null);
        Post plainPost = postService.create(plainCreator.getId(), "bai cua plain creator", Visibility.PUBLIC, null, null);
        Post userPost = postService.create(regularUser.getId(), "bai cua user thuong", Visibility.PUBLIC, null, null);

        var views = assembler.assemble(List.of(goldPost, plainPost, userPost), null);

        assertThat(views.get(0).author().badge()).isEqualTo(BadgeTier.GOLD);
        assertThat(views.get(1).author().badge()).isEqualTo(BadgeTier.NONE);
        assertThat(views.get(2).author().badge()).isEqualTo(BadgeTier.NONE);
    }

    @Test
    void repositoryCountsFollowersInOneQueryForMultipleAuthors() {
        User a = createUser();
        User b = createUser();
        giveFollowers(a, 3);
        giveFollowers(b, 1);

        var counts = followRepository.countByFolloweeIds(List.of(a.getId(), b.getId()));

        assertThat(counts).extracting(FollowRepository.FollowerCount::getUserId, FollowRepository.FollowerCount::getTotal)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple(a.getId(), 3L),
                        org.assertj.core.groups.Tuple.tuple(b.getId(), 1L));
    }
}
