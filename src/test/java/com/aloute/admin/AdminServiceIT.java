package com.aloute.admin;

import com.aloute.audit.AuditService;
import com.aloute.comment.CommentService;
import com.aloute.post.Post;
import com.aloute.post.PostNotFoundException;
import com.aloute.post.PostService;
import com.aloute.security.RefreshTokenService;
import com.aloute.support.IntegrationTest;
import com.aloute.user.RegistrationClosedException;
import com.aloute.user.Role;
import com.aloute.user.User;
import com.aloute.user.UserService;
import com.aloute.user.UserStatus;
import com.aloute.user.Visibility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Khu Quản trị: phân quyền Manager, khóa/xóa tài khoản, cấu hình đăng ký, nhật ký và ranh giới quyền. */
class AdminServiceIT extends IntegrationTest {

    @Autowired AdminService admin;
    @Autowired SystemSettings settings;
    @Autowired AuditService audit;
    @Autowired PostService posts;
    @Autowired CommentService comments;
    @Autowired UserService userService;
    @Autowired RefreshTokenService refreshTokens;

    @AfterEach
    void reopenRegistration() {
        settings.setRegistrationOpen(createUser(Role.ADMIN).getId(), true);
    }

    @Test
    void grantAndRevokeManagerChangeRolesRevokeSessionsAndAreLogged() {
        User adminUser = createUser(Role.ADMIN);
        User user = createUser();
        String refresh = refreshTokens.issue(user, "test");

        admin.grantManager(adminUser.getId(), user.getUsername());

        assertThat(users.findById(user.getId()).orElseThrow().hasRole(Role.MANAGER)).isTrue();
        assertThat(refreshTokens.rotate(refresh, "test")).as("phiên cũ bị thu hồi để nhận quyền mới").isEmpty();
        assertThat(admin.managers()).extracting(StaffMember::id).contains(user.getId());
        assertThatThrownBy(() -> admin.grantManager(adminUser.getId(), user.getUsername()))
                .isInstanceOf(InvalidAdminActionException.class);

        admin.revokeManager(adminUser.getId(), user.getId());

        assertThat(users.findById(user.getId()).orElseThrow().hasRole(Role.MANAGER)).isFalse();
        assertThat(audit.page(0, "MANAGER_GRANTED")).anyMatch(e -> user.getUsername().equals(e.detail()));
        assertThat(audit.page(0, "MANAGER_REVOKED")).anyMatch(e -> user.getUsername().equals(e.detail()));
    }

    @Test
    void cannotActOnAdminsOrYourself() {
        User adminUser = createUser(Role.ADMIN);
        User otherAdmin = createUser(Role.ADMIN);

        assertThatThrownBy(() -> admin.suspend(adminUser.getId(), adminUser.getId(), "x")).hasMessageContaining("chính mình");
        assertThatThrownBy(() -> admin.suspend(adminUser.getId(), otherAdmin.getId(), "x")).hasMessageContaining("Admin");
        assertThatThrownBy(() -> admin.deletePermanently(adminUser.getId(), otherAdmin.getId(), "x"))
                .isInstanceOf(InvalidAdminActionException.class);
        assertThatThrownBy(() -> admin.revokeManager(adminUser.getId(), otherAdmin.getId()))
                .isInstanceOf(InvalidAdminActionException.class);
    }

