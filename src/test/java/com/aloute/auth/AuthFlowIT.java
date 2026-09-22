package com.aloute.auth;

import com.aloute.security.CookieService;
import com.aloute.support.IntegrationTest;
import com.aloute.user.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** U1: đăng ký, đăng nhập, đăng xuất và vòng đời phiên (JWT cookie + refresh token xoay vòng). */
class AuthFlowIT extends IntegrationTest {

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private MvcResult register(String suffix) throws Exception {
        return mvc.perform(post("/register").with(csrf()).with(fromIp(uniqueIp()))
                        .param("displayName", "Bạn Test")
                        .param("username", "user" + suffix)
                        .param("email", "user" + suffix + "@test.local")
                        .param("password", PASSWORD)
                        .param("confirmPassword", PASSWORD))
                .andReturn();
    }

    private MvcResult login(String identifier, String password, String ip) throws Exception {
        return mvc.perform(post("/login").with(csrf()).with(fromIp(ip))
                        .param("identifier", identifier).param("password", password))
                .andReturn();
    }

    @Test
    void registerCreatesAccountAndStartsSessionWithHardenedCookies() throws Exception {
        String s = suffix();
        MvcResult result = register(s);

        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/");

        Cookie access = cookie(result, CookieService.ACCESS_COOKIE);
        Cookie refresh = cookie(result, CookieService.REFRESH_COOKIE);
        assertThat(access).isNotNull();
        assertThat(refresh).isNotNull();
        assertThat(access.isHttpOnly()).isTrue();
        assertThat(refresh.isHttpOnly()).isTrue();
        assertThat(access.getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(refresh.getPath()).as("refresh cookie chỉ gửi tới /auth").isEqualTo("/auth");

        User saved = users.findByEmail("user" + s + "@test.local").orElseThrow();
        assertThat(saved.getPasswordHash()).startsWith("$2").doesNotContain(PASSWORD);
        assertThat(saved.getProfile().getDisplayName()).isEqualTo("Bạn Test");
    }

    @Test
    void registerRejectsDuplicateEmailAndUsernameCaseInsensitively() throws Exception {
        String s = suffix();
        register(s);

        mvc.perform(post("/register").with(csrf())
                        .param("displayName", "Khác").param("username", "other" + s)
                        .param("email", ("USER" + s + "@TEST.local"))
                        .param("password", PASSWORD).param("confirmPassword", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Email này đã có tài khoản")));

        mvc.perform(post("/register").with(csrf())
                        .param("displayName", "Khác").param("username", ("USER" + s).toUpperCase())
                        .param("email", "other" + s + "@test.local")
                        .param("password", PASSWORD).param("confirmPassword", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("có người dùng mất rồi")));
    }

    @Test
    void registerValidatesInputAndDoesNotCreateAccount() throws Exception {
        String s = suffix();
        mvc.perform(post("/register").with(csrf())
                        .param("displayName", "A").param("username", "bad name!")
                        .param("email", "not-an-email")
                        .param("password", "short").param("confirmPassword", "different"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Email chưa đúng định dạng")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Username 3–30 ký tự")));
        assertThat(users.existsByEmail("not-an-email")).isFalse();
    }

    @Test
    void registerRejectsReservedUsername() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("displayName", "Giả").param("username", "admin")
                        .param("email", "fake" + suffix() + "@test.local")
                        .param("password", PASSWORD).param("confirmPassword", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("dành riêng")));
    }

