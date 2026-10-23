package com.aloute.auth;

import com.aloute.dto.auth.SocialIdentity;
import com.aloute.service.auth.SocialTokenVerifier;

import com.aloute.security.CookieService;
import com.aloute.support.IntegrationTest;
import com.aloute.model.user.AuthProvider;
import com.aloute.model.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** U1: đăng nhập Social qua Firebase (token được giả lập, không gọi Firebase thật). */
class SocialLoginIT extends IntegrationTest {

    @MockitoBean SocialTokenVerifier verifier;

    private MvcResult loginWith(SocialIdentity identity) throws Exception {
        when(verifier.enabled()).thenReturn(true);
        when(verifier.verify(anyString())).thenReturn(identity);
        return mvc.perform(post("/auth/firebase").with(csrf())
                        .contentType("application/json").content("{\"idToken\":\"fake-token\"}"))
                .andReturn();
    }

    private static SocialIdentity google(String uid, String email, boolean verified) {
        return new SocialIdentity(uid, email, verified, "Bạn Google", "https://img.example/a.png", AuthProvider.GOOGLE);
    }

    private static String uid() {
        return "fb-" + UUID.randomUUID();
    }

    private static String email() {
        return "g" + UUID.randomUUID().toString().substring(0, 8) + "@gmail.test";
    }

    @Test
    void firstSocialLoginCreatesAccountWithUsernameFromEmailAndStartsSession() throws Exception {
        String email = email();
        String uid = uid();

        MvcResult result = loginWith(google(uid, email, true));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(cookie(result, CookieService.ACCESS_COOKIE)).isNotNull();
        User created = users.findByFirebaseUid(uid).orElseThrow();
        assertThat(created.getEmail()).isEqualTo(email);
        assertThat(created.getPasswordHash()).as("tài khoản Social chưa có mật khẩu").isNull();
        assertThat(created.isEmailVerified()).isTrue();
        assertThat(created.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(created.getProfile().getDisplayName()).isEqualTo("Bạn Google");
        assertThat(created.getUsername()).matches("^[a-z0-9_.]{3,30}$");
    }

    @Test
    void secondLoginWithSameUidReusesTheAccount() throws Exception {
        String uid = uid();
        String email = email();
        loginWith(google(uid, email, true));
        long before = users.count();

        loginWith(google(uid, email, true));

        assertThat(users.count()).isEqualTo(before);
    }

    @Test
    void socialLoginLinksToExistingLocalAccountWithTheSameEmail() throws Exception {
        User local = createUser();
        local.setEmailVerified(true);
        users.saveAndFlush(local);

        MvcResult result = loginWith(google(uid(), local.getEmail(), true));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        User linked = users.findById(local.getId()).orElseThrow();
        assertThat(linked.getFirebaseUid()).isNotNull();
        assertThat(linked.getPasswordHash()).as("email đã xác minh nên giữ mật khẩu").isNotNull();
    }

    @Test
    void linkingToAnUnverifiedLocalAccountDisablesItsPasswordToBlockPreHijacking() throws Exception {
        // Kẻ xấu tạo trước tài khoản bằng email của nạn nhân (email chưa xác minh)
        User squatted = createUser();
        squatted.setEmailVerified(false);
        users.saveAndFlush(squatted);

        loginWith(google(uid(), squatted.getEmail(), true));

        User after = users.findById(squatted.getId()).orElseThrow();
        assertThat(after.getPasswordHash()).as("mật khẩu của kẻ tạo trước phải bị vô hiệu").isNull();
        assertThat(after.isEmailVerified()).isTrue();

        mvc.perform(post("/login").with(csrf()).with(fromIp(uniqueIp()))
                        .param("identifier", squatted.getEmail()).param("password", PASSWORD))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("chưa đúng")));
    }

    @Test
    void unverifiedOrMissingEmailIsRejected() throws Exception {
        assertThat(loginWith(google(uid(), email(), false)).getResponse().getStatus()).isEqualTo(422);
        assertThat(loginWith(google(uid(), null, true)).getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    void invalidTokenIsRejectedWith401() throws Exception {
        when(verifier.enabled()).thenReturn(true);
        when(verifier.verify(anyString()))
                .thenThrow(new SocialTokenVerifier.InvalidSocialTokenException("bad", null));

        MvcResult result = mvc.perform(post("/auth/firebase").with(csrf())
                .contentType("application/json").content("{\"idToken\":\"forged\"}")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(cookie(result, CookieService.ACCESS_COOKIE)).isNull();
    }

    @Test
    void suspendedAccountCannotUseSocialLogin() throws Exception {
        User user = createUser();
        user.setEmailVerified(true);
        user.setFirebaseUid(uid());
        user.setStatus(com.aloute.model.user.UserStatus.SUSPENDED);
        users.saveAndFlush(user);

        MvcResult result = loginWith(google(user.getFirebaseUid(), user.getEmail(), true));

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(cookie(result, CookieService.ACCESS_COOKIE)).isNull();
    }

    @Test
    void endpointReportsUnavailableWhenFirebaseIsNotConfigured() throws Exception {
        when(verifier.enabled()).thenReturn(false);

        MvcResult result = mvc.perform(post("/auth/firebase").with(csrf())
                .contentType("application/json").content("{\"idToken\":\"x\"}")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
    }

    @Test
    void endpointRequiresCsrfToken() throws Exception {
        mvc.perform(post("/auth/firebase").contentType("application/json").content("{\"idToken\":\"x\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }
}