    @Test
    void suspendAndUnsuspend() {
        User adminUser = createUser(Role.ADMIN);
        User user = createUser();
        String refresh = refreshTokens.issue(user, "test");

        admin.suspend(adminUser.getId(), user.getId(), "vi phạm");
        assertThat(users.findById(user.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(refreshTokens.rotate(refresh, "test")).isEmpty();
        assertThatThrownBy(() -> admin.suspend(adminUser.getId(), user.getId(), "lại")).isInstanceOf(InvalidAdminActionException.class);

        admin.unsuspend(adminUser.getId(), user.getId());
        assertThat(users.findById(user.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void permanentDeleteHidesAllContentAndStripsStaffRoles() {
        User adminUser = createUser(Role.ADMIN);
        User victim = createUser(Role.CREATOR);
        User other = createUser();
        Post post = posts.create(victim.getId(), "sẽ biến mất", Visibility.PUBLIC, List.of(), null);
        Post othersPost = posts.create(other.getId(), "bài người khác", Visibility.PUBLIC, List.of(), null);
        var comment = comments.create(victim.getId(), othersPost.getId(), null, "bình luận của nạn nhân");

        admin.deletePermanently(adminUser.getId(), victim.getId(), "vi phạm nghiêm trọng");

        User deleted = users.findById(victim.getId()).orElseThrow();
        assertThat(deleted.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(deleted.hasRole(Role.CREATOR)).isFalse();
        assertThatThrownBy(() -> posts.getVisible(post.getId(), null)).isInstanceOf(PostNotFoundException.class);
        assertThat(comments.list(othersPost.getId(), other.getId())).isEmpty();
        assertThat(posts.getVisible(othersPost.getId(), null).getId()).isEqualTo(othersPost.getId());
        assertThat(audit.page(0, "USER_DELETED")).anyMatch(e -> "vi phạm nghiêm trọng".equals(e.detail()));
        assertThat(comment.getId()).isNotNull();
    }

    @Test
    void closedRegistrationBlocksNewAccountsButNotExistingOnes() {
        User adminUser = createUser(Role.ADMIN);
        settings.setRegistrationOpen(adminUser.getId(), false);

        assertThat(settings.registrationOpen()).isFalse();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        assertThatThrownBy(() -> userService.registerLocal("Người mới", "moi" + suffix, "moi" + suffix + "@example.com", PASSWORD))
                .isInstanceOf(RegistrationClosedException.class);

        settings.setRegistrationOpen(adminUser.getId(), true);
        assertThat(userService.registerLocal("Người mới", "moi" + suffix, "moi" + suffix + "@example.com", PASSWORD)).isNotNull();
        assertThat(audit.page(0, "SETTING_CHANGED")).anyMatch(e -> "registration_open=false".equals(e.detail()));
    }

    @Test
    void searchFindsUsersByUsernameAndOverviewHasNumbers() {
        User user = createUser();

        assertThat(admin.search(user.getUsername())).extracting(StaffMember::id).containsExactly(user.getId());
        assertThat(admin.search("không-có-ai-tên-vậy-đâu-" + UUID.randomUUID())).isEmpty();
        assertThat(admin.overview()).containsKeys("Tổng người dùng", "Bài viết", "Xu đang lưu hành");
    }

    @Test
    void adminPagesAreOnlyForAdminsAndManagersCannotUseThem() throws Exception {
        User adminUser = createUser(Role.ADMIN);
        User manager = createUser(Role.MANAGER);
        User victim = createUser();

        for (String path : List.of("/admin", "/admin/staff", "/admin/users", "/admin/logs", "/admin/settings")) {
            mvc.perform(get(path).with(asUser(adminUser))).andExpect(status().isOk());
            mvc.perform(get(path).with(asUser(manager))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/admin/users/" + victim.getId() + "/suspend").with(csrf()).with(asUser(manager)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/users/" + victim.getId() + "/suspend").param("reason", "thử").with(csrf()).with(asUser(adminUser)))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/logs").param("action", "USER_SUSPENDED").with(asUser(adminUser)))
                .andExpect(content().string(containsString("USER_SUSPENDED")));
    }

    @Test
    void registerPageShowsAFriendlyErrorWhenClosed() throws Exception {
        settings.setRegistrationOpen(createUser(Role.ADMIN).getId(), false);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        mvc.perform(post("/register").with(csrf()).param("displayName", "Moi").param("username", "moi" + suffix)
                        .param("email", "moi" + suffix + "@example.com").param("password", PASSWORD).param("confirmPassword", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("tạm ngừng nhận đăng ký")));
    }
}
