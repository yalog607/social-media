package com.aloute.chat;

import com.aloute.social.FriendService;
import com.aloute.support.IntegrationTest;
import com.aloute.support.TestMedia;
import com.aloute.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Quản lý nhóm chat: vai trò, đổi tên/ảnh, xóa thành viên, biệt danh, chuyển quyền chủ nhóm. */
class GroupManagementIT extends IntegrationTest {

    @Autowired ChatService chats;
    @Autowired MessageService messages;
    @Autowired FriendService friends;

    private User owner;
    private User admin;
    private User member;
    private User other;
    private Conversation group;

    private void befriend(User a, User b) {
        friends.sendRequest(a.getId(), b.getId());
        friends.accept(b.getId(), a.getId());
    }

    private void setUp() {
        owner = createUser();
        admin = createUser();
        member = createUser();
        other = createUser();
        for (User u : List.of(admin, member, other)) {
            befriend(owner, u);
        }
        group = chats.createGroup(owner.getId(), "Nhóm test", List.of(admin.getId(), member.getId()));
        chats.setRole(owner.getId(), group.getId(), admin.getId(), GroupRole.ADMIN);
    }

    private GroupRole role(User u) {
        return chats.roleOf(u.getId(), group.getId());
    }

    @Test
    void creatorIsOwnerAndOnlyOwnerAssignsRoles() {
        setUp();

        assertThat(role(owner)).isEqualTo(GroupRole.OWNER);
        assertThat(role(admin)).isEqualTo(GroupRole.ADMIN);
        assertThat(role(member)).isEqualTo(GroupRole.MEMBER);
        assertThatThrownBy(() -> chats.setRole(admin.getId(), group.getId(), member.getId(), GroupRole.ADMIN))
                .hasMessageContaining("chủ nhóm");
        assertThatThrownBy(() -> chats.setRole(owner.getId(), group.getId(), owner.getId(), GroupRole.MEMBER))
                .isInstanceOf(ChatActionException.class);
        assertThatThrownBy(() -> chats.setRole(owner.getId(), group.getId(), member.getId(), GroupRole.OWNER))
                .isInstanceOf(ChatActionException.class);
    }

    @Test
    void ownerAndAdminCanRenameButMembersCannot() {
        setUp();

        chats.rename(admin.getId(), group.getId(), "  Tên   mới ");
        assertThat(chats.header(owner.getId(), chats.requireMembership(owner.getId(), group.getId())).title()).isEqualTo("Tên mới");
        assertThatThrownBy(() -> chats.rename(member.getId(), group.getId(), "Hack")).isInstanceOf(ChatActionException.class);
        assertThatThrownBy(() -> chats.rename(owner.getId(), group.getId(), " ")).isInstanceOf(ChatActionException.class);
        assertThatThrownBy(() -> chats.rename(owner.getId(), group.getId(), "x".repeat(101))).isInstanceOf(ChatActionException.class);
    }

    @Test
    void groupAvatarMustBeAnImageAndOnlyManagersChangeIt() {
        setUp();
        MockMultipartFile image = new MockMultipartFile("avatar", "a.jpg", "image/jpeg", TestMedia.jpeg(60, 60));
        MockMultipartFile text = new MockMultipartFile("avatar", "a.txt", "text/plain", "xin chào".getBytes());

        chats.setAvatar(admin.getId(), group.getId(), image);

        assertThat(chats.header(member.getId(), chats.requireMembership(member.getId(), group.getId())).avatarUrl()).isNotNull();
        assertThatThrownBy(() -> chats.setAvatar(owner.getId(), group.getId(), text)).hasMessageContaining("ảnh");
        assertThatThrownBy(() -> chats.setAvatar(member.getId(), group.getId(), image)).isInstanceOf(ChatActionException.class);
    }

    @Test
    void membershipChangesFollowTheRoleHierarchy() {
        setUp();

        befriend(admin, other);
        chats.addMember(admin.getId(), group.getId(), other.getId());
        
        // Members can add other members if approval is not required
        User anotherFriend = createUser();
        friends.sendRequest(member.getId(), anotherFriend.getId());
        friends.accept(anotherFriend.getId(), member.getId());
        chats.addMember(member.getId(), group.getId(), anotherFriend.getId());
        assertThat(chats.isMember(anotherFriend.getId(), group.getId())).isTrue();
        assertThatThrownBy(() -> chats.removeMember(admin.getId(), group.getId(), owner.getId())).isInstanceOf(ChatActionException.class);
        assertThatThrownBy(() -> chats.removeMember(member.getId(), group.getId(), other.getId())).isInstanceOf(ChatActionException.class);
        assertThatThrownBy(() -> chats.removeMember(owner.getId(), group.getId(), owner.getId())).hasMessageContaining("rời nhóm");

        chats.removeMember(admin.getId(), group.getId(), other.getId());
        assertThat(chats.isMember(other.getId(), group.getId())).isFalse();
        chats.removeMember(owner.getId(), group.getId(), admin.getId());
        assertThat(chats.isMember(admin.getId(), group.getId())).isFalse();
    }

