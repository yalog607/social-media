package com.aloute.controller.chat;

import com.aloute.exception.chat.ChatActionException;
import com.aloute.exception.chat.ConversationNotFoundException;
import com.aloute.model.chat.Conversation;
import com.aloute.model.chat.GroupRole;
import com.aloute.service.chat.ChatService;
import com.aloute.service.chat.MessageService;
import com.aloute.service.chat.StreakService;
import com.aloute.service.search.SearchService;
import com.aloute.util.chat.ChatLimits;

import com.aloute.util.common.SafeRedirect;
import com.aloute.dto.post.PostView;
import com.aloute.security.AlouteUserPrincipal;
import com.aloute.service.social.FriendService;
import com.aloute.model.user.User;
import com.aloute.repository.user.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
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
    private final StreakService streaks;
    private final SearchService peopleSearch;

    public ChatController(ChatService chats, MessageService messagesService, FriendService friends, UserRepository users,
                          StreakService streaks, SearchService peopleSearch) {
        this.peopleSearch = peopleSearch;
        this.streaks = streaks;
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
    public String view(@PathVariable UUID id, @AuthenticationPrincipal AlouteUserPrincipal me, RedirectAttributes flash, Model model) {
        if (!chats.isMember(me.id(), id)) {
            if (chats.exists(id)) {
                return "chat/not_member";
            } else {
                throw new ConversationNotFoundException();
            }
        }
        Conversation conversation = chats.requireMembership(me.id(), id);
        if (conversation.isSocialGroup() && !conversation.isChatEnabled()) {
            flash.addFlashAttribute("notice", "Hội nhóm này hiện chưa mở phòng chat.");
            return "redirect:/groups/" + id;
        }
        chats.markRead(me.id(), id);
        model.addAttribute("unreadMessages", chats.totalUnread(me.id()));
        List<PostView.AuthorView> members = chats.membersOf(id);
        List<UUID> memberIds = members.stream().map(PostView.AuthorView::id).toList();
        model.addAttribute("conversation", conversation);
        model.addAttribute("header", chats.header(me.id(), conversation));
        model.addAttribute("members", members);
        model.addAttribute("memberViews", chats.memberViews(id));
        GroupRole myRole = chats.roleOf(me.id(), id);
        model.addAttribute("myRole", myRole);
        model.addAttribute("canManage", conversation.isGroup() && myRole.canManage());
        model.addAttribute("isOwner", conversation.isGroup() && myRole == GroupRole.OWNER);
        model.addAttribute("canSendMessages", !conversation.isGroup() || conversation.isMembersCanSendMessages() || myRole.canManage());
        model.addAttribute("maxNickname", ChatService.MAX_NICKNAME);
        model.addAttribute("maxAttachmentMb", ChatLimits.MAX_ATTACHMENT_BYTES / (1024 * 1024));
        model.addAttribute("streak", streaks.forConversation(id));
        model.addAttribute("page", messagesService.history(me.id(), id, null));
        model.addAttribute("pinnedMessages", messagesService.getPinnedMessages(me.id(), id));
        model.addAttribute("mediaMessages", messagesService.getMediaMessages(me.id(), id));
        model.addAttribute("linkMessages", messagesService.getLinkMessages(me.id(), id));
        model.addAttribute("friendsNotInGroup", friends.friendsOf(me.id()).stream()
                .filter(f -> !memberIds.contains(f.id())).toList());
        
        if (model.getAttribute("canManage") == Boolean.TRUE) {
            model.addAttribute("pendingRequests", chats.getPendingRequests(me.id(), id));
        }
        
        return "chat/conversation";
    }

    @GetMapping("/api/messages/people")
    @ResponseBody
    public List<PostView.AuthorView> people(@AuthenticationPrincipal AlouteUserPrincipal me,
                                           @RequestParam(defaultValue = "") String q) {
        return peopleSearch.searchMessagePeople(q, me.id());
    }

    /** Mảnh HTML các tin cũ hơn con trỏ {@code before}, để chat.js chèn lên đầu khung chat khi cuộn lên. */
    @GetMapping("/api/conversations/{id}/messages")
    public String older(@PathVariable UUID id, @RequestParam(required = false) String before,
                        @AuthenticationPrincipal AlouteUserPrincipal me, RedirectAttributes flash, Model model) {
        Conversation conversation = chats.requireMembership(me.id(), id);
        if (conversation.isSocialGroup() && !conversation.isChatEnabled()) {
            flash.addFlashAttribute("notice", "Hội nhóm này hiện chưa mở phòng chat.");
            return "redirect:/groups/" + id;
        }
        model.addAttribute("conversation", conversation);
        model.addAttribute("header", chats.header(me.id(), conversation));
        model.addAttribute("page", messagesService.history(me.id(), id, before));
        return "chat/messages :: list";
    }

    /** Mảnh HTML kết quả tìm kiếm tin nhắn. */
    @GetMapping("/api/conversations/{id}/search")
    public String search(@PathVariable UUID id, @RequestParam String keyword,
                         @AuthenticationPrincipal AlouteUserPrincipal me, RedirectAttributes flash, Model model) {
        Conversation conversation = chats.requireMembership(me.id(), id);
        if (conversation.isSocialGroup() && !conversation.isChatEnabled()) {
            flash.addFlashAttribute("notice", "Hội nhóm này hiện chưa mở phòng chat.");
            return "redirect:/groups/" + id;
        }
        model.addAttribute("conversation", conversation);
        model.addAttribute("header", chats.header(me.id(), conversation));
        model.addAttribute("searchResults", messagesService.searchMessages(me.id(), id, keyword));
        return "chat/search :: results";
    }

    @RequestMapping(value = "/messages/start", method = {RequestMethod.GET, RequestMethod.POST})
    public String start(@AuthenticationPrincipal AlouteUserPrincipal me, @RequestParam String username,
                        RedirectAttributes flash) {
        User target = users.findByUsername(username).filter(User::isActive).orElse(null);
        if (target == null) {
            flash.addFlashAttribute("error", "Người này không còn hoạt động hoặc không còn tồn tại. Hãy tìm người khác nhé.");
            return "redirect:/messages";
        }
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
                        @RequestParam(required = false) UUID nextOwnerId,
                        RedirectAttributes flash) {
        try {
            chats.leave(me.id(), id, nextOwnerId);
        } catch (ChatActionException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/messages/" + id;
        }
        return "redirect:" + SafeRedirect.sanitize("/messages");
    }

    // ---------- Quản lý nhóm ----------

    @PostMapping("/messages/{id}/title")
    public String rename(@PathVariable UUID id, @AuthenticationPrincipal AlouteUserPrincipal me,
                         @RequestParam String title, RedirectAttributes flash) {
        return manage(id, flash, null, () -> chats.rename(me.id(), id, title));
    }

    @PostMapping("/messages/{id}/avatar")
    public String avatar(@PathVariable UUID id, @AuthenticationPrincipal AlouteUserPrincipal me,
                         @RequestParam MultipartFile avatar, RedirectAttributes flash) {
        return manage(id, flash, null, () -> chats.setAvatar(me.id(), id, avatar));
    }

    @PostMapping("/messages/{id}/members/{userId}/role")
    public String role(@PathVariable UUID id, @PathVariable UUID userId, @AuthenticationPrincipal AlouteUserPrincipal me,
                       @RequestParam GroupRole role, RedirectAttributes flash) {
        return manage(id, flash, null, () -> chats.setRole(me.id(), id, userId, role));
    }

    @PostMapping("/messages/{id}/members/{userId}/owner")
    public String owner(@PathVariable UUID id, @PathVariable UUID userId, @AuthenticationPrincipal AlouteUserPrincipal me,
                        RedirectAttributes flash) {
        return manage(id, flash, null, () -> chats.transferOwnership(me.id(), id, userId));
    }

    @PostMapping("/messages/{id}/members/{userId}/remove")
    public String removeMember(@PathVariable UUID id, @PathVariable UUID userId, @AuthenticationPrincipal AlouteUserPrincipal me,
                               RedirectAttributes flash) {
        return manage(id, flash, null, () -> chats.removeMember(me.id(), id, userId));
    }

    @PostMapping("/messages/{id}/members/{userId}/nickname")
    public String nickname(@PathVariable UUID id, @PathVariable UUID userId, @AuthenticationPrincipal AlouteUserPrincipal me,
                           @RequestParam(required = false) String nickname, RedirectAttributes flash) {
        return manage(id, flash, null, () -> chats.setNickname(me.id(), id, userId, nickname));
    }

    @PostMapping("/messages/{id}/nickname-permission")
    public String nicknamePermission(@PathVariable UUID id, @AuthenticationPrincipal AlouteUserPrincipal me,
                                     @RequestParam(required = false) boolean allow, RedirectAttributes flash) {
        return manage(id, flash, null, () -> chats.setAllowAnyoneChangeNickname(me.id(), id, allow));
    }

    @PostMapping("/messages/{id}/sending-permission")
    public String sendingPermission(@PathVariable UUID id, @AuthenticationPrincipal AlouteUserPrincipal me,
                                    @RequestParam(defaultValue = "false") boolean allow, RedirectAttributes flash) {
        return manage(id, flash, "Đã cập nhật quyền gửi tin nhắn.", () -> chats.setMembersCanSendMessages(me.id(), id, allow));
    }

    @PostMapping("/messages/{id}/pins/{messageId}")
    public String togglePin(@PathVariable UUID id, @PathVariable UUID messageId, @AuthenticationPrincipal AlouteUserPrincipal me,
                            @RequestParam boolean pin, RedirectAttributes flash) {
        return manage(id, flash, null, () -> messagesService.togglePin(me.id(), messageId, pin));
    }

    @PostMapping("/messages/{id}/delete/{messageId}")
    public String deleteMessage(@PathVariable UUID id, @PathVariable UUID messageId, @AuthenticationPrincipal AlouteUserPrincipal me,
                                RedirectAttributes flash) {
        return manage(id, flash, "Đã xóa tin nhắn.", () -> messagesService.deleteMessage(me.id(), messageId));
    }

    @PostMapping("/messages/{id}/edit/{messageId}")
    public String editMessage(@PathVariable UUID id, @PathVariable UUID messageId, @AuthenticationPrincipal AlouteUserPrincipal me,
                              @RequestParam String content, RedirectAttributes flash) {
        return manage(id, flash, "Đã sửa tin nhắn.", () -> messagesService.editMessage(me.id(), messageId, content));
    }

    @PostMapping("/messages/{id}/disband")
    public String disband(@PathVariable UUID id, @AuthenticationPrincipal AlouteUserPrincipal me, RedirectAttributes flash) {
        try {
            chats.disband(me.id(), id);
            flash.addFlashAttribute("notice", "Đã giải tán nhóm.");
        } catch (ChatActionException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/messages/" + id;
        }
        return "redirect:/messages";
    }

    @PostMapping("/messages/{id}/approval")
    public String approval(@PathVariable UUID id, @AuthenticationPrincipal AlouteUserPrincipal me,
                           @RequestParam(defaultValue = "false") boolean require, RedirectAttributes flash) {
        return manage(id, flash, null, () -> chats.toggleApproval(me.id(), id, require));
    }

    private static String manage(UUID id, RedirectAttributes flash, String success, Runnable action) {
        try {
            action.run();
            if (success != null) {
                flash.addFlashAttribute("chatNotice", success);
            }
        } catch (ChatActionException | com.aloute.exception.media.InvalidMediaException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/messages/" + id;
    }

    @PostMapping("/messages/{id}/requests/{uid}/approve")
    public String approveRequest(@PathVariable UUID id, @PathVariable UUID uid,
                                 @AuthenticationPrincipal AlouteUserPrincipal me) {
        chats.approveRequest(me.id(), id, uid);
        return "redirect:/messages/" + id;
    }
    
    @PostMapping("/messages/{id}/requests/{uid}/reject")
    public String rejectRequest(@PathVariable UUID id, @PathVariable UUID uid,
                                @AuthenticationPrincipal AlouteUserPrincipal me) {
        chats.rejectRequest(me.id(), id, uid);
        return "redirect:/messages/" + id;
    }
}
