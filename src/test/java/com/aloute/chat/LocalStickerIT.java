package com.aloute.chat;

import com.aloute.exception.chat.ChatActionException;
import com.aloute.exception.chat.ConversationNotFoundException;
import com.aloute.model.chat.Conversation;
import com.aloute.model.user.User;
import com.aloute.service.chat.ChatService;
import com.aloute.service.chat.MessageService;
import com.aloute.service.social.FriendService;
import com.aloute.service.sticker.LocalStickerLibrary;
import com.aloute.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Timestamp;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LocalStickerIT extends IntegrationTest {
    @Autowired ChatService chats;
    @Autowired MessageService messages;
    @Autowired JdbcTemplate jdbc;
    @Autowired FriendService friends;
    @Autowired LocalStickerLibrary library;

    @Test
    void persistsLocalStickerAndRejectsInvalidOrUnauthorizedSends() throws Exception {
        User a = createUser(), b = createUser(), outsider = createUser();
        Conversation conversation = chats.startDirect(a.getId(), b.getId());
        var sent = messages.sendSticker(a.getId(), conversation.getId(), "aloute-cat-0", null);
        assertThat(sent.sticker().id()).isEqualTo("aloute-cat-0");
        assertThat(sent.giphyId()).isNull();

        assertThat(messages.history(b.getId(), conversation.getId(), null).messages())
                .extracting(m -> m.sticker().id()).contains("aloute-cat-0");
        assertThat(chats.listFor(b.getId())).filteredOn(c -> c.id().equals(conversation.getId()))
                .singleElement().satisfies(c -> assertThat(c.lastMessagePreview()).contains("nhãn dán"));
        assertThatThrownBy(() -> messages.sendSticker(a.getId(), conversation.getId(), "https://invalid.example/x", null))
                .isInstanceOf(ChatActionException.class);
        assertThatThrownBy(() -> messages.sendSticker(outsider.getId(), conversation.getId(), "aloute-cat-0", null))
                .isInstanceOf(ConversationNotFoundException.class);
        mvc.perform(get("/messages/" + conversation.getId()).with(asUser(b))).andExpect(status().isOk())
                .andExpect(content().string(containsString("sticker-art--message")))
                .andExpect(content().string(containsString("message-art-" + sent.id())))
                .andExpect(content().string(not(containsString("background-size:400%"))))
                .andExpect(content().string(not(containsString("stickerStoreModal"))));
    }

    @Test
    void legacyStickerImageIsHiddenWithoutDeletingTheMessage() throws Exception {
        User a = createUser(), b = createUser();
        Conversation conversation = chats.startDirect(a.getId(), b.getId());
        UUID id = UUID.randomUUID();
        jdbc.update("insert into messages (id, conversation_id, sender_id, content, sticker_id, created_at) values (?, ?, ?, ?, ?, ?)",
                id, conversation.getId(), a.getId(), "Lời nhắn cũ", "meo_chao", Timestamp.from(clock.instant()));
        mvc.perform(get("/messages/" + conversation.getId()).with(asUser(b))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Nhãn dán cũ không còn hiển thị")))
                .andExpect(content().string(containsString("Lời nhắn cũ")))
                .andExpect(content().string(not(containsString("/stickers/meo-cute/meo_chao.svg"))));
        assertThat(jdbc.queryForObject("select count(*) from messages where id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void groupMembersCanSendLocalStickerThroughChatEndpoint() throws Exception {
        User a = createUser(), b = createUser();
        friends.sendRequest(a.getId(), b.getId());
        friends.accept(b.getId(), a.getId());
        Conversation group = chats.createGroup(a.getId(), "Nhóm", java.util.List.of(b.getId()));
        mvc.perform(post("/api/conversations/" + group.getId() + "/stickers")
                .with(asUser(b)).with(csrf()).param("stickerId", "aloute-dog-0"))
                .andExpect(status().isOk());
        assertThat(messages.history(a.getId(), group.getId(), null).messages())
                .filteredOn(m -> m.sticker() != null).extracting(m -> m.sticker().id()).contains("aloute-dog-0");
    }

    @Test
    void favoritesAndRecentAreSavedPerAccountWithoutDuplicates() throws Exception {
        User a = createUser(), b = createUser();
        Conversation c = chats.startDirect(a.getId(), b.getId());
        library.favorite(a.getId(), "aloute-frog-1", true);
        library.favorite(a.getId(), "aloute-frog-1", true);
        assertThat(library.library(a.getId()).favorites()).containsExactly("aloute-frog-1");
        assertThat(library.library(b.getId()).favorites()).isEmpty();
        messages.sendSticker(a.getId(), c.getId(), "aloute-cat-0", null);
        messages.sendSticker(a.getId(), c.getId(), "aloute-cat-0", null);
        assertThat(library.library(a.getId()).recent()).containsExactly("aloute-cat-0");
        assertThat(library.library(b.getId()).recent()).isEmpty();
        library.favorite(a.getId(), "aloute-frog-1", false);
        assertThat(library.library(a.getId()).favorites()).isEmpty();
        mvc.perform(get("/api/stickers/library").with(asUser(a))).andExpect(status().isOk())
                .andExpect(jsonPath("$.packs.length()").value(3))
                .andExpect(jsonPath("$.packs[0].stickers[0].spriteIndex").value(0))
                .andExpect(jsonPath("$.packs[0].stickers[0].artwork.viewBox").value("9 12 245 236"))
                .andExpect(jsonPath("$.packs[0].stickers[0].artwork.clipPoints").isNotEmpty());
    }
}
