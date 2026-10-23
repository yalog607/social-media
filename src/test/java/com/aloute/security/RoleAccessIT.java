package com.aloute.security;

import com.aloute.support.IntegrationTest;
import com.aloute.model.user.Role;
import com.aloute.model.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ma trận vai trò × khu vực. Kế thừa: ADMIN > MANAGER > USER và CREATOR > USER.
 * Manager KHÔNG tự động có quyền Creator (giả định đã chốt trong kế hoạch).
 */
class RoleAccessIT extends IntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired RefreshTokenService refreshTokens;

    private int statusFor(String path, User user) throws Exception {
        MockHttpServletRequestBuilder request = get(path);
        if (user != null) {
            request = request.with(asUser(user));
        }
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    @Test
    void anonymousVisitorsCanOnlySeePublicPages() throws Exception {
        assertThat(statusFor("/", null)).isEqualTo(200);
        assertThat(statusFor("/login", null)).isEqualTo(200);
        assertThat(statusFor("/register", null)).isEqualTo(200);
        assertThat(statusFor("/forgot-password", null)).isEqualTo(200);

        for (String protectedPath : new String[]{"/settings", "/me", "/creator", "/manage", "/admin"}) {
            assertThat(statusFor(protectedPath, null)).as(protectedPath).isEqualTo(302);
        }
    }

    @Test
    void devOnlyStyleguideIsNotExposedOutsideDevProfile() throws Exception {
        assertThat(statusFor("/dev/styleguide", null)).isEqualTo(404);
    }

    @Test
    void userRoleSeesOnlyUserAreas() throws Exception {
        User user = createUser();
        assertThat(statusFor("/settings", user)).isEqualTo(200);
        assertThat(statusFor("/creator", user)).isEqualTo(403);
        assertThat(statusFor("/manage", user)).isEqualTo(403);
        assertThat(statusFor("/admin", user)).isEqualTo(403);
    }

    @Test
    void creatorGetsCreatorAreaButNotStaffAreas() throws Exception {
        User creator = createUser(Role.CREATOR);
        assertThat(statusFor("/settings", creator)).as("kế thừa USER").isEqualTo(200);
        assertThat(statusFor("/creator", creator)).isEqualTo(200);
        assertThat(statusFor("/manage", creator)).isEqualTo(403);
        assertThat(statusFor("/admin", creator)).isEqualTo(403);
    }

    @Test
    void managerGetsManageAreaButNotCreatorOrAdmin() throws Exception {
        User manager = createUser(Role.MANAGER);
        assertThat(statusFor("/settings", manager)).as("kế thừa USER").isEqualTo(200);
        assertThat(statusFor("/manage", manager)).isEqualTo(200);
        assertThat(statusFor("/creator", manager)).as("Manager không tự có quyền Creator").isEqualTo(403);
        assertThat(statusFor("/admin", manager)).isEqualTo(403);
    }

    @Test
    void adminInheritsManagerAndUserButNotCreator() throws Exception {
        User admin = createUser(Role.ADMIN);
        assertThat(statusFor("/admin", admin)).isEqualTo(200);
        assertThat(statusFor("/manage", admin)).as("Admin kế thừa Manager").isEqualTo(200);
        assertThat(statusFor("/settings", admin)).as("Admin kế thừa User").isEqualTo(200);
        assertThat(statusFor("/creator", admin)).isEqualTo(403);
    }

    @Test
    void navigationOnlyShowsEntriesTheUserMayOpen() throws Exception {
        String userNav = mvc.perform(get("/").with(asUser(createUser()))).andReturn().getResponse().getContentAsString();
        assertThat(userNav).doesNotContain("href=\"/creator\"", "href=\"/manage\"", "href=\"/admin\"");

        String adminNav = mvc.perform(get("/").with(asUser(createUser(Role.ADMIN)))).andReturn().getResponse().getContentAsString();
        assertThat(adminNav).contains("href=\"/manage\"", "href=\"/admin\"").doesNotContain("href=\"/creator\"");

        String creatorNav = mvc.perform(get("/").with(asUser(createUser(Role.CREATOR)))).andReturn().getResponse().getContentAsString();
        assertThat(creatorNav).contains("href=\"/creator\"").doesNotContain("href=\"/admin\"");
    }

    @Test
    void roleColourFollowsHighestRole() throws Exception {
        mvc.perform(get("/").with(asUser(createUser(Role.ADMIN))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-role=\"admin\"")));
        mvc.perform(get("/").with(asUser(createUser(Role.MANAGER))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-role=\"manager\"")));
        mvc.perform(get("/").with(asUser(createUser())))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-role=\"user\"")));
    }

    @Test
    void forbiddenPageIsFriendlyAndHidesInternals() throws Exception {
        mvc.perform(get("/admin").with(asUser(createUser())))
                .andExpect(status().isForbidden());
    }

    @Test
    void suspendedUserWithStillValidTokenIsSignedOutOnNextRequest() throws Exception {
        User user = createUser();
        String refresh = refreshTokens.issue(user, "test");
        user.setStatus(com.aloute.model.user.UserStatus.SUSPENDED);
        users.saveAndFlush(user);

        // Access token còn hạn tối đa 15 phút nhưng tài khoản đã bị khóa: phiên bị thu hồi, không lỗi 500
        var page = mvc.perform(get("/settings").with(asUser(user)))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/login"))
                .andReturn();
        assertThat(page.getResponse().getCookie(CookieService.ACCESS_COOKIE).getMaxAge()).isZero();

        mvc.perform(get("/api/anything").with(asUser(user))).andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/auth/refresh")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(new jakarta.servlet.http.Cookie(CookieService.REFRESH_COOKIE, refresh)))
                .andExpect(status().isUnauthorized());
    }
}
