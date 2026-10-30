package com.aloute.service.chat;

import com.aloute.model.user.User;
import com.aloute.model.user.UserStatus;
import com.aloute.model.user.MessagePermission;
import com.aloute.service.social.BlockService;
import com.aloute.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MessagesPeopleSearchIT extends IntegrationTest {
    @Autowired BlockService blocks;
    @Autowired ChatService chats;

    private User named(String name) {
        User user = createUser();
        user.getProfile().setDisplayName(name);
        return users.saveAndFlush(user);
    }

    @Test void searchesNamesAndUsernamesAndRejectsInvalidQueries() throws Exception {
        User me = createUser();
        User target = named("Nguyễn Đặc Biệt " + UUID.randomUUID().toString().substring(0, 8));
        mvc.perform(get("/api/messages/people").param("q", "NGUYEN DAC BIET").with(asUser(me)))
                .andExpect(status().isOk()).andExpect(content().string(containsString(target.getUsername())));
        mvc.perform(get("/api/messages/people").param("q", target.getUsername()).with(asUser(me)))
                .andExpect(status().isOk()).andExpect(content().string(containsString(target.getUsername())));
        for (String query : new String[]{"", "a", " ", "a".repeat(101), "missing" + UUID.randomUUID()}) {
            mvc.perform(get("/api/messages/people").param("q", query).with(asUser(me)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Test void filtersBeforeApplyingTwentyResultLimit() throws Exception {
        String query = "people" + UUID.randomUUID().toString().substring(0, 8);
        User me = named(query);
        User outgoing = named(query);
        User incoming = named(query);
        User inactive = named(query);
        inactive.setStatus(UserStatus.SUSPENDED);
        users.saveAndFlush(inactive);
        blocks.block(me.getId(), outgoing.getId());
        blocks.block(incoming.getId(), me.getId());
        for (int i = 0; i < 23; i++) named(query + " " + i);
        String body = mvc.perform(get("/api/messages/people").param("q", query).with(asUser(me)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(20))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(me.getUsername(), outgoing.getUsername(), incoming.getUsername(), inactive.getUsername());
    }

    @Test void treatsSearchWildcardsAsLiteralCharacters() throws Exception {
        User me = createUser();
        String prefix = "literal" + UUID.randomUUID().toString().substring(0, 8);
        User match = named(prefix + "_%");
        User decoy = named(prefix + "xy");
        mvc.perform(get("/api/messages/people").param("q", prefix + "_%").with(asUser(me)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(content().string(containsString(match.getUsername())))
                .andExpect(content().string(not(containsString(decoy.getUsername()))));
    }

    @Test void requiresLoginAndCsrfAndHandlesUnavailableTargets() throws Exception {
        mvc.perform(get("/api/messages/people").param("q", "test")).andExpect(status().isUnauthorized());
        User me = createUser();
        User target = createUser();
        mvc.perform(post("/messages/start").param("username", target.getUsername()).with(asUser(me)))
                .andExpect(status().isForbidden());
        for (String username : new String[]{"missing" + UUID.randomUUID(), target.getUsername()}) {
            target.setStatus(UserStatus.SUSPENDED);
            users.saveAndFlush(target);
            mvc.perform(post("/messages/start").param("username", username).with(csrf()).with(asUser(me)))
                    .andExpect(redirectedUrl("/messages")).andExpect(flash().attributeExists("error"));
        }
    }

    @Test void preservesNewChatRulesAndReopensExistingChats() throws Exception {
        User me = createUser();
        User target = createUser();
        var direct = chats.startDirect(me.getId(), target.getId());
        blocks.block(target.getId(), me.getId());
        target.getProfile().setMessagePermission(MessagePermission.NOBODY);
        users.saveAndFlush(target);
        mvc.perform(post("/messages/start").param("username", target.getUsername()).with(csrf()).with(asUser(me)))
                .andExpect(redirectedUrl("/messages/" + direct.getId()));
        User stranger = createUser();
        mvc.perform(post("/messages/start").param("username", target.getUsername()).with(csrf()).with(asUser(stranger)))
                .andExpect(redirectedUrl("/messages")).andExpect(flash().attributeExists("error"));
        blocks.block(target.getId(), stranger.getId());
        mvc.perform(post("/messages/start").param("username", target.getUsername()).with(csrf()).with(asUser(stranger)))
                .andExpect(redirectedUrl("/messages")).andExpect(flash().attributeExists("error"));
    }

    @Test void blocksNewChatsInEitherDirectionEvenWhenEveryoneCanMessage() throws Exception {
        User me = createUser();
        User outgoing = createUser();
        User incoming = createUser();
        blocks.block(me.getId(), outgoing.getId());
        blocks.block(incoming.getId(), me.getId());
        for (User target : new User[]{outgoing, incoming}) {
            assertThat(target.getProfile().getMessagePermission()).isEqualTo(MessagePermission.EVERYONE);
            mvc.perform(post("/messages/start").param("username", target.getUsername()).with(csrf()).with(asUser(me)))
                    .andExpect(redirectedUrl("/messages")).andExpect(flash().attributeExists("error"));
        }
    }

    @Test void rendersSearchAndFriendsOnlyGroupEmptyState() throws Exception {
        mvc.perform(get("/messages").with(asUser(createUser())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("id=\"messagesPeopleQuery\"")))
                .andExpect(content().string(containsString("Bạn cần có bạn bè để tạo nhóm trò chuyện.")))
                .andExpect(content().string(not(containsString("name=\"memberIds\""))));
    }
}