    @Test
    void loginWorksWithEmailOrUsernameAndHonoursNext() throws Exception {
        User user = createUser();

        MvcResult byEmail = mvc.perform(post("/login").with(csrf()).with(fromIp(uniqueIp()))
                        .param("identifier", user.getEmail()).param("password", PASSWORD).param("next", "/settings"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings")).andReturn();
        assertThat(cookie(byEmail, CookieService.ACCESS_COOKIE)).isNotNull();

        mvc.perform(post("/login").with(csrf()).with(fromIp(uniqueIp()))
                        .param("identifier", user.getUsername().toUpperCase()).param("password", PASSWORD))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void loginIgnoresExternalNextTargets() throws Exception {
        User user = createUser();
        mvc.perform(post("/login").with(csrf()).with(fromIp(uniqueIp()))
                        .param("identifier", user.getEmail()).param("password", PASSWORD)
                        .param("next", "https://evil.example"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void wrongPasswordAndUnknownUserGiveTheSameGenericMessage() throws Exception {
        User user = createUser();
        String ip = uniqueIp();

        String wrongPassword = login(user.getEmail(), "Sai12345", ip).getResponse().getContentAsString();
        String unknownUser = login("nobody" + suffix() + "@test.local", "Sai12345", ip).getResponse().getContentAsString();

        assertThat(wrongPassword).contains("Email/username hoặc mật khẩu chưa đúng");
        assertThat(unknownUser).contains("Email/username hoặc mật khẩu chưa đúng");
    }

    @Test
    void repeatedFailuresAreRateLimitedEvenForTheCorrectPassword() throws Exception {
        User user = createUser();
        String ip = uniqueIp();
        for (int i = 0; i < 3; i++) {
            login(user.getEmail(), "Sai12345", ip);
        }

        MvcResult blocked = login(user.getEmail(), PASSWORD, ip);

        assertThat(blocked.getResponse().getContentAsString()).contains("thử sai nhiều lần");
        assertThat(cookie(blocked, CookieService.ACCESS_COOKIE)).isNull();
    }

    @Test
    void suspendedAccountCannotLogInAfterCorrectPassword() throws Exception {
        User user = createUser();
        user.setStatus(com.aloute.user.UserStatus.SUSPENDED);
        users.saveAndFlush(user);

        MvcResult result = login(user.getEmail(), PASSWORD, uniqueIp());

        assertThat(result.getResponse().getContentAsString()).contains("đang bị khóa");
        assertThat(cookie(result, CookieService.ACCESS_COOKIE)).isNull();
    }

    @Test
    void csrfIsRequiredOnStateChangingRequests() throws Exception {
        mvc.perform(post("/login").param("identifier", "a").param("password", "b"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/logout")).andExpect(status().isForbidden());
    }

    @Test
    void forgedOrExpiredAccessTokenIsTreatedAsAnonymous() throws Exception {
        User user = createUser();
        String token = jwt.generateAccessToken(com.aloute.security.AlouteUserPrincipal.of(user));

        mvc.perform(get("/settings").cookie(new Cookie(CookieService.ACCESS_COOKIE, token))).andExpect(status().isOk());

        mvc.perform(get("/settings").cookie(new Cookie(CookieService.ACCESS_COOKIE, token + "x")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/refresh?next=/settings"));

        clock.advance(Duration.ofMinutes(16));
        mvc.perform(get("/settings").cookie(new Cookie(CookieService.ACCESS_COOKIE, token)))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void apiCallsWithoutLoginGetJson401InsteadOfRedirect() throws Exception {
        mvc.perform(get("/api/anything"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/json")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("unauthorized")));
    }

    @Test
    void refreshRotatesTokenAndExpiredAccessTokenCanBeRenewed() throws Exception {
        String s = suffix();
        MvcResult registered = register(s);
        Cookie refresh = cookie(registered, CookieService.REFRESH_COOKIE);

        MvcResult refreshed = mvc.perform(post("/auth/refresh").with(csrf()).cookie(refresh))
                .andExpect(status().isNoContent()).andReturn();
        Cookie newRefresh = cookie(refreshed, CookieService.REFRESH_COOKIE);
        assertThat(newRefresh.getValue()).isNotEqualTo(refresh.getValue());
        assertThat(cookie(refreshed, CookieService.ACCESS_COOKIE)).isNotNull();

        // GET dùng cho điều hướng trang: quay lại đúng trang đang xem
        mvc.perform(get("/auth/refresh").param("next", "/settings").cookie(newRefresh))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings"));
    }

    @Test
    void reusingAnOldRefreshTokenAfterGraceRevokesEverySession() throws Exception {
        MvcResult registered = register(suffix());
        Cookie stolen = cookie(registered, CookieService.REFRESH_COOKIE);

        MvcResult rotated = mvc.perform(post("/auth/refresh").with(csrf()).cookie(stolen))
                .andExpect(status().isNoContent()).andReturn();
        Cookie legit = cookie(rotated, CookieService.REFRESH_COOKIE);

        clock.advance(Duration.ofSeconds(30));
        mvc.perform(post("/auth/refresh").with(csrf()).cookie(stolen)).andExpect(status().isUnauthorized());

        // Token hợp lệ hiện tại cũng bị thu hồi vì nghi ngờ bị đánh cắp
        mvc.perform(post("/auth/refresh").with(csrf()).cookie(legit)).andExpect(status().isUnauthorized());
    }

    @Test
    void parallelRequestsWithSameRefreshTokenWithinGraceDoNotLogTheUserOut() throws Exception {
        MvcResult registered = register(suffix());
        Cookie original = cookie(registered, CookieService.REFRESH_COOKIE);

        MvcResult first = mvc.perform(post("/auth/refresh").with(csrf()).cookie(original))
                .andExpect(status().isNoContent()).andReturn();
        mvc.perform(post("/auth/refresh").with(csrf()).cookie(original)).andExpect(status().isUnauthorized());

        // Phiên hợp lệ từ lần refresh đầu vẫn dùng được
        mvc.perform(post("/auth/refresh").with(csrf()).cookie(cookie(first, CookieService.REFRESH_COOKIE)))
                .andExpect(status().isNoContent());
    }

    @Test
    void logoutRevokesRefreshTokenAndClearsCookies() throws Exception {
        MvcResult registered = register(suffix());
        Cookie refresh = cookie(registered, CookieService.REFRESH_COOKIE);
        Cookie access = cookie(registered, CookieService.ACCESS_COOKIE);

        MvcResult out = mvc.perform(post("/logout").with(csrf()).cookie(access, refresh))
                .andExpect(redirectedUrl("/login")).andReturn();

        assertThat(cookie(out, CookieService.ACCESS_COOKIE).getMaxAge()).isZero();
        assertThat(cookie(out, CookieService.REFRESH_COOKIE).getMaxAge()).isZero();
        mvc.perform(post("/auth/refresh").with(csrf()).cookie(refresh)).andExpect(status().isUnauthorized());
    }
}
