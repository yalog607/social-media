package com.aloute.user;

import com.aloute.security.CookieService;
import com.aloute.security.RefreshTokenService;
import com.aloute.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** U3–U4: trang cá nhân, cập nhật hồ sơ, đổi mật khẩu, quyền riêng tư. */
class SettingsIT extends IntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired RefreshTokenService refreshTokens;

    @Test
    void settingsRequiresLogin() throws Exception {
        mvc.perform(post("/settings/profile").with(csrf()).param("displayName", "x"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void updatesDisplayNameAndBio() throws Exception {
        User user = createUser();

        mvc.perform(multipart("/settings/profile").with(csrf()).with(asUser(user))
                        .param("displayName", "  Mochi Xinh  ").param("bio", "Thích trà sữa"))
                .andExpect(redirectedUrl("/settings"))
                .andExpect(flash().attribute("notice", "Đã lưu hồ sơ!"));

        Profile profile = users.findById(user.getId()).orElseThrow().getProfile();
        assertThat(profile.getDisplayName()).isEqualTo("Mochi Xinh");
        assertThat(profile.getBio()).isEqualTo("Thích trà sữa");
    }

    @Test
    void validatesDisplayNameLength() throws Exception {
        mvc.perform(multipart("/settings/profile").with(csrf()).with(asUser(createUser()))
                        .param("displayName", "A").param("bio", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tên hiển thị dài 2–50 ký tự")));
    }

    @Test
    void storesARealImageAndServesItBack() throws Exception {
        User user = createUser();
        MockMultipartFile avatar = new MockMultipartFile("avatar", "me.png", "image/png", PNG);

        mvc.perform(multipart("/settings/profile").file(avatar).with(csrf()).with(asUser(user))
                        .param("displayName", "Có ảnh"))
                .andExpect(redirectedUrl("/settings"));

        String url = users.findById(user.getId()).orElseThrow().getProfile().getAvatarUrl();
        assertThat(url).startsWith("/uploads/avatars/").endsWith(".png");
        mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=31536000")))
                .andExpect(header().string("Cache-Control", containsString("immutable")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        assertThat(Files.exists(Path.of("target/test-uploads/avatars").resolve(url.substring(url.lastIndexOf('/') + 1)))).isTrue();
    }

    @Test
    void rejectsFilesThatAreNotImagesEvenWhenNamedPng() throws Exception {
        User user = createUser();
        MockMultipartFile fake = new MockMultipartFile("avatar", "evil.png", "image/png",
                "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/settings/profile").file(fake).with(csrf()).with(asUser(user))
                        .param("displayName", "Không đổi"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chỉ nhận ảnh JPG, PNG, GIF hoặc WEBP")));

        Profile profile = users.findById(user.getId()).orElseThrow().getProfile();
        assertThat(profile.getAvatarUrl()).isNull();
        assertThat(profile.getDisplayName()).as("lỗi upload không làm mất dữ liệu đã sửa").isNotEqualTo("Không đổi");
    }

    @Test
    void changePasswordNeedsCorrectCurrentPasswordAndKeepsThisDeviceLoggedIn() throws Exception {
        User user = createUser();
        String otherDevice = refreshTokens.issue(user, "phone");

        mvc.perform(post("/settings/password").with(csrf()).with(asUser(user))
                        .param("currentPassword", "SaiRoi123").param("newPassword", "NewPass456")
                        .param("confirmPassword", "NewPass456"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mật khẩu hiện tại chưa đúng")));

        MvcResult ok = mvc.perform(post("/settings/password").with(csrf()).with(asUser(user))
                        .param("currentPassword", PASSWORD).param("newPassword", "NewPass456")
                        .param("confirmPassword", "NewPass456"))
                .andExpect(redirectedUrl("/settings")).andReturn();

        assertThat(cookie(ok, CookieService.ACCESS_COOKIE)).as("thiết bị hiện tại vẫn đăng nhập").isNotNull();
        mvc.perform(post("/auth/refresh").with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie(CookieService.REFRESH_COOKIE, otherDevice)))
                .andExpect(status().isUnauthorized());
        assertThat(encoder.matches("NewPass456", users.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void changePasswordRejectsWeakAndMismatchedValues() throws Exception {
        User user = createUser();

        mvc.perform(post("/settings/password").with(csrf()).with(asUser(user))
                        .param("currentPassword", PASSWORD).param("newPassword", "onlyletters")
                        .param("confirmPassword", "onlyletters"))
                .andExpect(content().string(containsString("chữ và số")));
        mvc.perform(post("/settings/password").with(csrf()).with(asUser(user))
                        .param("currentPassword", PASSWORD).param("newPassword", "Valid1234")
                        .param("confirmPassword", "Valid9999"))
                .andExpect(content().string(containsString("chưa khớp")));
    }

    @Test
    void socialOnlyAccountCanCreateAFirstPasswordWithoutCurrentOne() throws Exception {
        User user = createUser();
        user.setPasswordHash(null);
        users.saveAndFlush(user);

        mvc.perform(get("/settings").with(asUser(user)))
                .andExpect(content().string(containsString("Tạo mật khẩu")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Mật khẩu hiện tại"))));

        mvc.perform(post("/settings/password").with(csrf()).with(asUser(user))
                        .param("newPassword", "FirstPass123").param("confirmPassword", "FirstPass123"))
                .andExpect(redirectedUrl("/settings"));
        assertThat(users.findById(user.getId()).orElseThrow().getPasswordHash()).isNotNull();
    }

    @Test
    void privacyChoicesArePersisted() throws Exception {
        User user = createUser();

        mvc.perform(post("/settings/privacy").with(csrf()).with(asUser(user))
                        .param("profileVisibility", "PRIVATE").param("defaultPostVisibility", "FRIENDS")
                        .param("messagePermission", "NOBODY"))
                .andExpect(redirectedUrl("/settings"));

        Profile p = users.findById(user.getId()).orElseThrow().getProfile();
        assertThat(p.getProfileVisibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(p.getDefaultPostVisibility()).isEqualTo(Visibility.FRIENDS);
        assertThat(p.getMessagePermission()).isEqualTo(MessagePermission.NOBODY);
    }

    @Test
    void invalidPrivacyValueIsRejectedWithoutServerError() throws Exception {
        int status = mvc.perform(post("/settings/privacy").with(csrf()).with(asUser(createUser()))
                        .param("profileVisibility", "EVERYONE-PLEASE"))
                .andReturn().getResponse().getStatus();
        assertThat(status).isBetween(200, 499);
    }

    // ---------- Trang cá nhân ----------

    @Test
    void publicProfileIsVisibleToAnonymousVisitors() throws Exception {
        User user = createUser();
        mvc.perform(get("/u/" + user.getUsername()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(user.getProfile().getDisplayName())));
    }

    @Test
    void coverBannerKeepsItsHeightEvenWithoutACoverPhoto() throws Exception {
        // Hồi quy: th:style trả về null khi chưa có coverUrl từng xóa luôn height:170px cố định,
        // khiến khung ảnh bìa co về 0 và avatar (kéo lên -56px) bị overflow-hidden cắt mất nửa trên.
        User withoutCover = createUser();
        assertThat(withoutCover.getProfile().getCoverUrl()).isNull();

        String html = mvc.perform(get("/u/" + withoutCover.getUsername()))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("class=\"profile-cover\"");
        // Không có "style=" thừa nào che mất class (điều làm avatar bị cắt trước đây)
        assertThat(html).doesNotContain("class=\"profile-cover\" style=\"\"");
    }

    @Test
    void privateProfileHidesDetailsFromOthersButNotFromItsOwner() throws Exception {
        User owner = createUser();
        owner.getProfile().setProfileVisibility(Visibility.PRIVATE);
        owner.getProfile().setBio("bí mật");
        users.saveAndFlush(owner);

        mvc.perform(get("/u/" + owner.getUsername()).with(asUser(createUser())))
                .andExpect(content().string(containsString("Trang này ở chế độ riêng tư")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("bí mật"))));
        mvc.perform(get("/u/" + owner.getUsername()).with(asUser(owner)))
                .andExpect(content().string(containsString("bí mật")));
    }

    @Test
    void unknownOrSuspendedUsersGive404() throws Exception {
        mvc.perform(get("/u/khong_ton_tai_12345")).andExpect(status().isNotFound());

        User suspended = createUser();
        suspended.setStatus(UserStatus.SUSPENDED);
        users.saveAndFlush(suspended);
        mvc.perform(get("/u/" + suspended.getUsername())).andExpect(status().isNotFound());
    }

    @Test
    void meRedirectsToOwnProfile() throws Exception {
        User user = createUser();
        mvc.perform(get("/me").with(asUser(user))).andExpect(redirectedUrl("/u/" + user.getUsername()));
    }

    @Test
    void profileNamesAreEscapedAgainstHtmlInjection() throws Exception {
        User user = createUser();
        user.getProfile().setDisplayName("<script>alert(1)</script>");
        user.getProfile().setBio("<img src=x onerror=alert(2)>");
        users.saveAndFlush(user);

        String html = mvc.perform(get("/u/" + user.getUsername())).andReturn().getResponse().getContentAsString();

        assertThat(html).doesNotContain("<script>alert(1)</script>").doesNotContain("<img src=x onerror");
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }
}
