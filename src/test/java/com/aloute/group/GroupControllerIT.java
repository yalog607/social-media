package com.aloute.group;

import com.aloute.dto.group.GroupView;
import com.aloute.model.chat.Conversation;
import com.aloute.model.chat.GroupRole;
import com.aloute.model.user.User;
import com.aloute.repository.chat.ConversationMemberRepository;
import com.aloute.repository.chat.ConversationRepository;
import com.aloute.repository.chat.ConversationRequestRepository;
import com.aloute.service.group.GroupService;
import com.aloute.service.social.FriendService;
import com.aloute.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class GroupControllerIT extends IntegrationTest {

    @Autowired GroupService groupService;
    @Autowired FriendService friends;
    @Autowired ConversationRepository conversations;
    @Autowired ConversationMemberRepository members;
    @Autowired ConversationRequestRepository requests;

    @Test
    void loadsGroupsPageSuccessfully() throws Exception {
        User user = createUser();

        mvc.perform(get("/groups").with(asUser(user)))
                .andExpect(status().isOk())
                .andExpect(view().name("groups/list"))
                .andExpect(model().attribute("active", "groups"))
                .andExpect(model().attributeExists("myGroups", "discoverGroups", "friends"));
    }

    @Test
    void createGroupSuccessfully() throws Exception {
        User creator = createUser();
        User friend = createUser();
        friends.sendRequest(creator.getId(), friend.getId());
        friends.accept(friend.getId(), creator.getId());

        mvc.perform(post("/groups/create")
                        .param("title", "CLB Tin Học")
                        .param("description", "Nơi trao đổi lập trình và công nghệ")
                        .param("approvalRequired", "true")
                        .param("memberIds", friend.getId().toString())
                        .with(csrf())
                        .with(asUser(creator)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/groups/*"));

        List<Conversation> all = conversations.findAllGroupConversations();
        Conversation created = all.stream()
                .filter(c -> "CLB Tin Học".equals(c.getTitle()))
                .findFirst()
                .orElse(null);

        assertThat(created).isNotNull();
        assertThat(created.getDescription()).isEqualTo("Nơi trao đổi lập trình và công nghệ");
        assertThat(created.isApprovalRequired()).isTrue();
        assertThat(members.existsByConversationIdAndUserId(created.getId(), creator.getId())).isTrue();
        assertThat(members.existsByConversationIdAndUserId(created.getId(), friend.getId())).isTrue();
    }

    @Test
    void joinPublicGroupImmediately() throws Exception {
        User creator = createUser();
        User friend = createUser();
        friends.sendRequest(creator.getId(), friend.getId());
        friends.accept(friend.getId(), creator.getId());

        Conversation group = groupService.createGroup(creator.getId(), "CLB Tiếng Anh", "Học tiếng Anh mỗi ngày",
                false, List.of(friend.getId()), null);

        User stranger = createUser();
        mvc.perform(post("/groups/" + group.getId() + "/join")
                        .param("redirectUrl", "/groups?tab=discover")
                        .with(csrf())
                        .with(asUser(stranger)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/groups?tab=discover"));

        assertThat(members.existsByConversationIdAndUserId(group.getId(), stranger.getId())).isTrue();
    }

    @Test
    void requestJoinGroupWithApprovalRequiredAndCancelRequest() throws Exception {
        User creator = createUser();
        User friend = createUser();
        friends.sendRequest(creator.getId(), friend.getId());
        friends.accept(friend.getId(), creator.getId());

        Conversation group = groupService.createGroup(creator.getId(), "CLB Mật", "Nhóm kín trường học",
                true, List.of(friend.getId()), null);

        User stranger = createUser();

        // 1. Xin vào nhóm -> Tạo request đang chờ
        mvc.perform(post("/groups/" + group.getId() + "/join")
                        .param("redirectUrl", "/groups?tab=discover")
                        .with(csrf())
                        .with(asUser(stranger)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/groups?tab=discover"));

        assertThat(members.existsByConversationIdAndUserId(group.getId(), stranger.getId())).isFalse();
        assertThat(requests.existsByConversationIdAndUserId(group.getId(), stranger.getId())).isTrue();

        // 2. Hủy yêu cầu xin gia nhập
        mvc.perform(post("/groups/" + group.getId() + "/cancel-request")
                        .param("redirectUrl", "/groups?tab=discover")
                        .with(csrf())
                        .with(asUser(stranger)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/groups?tab=discover"));

        assertThat(requests.existsByConversationIdAndUserId(group.getId(), stranger.getId())).isFalse();
    }

    @Test
    void viewGroupPageAndPostFeed() throws Exception {
        User creator = createUser();
        User friend = createUser();
        friends.sendRequest(creator.getId(), friend.getId());
        friends.accept(friend.getId(), creator.getId());

        Conversation group = groupService.createGroup(creator.getId(), "CLB Game", "Nơi chia sẻ game",
                false, List.of(friend.getId()), null);

        // 1. Xem trang nhóm
        mvc.perform(get("/groups/" + group.getId()).with(asUser(creator)))
                .andExpect(status().isOk())
                .andExpect(view().name("groups/view"))
                .andExpect(model().attribute("isMember", true))
                .andExpect(model().attributeExists("group", "page"));

        // 2. Đăng bài vào trong nhóm
        mvc.perform(post("/posts")
                        .param("content", "Chào mừng mọi người tới CLB Game!")
                        .param("groupId", group.getId().toString())
                        .with(csrf())
                        .with(asUser(creator)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/groups/" + group.getId()));

        // 3. Kiểm tra trang nhóm có bài viết
        mvc.perform(get("/groups/" + group.getId()).with(asUser(creator)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("isMember", true));

        // 4. Kiểm tra trang chủ (Home feed) KHÔNG chứa bài viết của nhóm
        mvc.perform(get("/").with(asUser(creator)))
                .andExpect(status().isOk())
                .andExpect(view().name("home/feed"));

        // 5. Người ngoài (chưa vào nhóm) không thể đăng bài vào nhóm
        User stranger = createUser();
        mvc.perform(post("/posts")
                        .param("content", "Bài viết spam của người lạ")
                        .param("groupId", group.getId().toString())
                        .with(csrf())
                        .with(asUser(stranger)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/groups/" + group.getId()));

        // 6. Thành viên rời nhóm
        mvc.perform(post("/groups/" + group.getId() + "/leave")
                        .with(csrf())
                        .with(asUser(friend)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/groups"));

        assertThat(members.existsByConversationIdAndUserId(group.getId(), friend.getId())).isFalse();
    }
}
