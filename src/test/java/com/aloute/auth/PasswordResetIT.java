package com.aloute.auth;

import com.aloute.common.MailService;
import com.aloute.security.CookieService;
import com.aloute.support.IntegrationTest;
import com.aloute.user.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** U1: quên / đặt lại mật khẩu. */
class PasswordResetIT extends IntegrationTest {

    @MockitoBean MailService mail;
    @Autowired com.aloute.security.RefreshTokenService refreshTokens;

    private String requestLinkFor(User user) throws Exception {
        mvc.perform(post("/forgot-password").with(csrf()).param("email", user.getEmail()))
                .andExpect(status().isOk());
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(mail).sendPasswordReset(eq(user.getEmail()), anyString(), link.capture(), anyInt());
        return UriComponentsBuilder.fromUriString(link.getValue()).build().getQueryParams().getFirst("token");
    }

    @Test
    void unknownEmailGetsTheSameAnswerAndNoMailIsSent() throws Exception {
        String known = mvc.perform(post("/forgot-password").with(csrf()).param("email", createUser().getEmail()))
                .andReturn().getResponse().getContentAsString();
        String unknown = mvc.perform(post("/forgot-password").with(csrf()).param("email", "ghost@test.local"))
                .andReturn().getResponse().getContentAsString();

        assertThat(unknown).contains("Nếu email này có tài khoản");
        assertThat(unknown).as("không lộ email có tồn tại hay không").isEqualTo(known);
        verify(mail, never()).sendPasswordReset(eq("ghost@test.local"), anyString(), anyString(), anyInt());
    }

    @Test
    void linkLetsUserSetNewPasswordOnceAndOldPasswordStopsWorking() throws Exception {
        User user = createUser();
        String token = requestLinkFor(user);

        mvc.perform(get("/reset-password").param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Đặt mật khẩu mới")));

        mvc.perform(post("/reset-password").with(csrf()).param("token", token)
                        .param("password", "NewPass456").param("confirmPassword", "NewPass456"))
                .andExpect(redirectedUrl("/login"));

        // đăng nhập bằng mật khẩu mới được, mật khẩu cũ thì không
        mvc.perform(post("/login").with(csrf()).with(fromIp(uniqueIp()))
                        .param("identifier", user.getEmail()).param("password", "NewPass456"))
                .andExpect(redirectedUrl("/"));
        mvc.perform(post("/login").with(csrf()).with(fromIp(uniqueIp()))
                        .param("identifier", user.getEmail()).param("password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("chưa đúng")));

        // dùng lại link: bị từ chối
        mvc.perform(post("/reset-password").with(csrf()).param("token", token)
                        .param("password", "Another789").param("confirmPassword", "Another789"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hết dùng được")));
    }

    @Test
    void expiredLinkIsRejected() throws Exception {
        String token = requestLinkFor(createUser());

        clock.advance(Duration.ofMinutes(31));

        mvc.perform(get("/reset-password").param("token", token))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hết dùng được")));
    }

    @Test
    void requestingANewLinkInvalidatesThePreviousOne() throws Exception {
        User user = createUser();
        String first = requestLinkFor(user);
        org.mockito.Mockito.clearInvocations(mail);
        String second = requestLinkFor(user);

        assertThat(second).isNotEqualTo(first);
        mvc.perform(get("/reset-password").param("token", first))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hết dùng được")));
        mvc.perform(get("/reset-password").param("token", second))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Đặt mật khẩu mới")));
    }

    @Test
    void weakOrMismatchedPasswordKeepsTheLinkUsable() throws Exception {
        String token = requestLinkFor(createUser());

        mvc.perform(post("/reset-password").with(csrf()).param("token", token)
                        .param("password", "onlyletters").param("confirmPassword", "onlyletters"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("chữ và số")));
        mvc.perform(post("/reset-password").with(csrf()).param("token", token)
                        .param("password", "Valid1234").param("confirmPassword", "Valid9999"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("chưa khớp")));

        mvc.perform(get("/reset-password").param("token", token))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Đặt mật khẩu mới")));
    }

    @Test
    void resettingRevokesExistingSessionsAndVerifiesEmail() throws Exception {
        User user = createUser();
        user.setEmailVerified(false);
        users.saveAndFlush(user);
        String oldRefresh = refreshTokens.issue(user, "test");

        String token = requestLinkFor(user);
        mvc.perform(post("/reset-password").with(csrf()).param("token", token)
                        .param("password", "NewPass456").param("confirmPassword", "NewPass456"))
                .andExpect(redirectedUrl("/login"));

        mvc.perform(post("/auth/refresh").with(csrf()).cookie(new Cookie(CookieService.REFRESH_COOKIE, oldRefresh)))
                .andExpect(status().isUnauthorized());
        assertThat(users.findById(user.getId()).orElseThrow().isEmailVerified()).isTrue();
    }

    @Test
    void garbageTokenShowsInvalidLinkPage() throws Exception {
        MvcResult result = mvc.perform(get("/reset-password").param("token", "not-a-real-token")).andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("hết dùng được");
        mvc.perform(get("/reset-password")).andExpect(content().string(org.hamcrest.Matchers.containsString("hết dùng được")));
    }
}
