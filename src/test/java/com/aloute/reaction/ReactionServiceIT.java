package com.aloute.reaction;

import com.aloute.dto.reaction.ReactionSummary;
import com.aloute.model.reaction.ReactionType;
import com.aloute.service.reaction.ReactionService;

import com.aloute.exception.common.RateLimitExceededException;
import com.aloute.model.post.Post;
import com.aloute.exception.post.PostNotFoundException;
import com.aloute.service.post.PostService;
import com.aloute.support.IntegrationTest;
import com.aloute.model.user.User;
import com.aloute.model.user.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Phần 2b: thả/đổi/bỏ cảm xúc. */
class ReactionServiceIT extends IntegrationTest {

    @Autowired ReactionService reactions;
    @Autowired PostService posts;

    private Post publicPost(User author) {
        return posts.create(author.getId(), "bài của " + author.getUsername(), Visibility.PUBLIC, List.of(), null);
    }

    @Test
    void reactingTwiceWithTheSameTypeUnreacts() {
        User author = createUser();
        Post post = publicPost(author);
        User viewer = createUser();

        ReactionSummary first = reactions.toggle(viewer.getId(), post.getId(), ReactionType.LOVE);
        assertThat(first.mine()).isEqualTo(ReactionType.LOVE);
        assertThat(first.count(ReactionType.LOVE)).isEqualTo(1);

        ReactionSummary second = reactions.toggle(viewer.getId(), post.getId(), ReactionType.LOVE);
        assertThat(second.mine()).isNull();
        assertThat(second.count(ReactionType.LOVE)).isEqualTo(0);
        assertThat(second.total()).isEqualTo(0);
    }

    @Test
    void reactingWithADifferentTypeReplacesTheFirstOne() {
        User author = createUser();
        Post post = publicPost(author);
        User viewer = createUser();

        reactions.toggle(viewer.getId(), post.getId(), ReactionType.LOVE);
        ReactionSummary result = reactions.toggle(viewer.getId(), post.getId(), ReactionType.HAHA);

        assertThat(result.mine()).isEqualTo(ReactionType.HAHA);
        assertThat(result.count(ReactionType.LOVE)).isEqualTo(0);
        assertThat(result.count(ReactionType.HAHA)).isEqualTo(1);
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void eachPersonCountsOnceEvenAcrossDifferentPosts() {
        User author = createUser();
        Post post = publicPost(author);
        User a = createUser();
        User b = createUser();

        reactions.toggle(a.getId(), post.getId(), ReactionType.FIRE);
        reactions.toggle(b.getId(), post.getId(), ReactionType.FIRE);

        assertThat(reactions.summarize(post.getId(), null).count(ReactionType.FIRE)).isEqualTo(2);
    }

    @Test
    void cannotReactToAPrivatePostOfSomeoneElse() {
        User owner = createUser();
        Post privatePost = posts.create(owner.getId(), "riêng tư", Visibility.PRIVATE, List.of(), null);

        assertThatThrownBy(() -> reactions.toggle(createUser().getId(), privatePost.getId(), ReactionType.SAD))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    void moreThan120ReactionsInAMinuteIsRejected() {
        User author = createUser();
        Post post = publicPost(author);
        UUID userId = createUser().getId();
        for (int i = 0; i < 120; i++) {
            // đổi qua lại 2 loại để mỗi lần đều tính là một lượt (thả lại đúng loại cũ sẽ bị coi là bỏ, không tính thêm)
            reactions.toggle(userId, post.getId(), i % 2 == 0 ? ReactionType.WOW : ReactionType.SAD);
        }

        assertThatThrownBy(() -> reactions.toggle(userId, post.getId(), ReactionType.LOVE))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void summarizeAllBatchesCountsAndMineAcrossManyPosts() {
        User author = createUser();
        Post postA = publicPost(author);
        Post postB = publicPost(author);
        User viewer = createUser();
        reactions.toggle(viewer.getId(), postA.getId(), ReactionType.LOVE);

        Map<UUID, ReactionSummary> result = reactions.summarizeAll(List.of(postA.getId(), postB.getId()), viewer.getId());

        assertThat(result.get(postA.getId()).mine()).isEqualTo(ReactionType.LOVE);
        assertThat(result.get(postB.getId()).mine()).isNull();
        assertThat(result.get(postB.getId()).total()).isEqualTo(0);
    }
}
