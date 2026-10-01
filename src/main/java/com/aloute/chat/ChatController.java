package com.aloute.chat;

import com.aloute.common.SafeRedirect;
import com.aloute.post.PostView;
import com.aloute.security.AlouteUserPrincipal;
import com.aloute.social.FriendService;
import com.aloute.user.User;
import com.aloute.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.UUID;

/** Trang danh sách hội thoại, trang một hội thoại, và các form quản lý (bắt đầu chat, tạo/rời/thêm người vào nhóm). */
@Controller
public class ChatController {

    private final ChatService chats;
    private final MessageService messagesService;
    private final FriendService friends;
    private final UserRepository users;

    public ChatController(ChatService chats, MessageService messagesService, FriendService friends, UserRepository users) {
        this.chats = chats;
        this.messagesService = messagesService;
        this.friends = friends;
        this.users = users;
    }

    @GetMapping("/messages")
    public String list(@AuthenticationPrincipal AlouteUserPrincipal me, Model model) {
        model.addAttribute("conversations", chats.listFor(me.id()));
        model.addAttribute("friends", friends.friendsOf(me.id()));
        return "chat/list";
    }

    @GetMapping("/messages/{id}")
    public String view(@PathVariable UUID id, @AuthenticationPrincipal AlouteUserPrincipal me, Model model) {
        Conversation conversation = chats.requireMembership(me.id(), id);
        chats.markRead(me.id(), id);
        List<PostView.AuthorView> members = chats.membersOf(id);
        List<UUID> memberIds = members.stream().map(PostView.AuthorView::id).toList();
        model.addAttribute("conversation", conversation);
        model.addAttribute("header", chats.header(me.id(), conversation));
        model.addAttribute("members", members);
        model.addAttribute("page", messagesService.history(me.id(), id, null));
        model.addAttribute("friendsNotInGroup", friends.friendsOf(me.id()).stream()
                .filter(f -> !memberIds.contains(f.id())).toList());
        return "chat/conversation";
    }

    /** Mảnh HTML các tin cũ hơn con trỏ {@code before}, để chat.js chèn lên đầu khung chat khi cuộn lên. */
    @GetMapping("/api/conversations/{id}/messages")
    public String older(@PathVariable UUID id, @RequestParam(required = false) String before,
                        @AuthenticationPrincipal AlouteUserPrincipal me, Model model) {
        Conversation conversation = chats.requireMembership(me.id(), id);
        model.addAttribute("conversation", conversation);
        model.addAttribute("header", chats.header(me.id(), conversation));
        model.addAttribute("page", messagesService.history(me.id(), id, before));
        return "chat/messages :: list";
    }

    @PostMapping("/messages/start")
    public String start(@AuthenticationPrincipal AlouteUserPrincipal me, @RequestParam String username,
                        RedirectAttributes flash) {
        User target = users.findByUsername(username).filter(User::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        try {
            Conversation conversation = chats.startDirect(me.id(), target.getId());
            return "redirect:/messages/" + conversation.getId();
        } catch (ChatActionException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/messages";
        }
    }

    @PostMapping("/messages/group")
    public String createGroup(@AuthenticationPrincipal AlouteUserPrincipal me, @RequestParam String title,
                              @RequestParam(required = false) List<UUID> memberIds, RedirectAttributes flash) {
        try {
            Conversation conversation = chats.createGroup(me.id(), title, memberIds);
            return "redirect:/messages/" + conversation.getId();
        } catch (ChatActionException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/messages";
        }
    }

    @PostMapping("/messages/{id}/members")
    public String addMember(@PathVariable UUID id, @AuthenticationPrincipal AlouteUserPrincipal me,
                            @RequestParam UUID memberId, RedirectAttributes flash) {
        try {
            chats.addMember(me.id(), id, memberId);
        } catch (ChatActionException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/messages/" + id;
    }

    @PostMapping("/messages/{id}/leave")
    public String leave(@PathVariable UUID id, @AuthenticationPrincipal AlouteUserPrincipal me,
                        RedirectAttributes flash) {
        try {
            chats.leave(me.id(), id);
        } catch (ChatActionException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/messages/" + id;
        }
        return "redirect:" + SafeRedirect.sanitize("/messages");
    }
}
