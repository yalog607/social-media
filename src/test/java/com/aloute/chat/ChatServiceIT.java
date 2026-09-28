package com.aloute.chat;

import com.aloute.social.BlockService;
import com.aloute.social.FriendService;
import com.aloute.support.IntegrationTest;
import com.aloute.user.MessagePermission;
import com.aloute.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Phần 3c: tạo/quản lý hội thoại 1-1 và nhóm. Gửi/đọc tin nhắn ở {@link MessageServiceIT}. */
class ChatServiceIT extends IntegrationTest {

    @Autowired ChatService chats;
    @Autowired FriendService friends;
    @Autowired BlockService blocks;

    private void makeFriends(User a, User b) {
        friends.sendRequest(a.getId(), b.getId());
        friends.accept(b.getId(), a.getId());
    }

    @Test
    void startingADirectConversationTwiceReturnsTheSameOne() {
        User a = createUser();
        User b = createUser();

        Conversation first = chats.startDirect(a.getId(), b.getId());
        Conversation second = chats.startDirect(b.getId(), a.getId()); // thứ tự ngược lại

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(chats.isMember(a.getId(), first.getId())).isTrue();
        assertThat(chats.isMember(b.getId(), first.getId())).isTrue();
    }

    @Test
    void cannotStartADirectConversationWithYourself() {
        User a = createUser();
        assertThatThrownBy(() -> chats.startDirect(a.getId(), a.getId())).isInstanceOf(ChatActionException.class);
    }

    @Test
    void cannotStartADirectConversationWhenBlocked() {
        User a = createUser();
        User b = createUser();
        blocks.block(a.getId(), b.getId());

        assertThatThrownBy(() -> chats.startDirect(b.getId(), a.getId())).isInstanceOf(ChatActionException.class);
    }

    @Test
    void respectsMessagePermissionFriendsOnly() {
        User a = createUser();
        User b = createUser();
        b.getProfile().setMessagePermission(MessagePermission.FRIENDS);
        users.saveAndFlush(b);

        assertThatThrownBy(() -> chats.startDirect(a.getId(), b.getId())).isInstanceOf(ChatActionException.class);

        makeFriends(a, b);
        assertThat(chats.startDirect(a.getId(), b.getId())).isNotNull();
    }

    @Test
    void respectsMessagePermissionNobody() {
        User a = createUser();
        User b = createUser();
        b.getProfile().setMessagePermission(MessagePermission.NOBODY);
        users.saveAndFlush(b);
        makeFriends(a, b);

        assertThatThrownBy(() -> chats.startDirect(a.getId(), b.getId())).isInstanceOf(ChatActionException.class);
    }

    @Test
    void existingConversationSurvivesThePermissionChangingLater() {
        User a = createUser();
        User b = createUser();
        makeFriends(a, b);
        Conversation conversation = chats.startDirect(a.getId(), b.getId());

        b.getProfile().setMessagePermission(MessagePermission.NOBODY);
        users.saveAndFlush(b);

        assertThat(chats.startDirect(a.getId(), b.getId()).getId()).isEqualTo(conversation.getId());
    }

    @Test
    void createGroupAddsCreatorAndFriends() {
        User owner = createUser();
        User friendA = createUser();
        User friendB = createUser();
        makeFriends(owner, friendA);
        makeFriends(owner, friendB);

        Conversation group = chats.createGroup(owner.getId(), "Hội bạn thân", List.of(friendA.getId(), friendB.getId()));

        assertThat(group.isGroup()).isTrue();
        assertThat(chats.membersOf(group.getId())).extracting(m -> m.id())
                .containsExactlyInAnyOrder(owner.getId(), friendA.getId(), friendB.getId());
    }

    @Test
    void cannotCreateGroupWithSomeoneWhoIsNotAFriend() {
        User owner = createUser();
        User stranger = createUser();
        assertThatThrownBy(() -> chats.createGroup(owner.getId(), "Nhóm lạ", List.of(stranger.getId())))
                .isInstanceOf(ChatActionException.class);
    }

    @Test
    void groupNeedsATitleAndAtLeastOneOtherMember() {
        User owner = createUser();
        User friend = createUser();
        makeFriends(owner, friend);

        assertThatThrownBy(() -> chats.createGroup(owner.getId(), "  ", List.of(friend.getId())))
                .isInstanceOf(ChatActionException.class);
        assertThatThrownBy(() -> chats.createGroup(owner.getId(), "Nhóm rỗng", List.of()))
                .isInstanceOf(ChatActionException.class);
    }

    @Test
    void memberCanAddAnotherFriendToTheGroup() {
        User owner = createUser();
        User friendA = createUser();
        User friendB = createUser();
        makeFriends(owner, friendA);
        makeFriends(owner, friendB);
        Conversation group = chats.createGroup(owner.getId(), "Nhóm", List.of(friendA.getId()));

        chats.addMember(owner.getId(), group.getId(), friendB.getId());

        assertThat(chats.isMember(friendB.getId(), group.getId())).isTrue();
    }

    @Test
    void cannotAddSomeoneAlreadyInTheGroup() {
        User owner = createUser();
        User friendA = createUser();
        makeFriends(owner, friendA);
        Conversation group = chats.createGroup(owner.getId(), "Nhóm", List.of(friendA.getId()));

        assertThatThrownBy(() -> chats.addMember(owner.getId(), group.getId(), friendA.getId()))
                .isInstanceOf(ChatActionException.class);
    }

    @Test
    void cannotAddMembersToADirectConversation() {
        User a = createUser();
        User b = createUser();
        User c = createUser();
        makeFriends(a, c);
        Conversation direct = chats.startDirect(a.getId(), b.getId());

        assertThatThrownBy(() -> chats.addMember(a.getId(), direct.getId(), c.getId()))
                .isInstanceOf(ChatActionException.class);
    }

    @Test
    void memberCanLeaveAGroupButNotADirectConversation() {
        User owner = createUser();
        User friend = createUser();
        makeFriends(owner, friend);
        Conversation group = chats.createGroup(owner.getId(), "Nhóm", List.of(friend.getId()));
        Conversation direct = chats.startDirect(owner.getId(), friend.getId());

        chats.leave(friend.getId(), group.getId());
        assertThat(chats.isMember(friend.getId(), group.getId())).isFalse();

        assertThatThrownBy(() -> chats.leave(owner.getId(), direct.getId())).isInstanceOf(ChatActionException.class);
    }

    @Test
    void nonMembersCannotAccessAConversation() {
        User a = createUser();
        User b = createUser();
        User stranger = createUser();
        Conversation direct = chats.startDirect(a.getId(), b.getId());

        assertThatThrownBy(() -> chats.requireMembership(stranger.getId(), direct.getId()))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void unknownConversationIsAlsoReportedAsNotFound() {
        User a = createUser();
        assertThatThrownBy(() -> chats.requireMembership(a.getId(), UUID.randomUUID()))
                .isInstanceOf(ConversationNotFoundException.class);
    }
}