    @Test
    void nicknamesFollowPermissionsAndShowInMessages() {
        setUp();

        // Test that anyone can change nickname by default
        chats.setNickname(member.getId(), group.getId(), member.getId(), "  Mochi   xinh ");
        chats.setNickname(member.getId(), group.getId(), admin.getId(), "Quản trị viên 1");
        
        // Disable allowAnyoneChangeNickname
        chats.setAllowAnyoneChangeNickname(owner.getId(), group.getId(), false);

        chats.setNickname(admin.getId(), group.getId(), member.getId(), "Bé Mochi");
        assertThatThrownBy(() -> chats.setNickname(member.getId(), group.getId(), admin.getId(), "x")).isInstanceOf(ChatActionException.class);
        assertThatThrownBy(() -> chats.setNickname(admin.getId(), group.getId(), owner.getId(), "x")).isInstanceOf(ChatActionException.class);
        assertThatThrownBy(() -> chats.setNickname(owner.getId(), group.getId(), member.getId(), "x".repeat(41))).isInstanceOf(ChatActionException.class);

        MessageView sent = messages.send(member.getId(), group.getId(), "xin chào", null);
        assertThat(sent.sender().displayName()).isEqualTo("Bé Mochi");
        assertThat(messages.history(owner.getId(), group.getId(), null).messages().get(0).sender().displayName()).isEqualTo("Bé Mochi");

        chats.setNickname(owner.getId(), group.getId(), member.getId(), "  ");
        assertThat(messages.history(owner.getId(), group.getId(), null).messages().get(0).sender().displayName())
                .isEqualTo(member.getProfile().getDisplayName());
    }

    @Test
    void directChatNicknamesShowInTheConversationList() {
        User a = createUser();
        User b = createUser();
        Conversation direct = chats.startDirect(a.getId(), b.getId());

        chats.setNickname(a.getId(), direct.getId(), b.getId(), "Bạn thân");

        assertThat(chats.listFor(a.getId())).extracting(ConversationSummaryView::title).contains("Bạn thân");
        assertThat(chats.listFor(b.getId())).extracting(ConversationSummaryView::title).contains(a.getProfile().getDisplayName());
    }

    @Test
    void ownershipTransferAndRequiredSuccessionWhenTheOwnerLeaves() {
        setUp();

        chats.transferOwnership(owner.getId(), group.getId(), member.getId());
        assertThat(role(member)).isEqualTo(GroupRole.OWNER);
        assertThat(role(owner)).isEqualTo(GroupRole.ADMIN);
        assertThatThrownBy(() -> chats.transferOwnership(owner.getId(), group.getId(), admin.getId())).isInstanceOf(ChatActionException.class);

        assertThatThrownBy(() -> chats.leave(member.getId(), group.getId(), null)).isInstanceOf(ChatActionException.class);
        chats.leave(member.getId(), group.getId(), admin.getId());
        assertThat(role(admin)).isEqualTo(GroupRole.OWNER);
        assertThat(chats.memberViews(group.getId())).filteredOn(m -> m.role() == GroupRole.OWNER).hasSize(1);
    }

    @Test
    void httpFlowAndPageRendering() throws Exception {
        setUp();

        mvc.perform(post("/messages/" + group.getId() + "/title").param("title", "Tên qua form").with(csrf()).with(asUser(admin)))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/messages/" + group.getId() + "/title").param("title", "Hack").with(csrf()).with(asUser(member)))
                .andExpect(status().is3xxRedirection());
        mvc.perform(multipart("/messages/" + group.getId() + "/avatar")
                        .file(new MockMultipartFile("avatar", "a.jpg", "image/jpeg", TestMedia.jpeg(40, 40))).with(csrf()).with(asUser(owner)))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/messages/" + group.getId() + "/members/" + member.getId() + "/role").param("role", "ADMIN")
                        .with(csrf()).with(asUser(owner))).andExpect(status().is3xxRedirection());
        mvc.perform(get("/messages/" + group.getId()).with(asUser(owner))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Tên qua form")))
                .andExpect(content().string(containsString("Nhường chủ nhóm")));
        mvc.perform(get("/messages/" + group.getId()).with(asUser(createUser()))).andExpect(status().isNotFound());
        assertThat(chats.header(owner.getId(), chats.requireMembership(owner.getId(), group.getId())).title()).isEqualTo("Tên qua form");
        assertThat(role(member)).isEqualTo(GroupRole.ADMIN);
        assertThat(UUID.randomUUID()).isNotNull();
    }
}
