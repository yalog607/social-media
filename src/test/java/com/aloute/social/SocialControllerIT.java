package com.aloute.social;

import com.aloute.support.IntegrationTest;
import com.aloute.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phần 3a: kết bạn/theo dõi/chặn qua HTTP. */
class SocialControllerIT extends IntegrationTest {

    @Autowired FriendService friendService;
    @Autowired FollowService followService;

    @Test
    void sendingAFriendRequestRedirectsBackAndPersists() throws Exception {
        User a = createUser();
        User b = createUser();

        mvc.perform(post("/u/" + b.getUsername() + "/friend-request").with(csrf()).with(asUser(a))
                        .param("next", "/u/" + b.getUsername()))
                .andExpect(status().is3xxRedirection());

        org.assertj.core.api.Assertions.assertThat(friendService.stateBetween(a.getId(), b.getId())).isEqualTo(FriendState.OUTGOING);
    }

    @Test
    void invalidRequestComesBackAsAFlashErrorNotA500() throws Exception {
        User a = createUser();

        mvc.perform(post("/u/" + a.getUsername() + "/friend-request").with(csrf()).with(asUser(a)).param("next", "/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void guestCannotSendAFriendRequest() throws Exception {
        User b = createUser();

        mvc.perform(post("/u/" + b.getUsername() + "/friend-request").with(csrf()).param("next", "/"))
                .andExpect(status().is3xxRedirection());
        org.assertj.core.api.Assertions.assertThat(friendService.incomingRequests(b.getId())).isEmpty();
    }

    @Test
    void rejectsWithoutCsrfToken() throws Exception {
        User a = createUser();
        User b = createUser();

        mvc.perform(post("/u/" + b.getUsername() + "/follow").with(asUser(a)).param("next", "/"))
                .andExpect(status().isForbidden());
    }

    @Test
    void followThenUnfollow() throws Exception {
        User a = createUser();
        User b = createUser();

        mvc.perform(post("/u/" + b.getUsername() + "/follow").with(csrf()).with(asUser(a)).param("next", "/"))
                .andExpect(status().is3xxRedirection());
        org.assertj.core.api.Assertions.assertThat(followService.isFollowing(a.getId(), b.getId())).isTrue();

        mvc.perform(post("/u/" + b.getUsername() + "/unfollow").with(csrf()).with(asUser(a)).param("next", "/"))
                .andExpect(status().is3xxRedirection());
        org.assertj.core.api.Assertions.assertThat(followService.isFollowing(a.getId(), b.getId())).isFalse();
    }

    @Test
    void blockingHidesTheirProfile() throws Exception {
        User a = createUser();
        User b = createUser();

        mvc.perform(post("/u/" + b.getUsername() + "/block").with(csrf()).with(asUser(a)).param("next", "/"))
                .andExpect(status().is3xxRedirection());

        mvc.perform(get("/u/" + b.getUsername()).with(asUser(a)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bạn đã chặn người này")));
    }

    @Test
    void friendsPageRequiresLogin() throws Exception {
        mvc.perform(get("/friends")).andExpect(status().is3xxRedirection());
    }

    @Test
    void friendsPageListsIncomingRequests() throws Exception {
        User a = createUser();
        User b = createUser();
        friendService.sendRequest(a.getId(), b.getId());

        mvc.perform(get("/friends").with(asUser(b)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(a.getUsername())));
    }
}
