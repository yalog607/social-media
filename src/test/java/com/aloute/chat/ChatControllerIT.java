package com.aloute.chat;

import com.aloute.social.FriendService;
import com.aloute.support.IntegrationTest;
import com.aloute.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phần 3c: trang tin nhắn và các thao tác quản lý hội thoại qua HTTP. */
class ChatControllerIT extends IntegrationTest {

    @Autowired ChatService chats;
    @Autowired FriendService friends;

    private void makeFriends(User a, User b) {
        friends.sendRequest(a.getId(), b.getId());
        friends.accept(b.getId(), a.getId());
    }

    @Test
    void guestCannotSeeMessages() throws Exception {
        mvc.perform(get("/messages")).andExpect(status().is3xxRedirection());
    }

    @Test
    void listsMyConversations() throws Exception {
        User me = createUser();
        User friend = createUser();
        makeFriends(me, friend);
        chats.startDirect(me.getId(), friend.getId());

        mvc.perform(get("/messages").with(asUser(me)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(friend.getProfile().getDisplayName())));
    }

    @Test
    void startingAConversationRedirectsToIt() throws Exception {
        User me = createUser();
        User friend = createUser();

        mvc.perform(post("/messages/start").param("username", friend.getUsername()).with(csrf()).with(asUser(me)))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void viewingAConversationRendersItsHistory() throws Exception {
        User me = createUser();
        User friend = createUser();
        makeFriends(me, friend);
        Conversation direct = chats.startDirect(me.getId(), friend.getId());

        mvc.perform(get("/messages/" + direct.getId()).with(asUser(me)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(friend.getProfile().getDisplayName())));
    }

    /**
     * Trang một hội thoại đặt các script realtime (SockJS/STOMP/chat.js) sau thẻ {@code </main>} trong file nguồn,
     * nhưng {@code th:replace} trên thẻ {@code <html>} chỉ mang theo phần được chọn bởi {@code ~{::main}} — bất cứ
     * thứ gì nằm ngoài main sẽ âm thầm biến mất khỏi trang thật, nên phải kiểm tra chúng thật sự có mặt trong HTML.
     */
    @Test
    void conversationPageActuallyLoadsTheRealtimeScripts() throws Exception {
        User me = createUser();
        User friend = createUser();
        makeFriends(me, friend);
        Conversation direct = chats.startDirect(me.getId(), friend.getId());

        mvc.perform(get("/messages/" + direct.getId()).with(asUser(me)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("sockjs.min.js")))
                .andExpect(content().string(containsString("stomp.min.js")))
                .andExpect(content().string(containsString("/js/chat.js")));
    }

    @Test
    void nonMemberGetsNotFound() throws Exception {
        User a = createUser();
        User b = createUser();
        User stranger = createUser();
        Conversation direct = chats.startDirect(a.getId(), b.getId());

        mvc.perform(get("/messages/" + direct.getId()).with(asUser(stranger)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createsAGroupWithFriends() throws Exception {
        User owner = createUser();
        User friend = createUser();
        makeFriends(owner, friend);

        mvc.perform(post("/messages/group").param("title", "Nhóm vui vẻ").param("memberIds", friend.getId().toString())
                        .with(csrf()).with(asUser(owner)))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void sendingAMessageViaAjaxWorks() throws Exception {
        User me = createUser();
        User friend = createUser();
        makeFriends(me, friend);
        Conversation direct = chats.startDirect(me.getId(), friend.getId());

        mvc.perform(post("/api/conversations/" + direct.getId() + "/messages")
                        .param("content", "Chào cậu!").with(csrf()).with(asUser(me)))
                .andExpect(status().isOk());
    }

    @Test
    void sendingAMessageWithAnAttachmentWorks() throws Exception {
        User me = createUser();
        User friend = createUser();
        makeFriends(me, friend);
        Conversation direct = chats.startDirect(me.getId(), friend.getId());
        MockMultipartFile file = new MockMultipartFile("attachment", "note.txt", "text/plain", "hi".getBytes());

        mvc.perform(multipart("/api/conversations/" + direct.getId() + "/messages").file(file)
                        .with(csrf()).with(asUser(me)))
                .andExpect(status().isOk());
    }

    @Test
    void leavingAGroupRedirectsToTheList() throws Exception {
        User owner = createUser();
        User friend = createUser();
        makeFriends(owner, friend);
        Conversation group = chats.createGroup(owner.getId(), "Nhóm", java.util.List.of(friend.getId()));

        mvc.perform(post("/messages/" + group.getId() + "/leave").with(csrf()).with(asUser(friend)))
                .andExpect(redirectedUrl("/messages"));
    }
}
