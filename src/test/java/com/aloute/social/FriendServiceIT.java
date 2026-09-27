package com.aloute.social;

import com.aloute.support.IntegrationTest;
import com.aloute.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Phần 3a: kết bạn (hai chiều, cần chấp nhận). */
class FriendServiceIT extends IntegrationTest {

    @Autowired FriendService friends;
    @Autowired BlockService blocks;

    @Test
    void sendAcceptMakesThemFriends() {
        User a = createUser();
        User b = createUser();

        friends.sendRequest(a.getId(), b.getId());
        assertThat(friends.stateBetween(a.getId(), b.getId())).isEqualTo(FriendState.OUTGOING);
        assertThat(friends.stateBetween(b.getId(), a.getId())).isEqualTo(FriendState.INCOMING);

        friends.accept(b.getId(), a.getId());

        assertThat(friends.areFriends(a.getId(), b.getId())).isTrue();
        assertThat(friends.stateBetween(a.getId(), b.getId())).isEqualTo(FriendState.FRIENDS);
        assertThat(friends.stateBetween(b.getId(), a.getId())).isEqualTo(FriendState.FRIENDS);
    }

    @Test
    void mutualRequestsAutoAccept() {
        User a = createUser();
        User b = createUser();

        friends.sendRequest(a.getId(), b.getId());
        friends.sendRequest(b.getId(), a.getId());

        assertThat(friends.areFriends(a.getId(), b.getId())).isTrue();
    }

    @Test
    void decliningRemovesTheRequest() {
        User a = createUser();
        User b = createUser();
        friends.sendRequest(a.getId(), b.getId());

        friends.decline(b.getId(), a.getId());

        assertThat(friends.stateBetween(a.getId(), b.getId())).isEqualTo(FriendState.NONE);
    }

    @Test
    void cancelingRemovesOwnPendingRequest() {
        User a = createUser();
        User b = createUser();
        friends.sendRequest(a.getId(), b.getId());

        friends.cancel(a.getId(), b.getId());

        assertThat(friends.stateBetween(a.getId(), b.getId())).isEqualTo(FriendState.NONE);
    }

    @Test
    void unfriendRemovesAnAcceptedFriendship() {
        User a = createUser();
        User b = createUser();
        friends.sendRequest(a.getId(), b.getId());
        friends.accept(b.getId(), a.getId());

        friends.unfriend(a.getId(), b.getId());

        assertThat(friends.areFriends(a.getId(), b.getId())).isFalse();
        assertThat(friends.stateBetween(a.getId(), b.getId())).isEqualTo(FriendState.NONE);
    }

    @Test
    void cannotFriendYourself() {
        User a = createUser();
        assertThatThrownBy(() -> friends.sendRequest(a.getId(), a.getId())).isInstanceOf(SocialActionException.class);
    }

    @Test
    void cannotSendASecondRequestOrFriendAnExistingFriend() {
        User a = createUser();
        User b = createUser();
        friends.sendRequest(a.getId(), b.getId());
        assertThatThrownBy(() -> friends.sendRequest(a.getId(), b.getId())).isInstanceOf(SocialActionException.class);

        friends.accept(b.getId(), a.getId());
        assertThatThrownBy(() -> friends.sendRequest(a.getId(), b.getId())).isInstanceOf(SocialActionException.class);
    }

    @Test
    void cannotFriendSomeoneBlocked() {
        User a = createUser();
        User b = createUser();
        blocks.block(a.getId(), b.getId());

        assertThatThrownBy(() -> friends.sendRequest(a.getId(), b.getId())).isInstanceOf(SocialActionException.class);
        assertThatThrownBy(() -> friends.sendRequest(b.getId(), a.getId())).isInstanceOf(SocialActionException.class);
    }

    @Test
    void listsFriendsAndPendingRequestsFromBothSides() {
        User a = createUser();
        User b = createUser();
        User c = createUser();
        friends.sendRequest(a.getId(), b.getId());
        friends.accept(b.getId(), a.getId());
        friends.sendRequest(c.getId(), a.getId());

        assertThat(friends.friendsOf(a.getId())).extracting(v -> v.id()).containsExactly(b.getId());
        assertThat(friends.incomingRequests(a.getId())).extracting(r -> r.person().id()).containsExactly(c.getId());
        assertThat(friends.outgoingRequests(c.getId())).extracting(r -> r.person().id()).containsExactly(a.getId());
        assertThat(friends.friendCount(a.getId())).isEqualTo(1);
    }
}
