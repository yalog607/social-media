package com.aloute.social;

import com.aloute.exception.social.SocialActionException;
import com.aloute.service.social.BlockService;
import com.aloute.service.social.FollowService;
import com.aloute.service.social.FriendService;

import com.aloute.support.IntegrationTest;
import com.aloute.model.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Phần 3a: chặn cắt đứt bạn bè/theo dõi hai chiều. */
class BlockServiceIT extends IntegrationTest {

    @Autowired BlockService blocks;
    @Autowired FriendService friends;
    @Autowired FollowService follows;

    @Test
    void blockingRemovesExistingFriendshipAndFollowsBothWays() {
        User a = createUser();
        User b = createUser();
        friends.sendRequest(a.getId(), b.getId());
        friends.accept(b.getId(), a.getId());
        follows.follow(a.getId(), b.getId());
        follows.follow(b.getId(), a.getId());

        blocks.block(a.getId(), b.getId());

        assertThat(friends.areFriends(a.getId(), b.getId())).isFalse();
        assertThat(follows.isFollowing(a.getId(), b.getId())).isFalse();
        assertThat(follows.isFollowing(b.getId(), a.getId())).isFalse();
        assertThat(blocks.isBlockedEitherWay(a.getId(), b.getId())).isTrue();
        assertThat(blocks.hasBlocked(a.getId(), b.getId())).isTrue();
        assertThat(blocks.hasBlocked(b.getId(), a.getId())).isFalse();
    }

    @Test
    void blockingTwiceIsIdempotent() {
        User a = createUser();
        User b = createUser();
        blocks.block(a.getId(), b.getId());
        blocks.block(a.getId(), b.getId());

        assertThat(blocks.listBlocked(a.getId())).hasSize(1);
    }

    @Test
    void unblockRemovesTheBlockButNotAnythingElse() {
        User a = createUser();
        User b = createUser();
        blocks.block(a.getId(), b.getId());

        blocks.unblock(a.getId(), b.getId());

        assertThat(blocks.isBlockedEitherWay(a.getId(), b.getId())).isFalse();
    }

    @Test
    void cannotBlockYourself() {
        User a = createUser();
        assertThatThrownBy(() -> blocks.block(a.getId(), a.getId())).isInstanceOf(SocialActionException.class);
    }

    @Test
    void listBlockedShowsWhoIBlockedNotWhoBlockedMe() {
        User a = createUser();
        User b = createUser();
        blocks.block(a.getId(), b.getId());

        assertThat(blocks.listBlocked(a.getId())).extracting(v -> v.id()).containsExactly(b.getId());
        assertThat(blocks.listBlocked(b.getId())).isEmpty();
    }
}
