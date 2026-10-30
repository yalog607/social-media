package com.aloute.user;

import com.aloute.model.user.User;
import com.aloute.service.social.BlockService;
import com.aloute.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Xem bạn bè / số người đang theo dõi: chủ hồ sơ luôn xem được, người khác chỉ khi được bật và không bị chặn. */
class ProfileListPrivacyIT extends IntegrationTest {

    private static final String FRIENDS_LINK = "data-bs-target=\"#friendsModal\"";
    private static final String FOLLOWING_LINK = "data-bs-target=\"#followingModal\"";

    @Autowired BlockService blocks;

    private User owner(boolean friendsVisible, boolean followingVisible) {
        User user = createUser();
        user.getProfile().setFriendsListVisible(friendsVisible);
        user.getProfile().setFollowingListVisible(followingVisible);
        users.saveAndFlush(user);
        return user;
    }

    @Test
    void defaultsAreOnAndCanBeTurnedOffInSettings() throws Exception {
        User user = createUser();
        assertThat(user.getProfile().isFriendsListVisible()).isTrue();
        assertThat(user.getProfile().isFollowingListVisible()).isTrue();

        mvc.perform(post("/settings/privacy").with(csrf()).with(asUser(user))
                        .param("profileVisibility", "PUBLIC").param("defaultPostVisibility", "PUBLIC")
                        .param("messagePermission", "EVERYONE")
                        .param("friendsListVisible", "false").param("followingListVisible", "false"))
                .andExpect(redirectedUrl("/settings"));

        var profile = users.findById(user.getId()).orElseThrow().getProfile();
        assertThat(profile.isFriendsListVisible()).isFalse();
        assertThat(profile.isFollowingListVisible()).isFalse();
    }

    @Test
    void strangerSeesBothWhenAllowed() throws Exception {
        User owner = owner(true, true);

        mvc.perform(get("/u/" + owner.getUsername()).with(asUser(createUser())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(FRIENDS_LINK)))
                .andExpect(content().string(containsString(FOLLOWING_LINK)));
        mvc.perform(get("/u/" + owner.getUsername() + "/friends-fragment").with(asUser(createUser())))
                .andExpect(status().isOk());
        mvc.perform(get("/u/" + owner.getUsername() + "/following-fragment").with(asUser(createUser())))
                .andExpect(status().isOk());
    }

    @Test
    void strangerSeesNeitherWhenDisallowedAndFragmentsAreForbidden() throws Exception {
        User owner = owner(false, false);
        User stranger = createUser();

        mvc.perform(get("/u/" + owner.getUsername()).with(asUser(stranger)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(FRIENDS_LINK))))
                .andExpect(content().string(not(containsString(FOLLOWING_LINK))));
        mvc.perform(get("/u/" + owner.getUsername() + "/friends-fragment").with(asUser(stranger)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/u/" + owner.getUsername() + "/following-fragment").with(asUser(stranger)))
                .andExpect(status().isForbidden());
    }

    @Test
    void settingsAreIndependent() throws Exception {
        User owner = owner(false, true);
        User stranger = createUser();

        mvc.perform(get("/u/" + owner.getUsername()).with(asUser(stranger)))
                .andExpect(content().string(not(containsString(FRIENDS_LINK))))
                .andExpect(content().string(containsString(FOLLOWING_LINK)));
        mvc.perform(get("/u/" + owner.getUsername() + "/following-fragment").with(asUser(stranger)))
                .andExpect(status().isOk());
    }

    @Test
    void ownerAlwaysSeesBothEvenWhenOff() throws Exception {
        User owner = owner(false, false);

        mvc.perform(get("/u/" + owner.getUsername()).with(asUser(owner)))
                .andExpect(content().string(containsString(FRIENDS_LINK)))
                .andExpect(content().string(containsString(FOLLOWING_LINK)));
        mvc.perform(get("/u/" + owner.getUsername() + "/friends-fragment").with(asUser(owner)))
                .andExpect(status().isOk());
        mvc.perform(get("/u/" + owner.getUsername() + "/following-fragment").with(asUser(owner)))
                .andExpect(status().isOk());
    }

    @Test
    void blockedViewerCannotFetchListsEvenIfAllowed() throws Exception {
        User owner = owner(true, true);
        User stranger = createUser();
        blocks.block(owner.getId(), stranger.getId());

        mvc.perform(get("/u/" + owner.getUsername() + "/friends-fragment").with(asUser(stranger)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/u/" + owner.getUsername() + "/following-fragment").with(asUser(stranger)))
                .andExpect(status().isForbidden());
    }
}
