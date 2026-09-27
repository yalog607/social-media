package com.aloute.social;

import com.aloute.support.IntegrationTest;
import com.aloute.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Phần 3a: theo dõi một chiều, không cần chấp nhận. */
class FollowServiceIT extends IntegrationTest {

    @Autowired FollowService follows;
    @Autowired BlockService blocks;

    @Test
    void followAndUnfollow() {
        User a = createUser();
        User b = createUser();

        follows.follow(a.getId(), b.getId());
        assertThat(follows.isFollowing(a.getId(), b.getId())).isTrue();
        assertThat(follows.isFollowing(b.getId(), a.getId())).as("theo dõi không cần chấp nhận và không tự động hai chiều").isFalse();
        assertThat(follows.followerCount(b.getId())).isEqualTo(1);
        assertThat(follows.followingCount(a.getId())).isEqualTo(1);

        follows.unfollow(a.getId(), b.getId());
        assertThat(follows.isFollowing(a.getId(), b.getId())).isFalse();
    }

    @Test
    void followingTwiceIsIdempotent() {
        User a = createUser();
        User b = createUser();
        follows.follow(a.getId(), b.getId());
        follows.follow(a.getId(), b.getId());

        assertThat(follows.followerCount(b.getId())).isEqualTo(1);
    }

    @Test
    void cannotFollowYourself() {
        User a = createUser();
        assertThatThrownBy(() -> follows.follow(a.getId(), a.getId())).isInstanceOf(SocialActionException.class);
    }

    @Test
    void cannotFollowSomeoneBlocked() {
        User a = createUser();
        User b = createUser();
        blocks.block(b.getId(), a.getId());

        assertThatThrownBy(() -> follows.follow(a.getId(), b.getId())).isInstanceOf(SocialActionException.class);
    }

    @Test
    void listsFollowersAndFollowing() {
        User a = createUser();
        User b = createUser();
        User c = createUser();
        follows.follow(a.getId(), b.getId());
        follows.follow(c.getId(), b.getId());

        assertThat(follows.followers(b.getId())).extracting(v -> v.id()).containsExactlyInAnyOrder(a.getId(), c.getId());
        assertThat(follows.following(a.getId())).extracting(v -> v.id()).containsExactly(b.getId());
    }
}
