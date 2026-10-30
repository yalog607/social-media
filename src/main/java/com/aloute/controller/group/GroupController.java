package com.aloute.controller.group;

import com.aloute.dto.feed.FeedPage;
import com.aloute.dto.group.GroupMemberView;
import com.aloute.dto.group.GroupReportView;
import com.aloute.dto.group.GroupRequestView;
import com.aloute.dto.group.GroupView;
import com.aloute.dto.post.PostView;
import com.aloute.exception.chat.ChatActionException;
import com.aloute.model.chat.Conversation;
import com.aloute.model.chat.GroupRole;
import com.aloute.model.post.Post;
import com.aloute.security.AlouteUserPrincipal;
import com.aloute.service.feed.FeedService;
import com.aloute.service.group.GroupService;
import com.aloute.service.post.PostService;
import com.aloute.service.social.FriendService;
import com.aloute.util.media.MediaLimits;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/groups")
public class GroupController {

    private final GroupService groupService;
    private final FriendService friendService;
    private final FeedService feedService;

    public GroupController(GroupService groupService, FriendService friendService, FeedService feedService) {
        this.groupService = groupService;
        this.friendService = friendService;
        this.feedService = feedService;
    }

    @GetMapping
    public String index(@AuthenticationPrincipal AlouteUserPrincipal me,
                        @RequestParam(required = false) String tab,
                        @RequestParam(required = false) String q,
                        Model model) {
        List<GroupView> myGroups = groupService.listMyGroups(me.id());
        List<GroupView> discoverGroups = groupService.listDiscoverGroups(me.id(), q);

        String activeTab = tab;
        if (activeTab == null || activeTab.isBlank()) {
            activeTab = myGroups.isEmpty() ? "discover" : "my";
        }

        model.addAttribute("active", "groups");
        model.addAttribute("tab", activeTab);
        model.addAttribute("q", q != null ? q : "");
        model.addAttribute("myGroups", myGroups);
        model.addAttribute("discoverGroups", discoverGroups);
        model.addAttribute("friends", friendService.friendsOf(me.id()));

        return "groups/list";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable UUID id,
                       @RequestParam(defaultValue = "feed") String tab,
                       @AuthenticationPrincipal AlouteUserPrincipal me,
                       Model model) {
        UUID userId = me != null ? me.id() : null;
        GroupView group;
        try { group = groupService.getGroup(id, userId); }
        catch (ChatActionException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }

        String activeTab = tab;
        if (!"members".equals(activeTab) && !"manage".equals(activeTab)) {
            activeTab = "feed";
        }

        model.addAttribute("active", "groups");
        model.addAttribute("tab", activeTab);
        model.addAttribute("group", group);
        model.addAttribute("groupId", id);
        model.addAttribute("isMember", group.isMember());
        model.addAttribute("myRole", group.myRole());
        model.addAttribute("isPending", group.isPending());
        model.addAttribute("memberCount", group.memberCount());

        boolean canModerate = group.myRole() != null && group.myRole().canModerate();
        boolean canManage = group.myRole() != null && group.myRole().canManage();
        model.addAttribute("canModerate", canModerate);
        model.addAttribute("canManage", canManage);

        if (canModerate) {
            model.addAttribute("pendingCount", groupService.countPendingRequests(id));
            model.addAttribute("reportCount", groupService.countReports(id));
        }

        if ("members".equals(activeTab)) {
            List<GroupMemberView> groupMembers = groupService.listMembers(id);
            model.addAttribute("groupMembers", groupMembers);
        } else if ("manage".equals(activeTab)) {
            if (!canModerate) {
                return "redirect:/groups/" + id;
            }
            List<GroupRequestView> pendingRequests = groupService.listPendingRequests(userId, id);
            List<GroupReportView> reports = groupService.listReports(userId, id);
            model.addAttribute("pendingRequests", pendingRequests);
            model.addAttribute("reports", reports);
        } else {
            // tab == 'feed'
            List<PostView> pinnedPosts = feedService.pinnedByGroup(id, userId);
            FeedPage page = feedService.byGroup(id, userId, null);
            model.addAttribute("pinnedPosts", pinnedPosts);
            model.addAttribute("page", page);
            model.addAttribute("moreUrl", "/groups/" + id + "/feed");

            model.addAttribute("mediaLimits", Map.of(
                    "maxImages", MediaLimits.MAX_IMAGES,
                    "maxImageMb", MediaLimits.MAX_IMAGE_BYTES / (1024 * 1024),
                    "maxVideoMb", MediaLimits.MAX_VIDEO_BYTES / (1024 * 1024),
                    "maxChars", Post.MAX_CONTENT_LENGTH));

            if (me != null) {
                model.addAttribute("tagFriends", friendService.friendsOf(me.id()));
            }
            model.addAttribute("maxTags", PostService.MAX_TAGS);
        }

        return "groups/view";
    }

    @GetMapping("/{id}/feed")
    public String feed(@PathVariable UUID id,
                       @RequestParam(required = false) String cursor,
                       @AuthenticationPrincipal AlouteUserPrincipal me,
                       Model model) {
        UUID userId = me != null ? me.id() : null;
        FeedPage page = feedService.byGroup(id, userId, cursor);
        model.addAttribute("page", page);
        model.addAttribute("moreUrl", "/groups/" + id + "/feed");
        return "feed/list :: list";
    }

    @PostMapping("/create")
    public String create(@AuthenticationPrincipal AlouteUserPrincipal me,
                         @RequestParam String title,
                         @RequestParam(required = false) String description,
                         @RequestParam(defaultValue = "false") boolean approvalRequired,
                         @RequestParam(required = false) List<UUID> memberIds,
                         @RequestParam(required = false) MultipartFile avatar,
                         RedirectAttributes redirectAttributes) {
        try {
            Conversation c = groupService.createGroup(me.id(), title, description, approvalRequired, memberIds, avatar);
            redirectAttributes.addFlashAttribute("notice", "Tạo nhóm thành công!");
            return "redirect:/groups/" + c.getId();
        } catch (ChatActionException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/groups";
        }
    }

    @PostMapping("/{id}/join")
    public String join(@AuthenticationPrincipal AlouteUserPrincipal me,
                       @PathVariable UUID id,
                       @RequestParam(required = false) String answer,
                       @RequestParam(required = false) String redirectUrl,
                       RedirectAttributes redirectAttributes) {
        try {
            boolean pending = groupService.joinGroup(me.id(), id, answer);
            if (pending) {
                redirectAttributes.addFlashAttribute("notice", "Đã gửi câu trả lời và yêu cầu tham gia nhóm, vui lòng chờ ban quản trị phê duyệt.");
            } else {
                redirectAttributes.addFlashAttribute("notice", "Đã tham gia nhóm thành công!");
            }
        } catch (ChatActionException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        if (redirectUrl != null && !redirectUrl.isBlank()) {
            return "redirect:" + redirectUrl;
        }
        return "redirect:/groups/" + id;
    }

    @PostMapping("/{id}/cancel-request")
    public String cancelRequest(@AuthenticationPrincipal AlouteUserPrincipal me,
                                @PathVariable UUID id,
                                @RequestParam(required = false) String redirectUrl,
                                RedirectAttributes redirectAttributes) {
        groupService.cancelRequest(me.id(), id);
        redirectAttributes.addFlashAttribute("notice", "Đã hủy yêu cầu tham gia nhóm.");
        if (redirectUrl != null && !redirectUrl.isBlank()) {
            return "redirect:" + redirectUrl;
        }
        return "redirect:/groups/" + id;
    }

    @PostMapping("/{id}/leave")
    public String leave(@AuthenticationPrincipal AlouteUserPrincipal me,
                        @PathVariable UUID id,
                        @RequestParam(required = false) UUID nextOwnerId,
                        RedirectAttributes redirectAttributes) {
        try {
            groupService.leaveGroup(me.id(), id, nextOwnerId);
            redirectAttributes.addFlashAttribute("notice", "Bạn đã rời khỏi nhóm.");
            return "redirect:/groups";
        } catch (ChatActionException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/groups/" + id;
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal AlouteUserPrincipal me, @PathVariable UUID id, RedirectAttributes flash) {
        try {
            groupService.deleteGroup(me.id(), id);
            flash.addFlashAttribute("notice", "Đã xóa hội nhóm.");
            return "redirect:/groups";
        } catch (ChatActionException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/groups";
        }
    }

    @PostMapping("/{id}/toggle-chat")
    public String toggleChat(@AuthenticationPrincipal AlouteUserPrincipal me,
                             @PathVariable UUID id,
                             RedirectAttributes redirectAttributes) {
        try {
            boolean enabled = groupService.toggleChat(me.id(), id);
            if (enabled) {
                redirectAttributes.addFlashAttribute("notice", "Đã mở phòng chat cho nhóm! Các thành viên giờ đây có thể nhắn tin.");
            } else {
                redirectAttributes.addFlashAttribute("notice", "Đã đóng phòng chat của nhóm.");
            }
        } catch (ChatActionException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/groups/" + id;
    }

    @PostMapping("/{id}/settings")
    public String updateSettings(@AuthenticationPrincipal AlouteUserPrincipal me,
                                 @PathVariable UUID id,
                                 @RequestParam(required = false) String title,
                                 @RequestParam(required = false) String description,
                                 @RequestParam(required = false) String rules,
                                 @RequestParam(defaultValue = "false") boolean approvalRequired,
                                 @RequestParam(required = false) String approvalQuestion,
                                 RedirectAttributes redirectAttributes) {
        try {
            groupService.updateSettings(me.id(), id, title, description, rules, approvalRequired, approvalQuestion);
            redirectAttributes.addFlashAttribute("notice", "Đã cập nhật cài đặt nhóm thành công!");
        } catch (ChatActionException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/groups/" + id + "?tab=manage";
    }

    @PostMapping("/{id}/members/{uid}/role")
    public String changeRole(@AuthenticationPrincipal AlouteUserPrincipal me,
                             @PathVariable UUID id,
                             @PathVariable UUID uid,
                             @RequestParam GroupRole role,
                             RedirectAttributes redirectAttributes) {
        try {
            groupService.changeMemberRole(me.id(), id, uid, role);
            redirectAttributes.addFlashAttribute("notice", "Đã cập nhật vai trò thành công!");
        } catch (ChatActionException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/groups/" + id + "?tab=members";
    }

    @PostMapping("/{id}/members/{uid}/remove")
    public String removeMember(@AuthenticationPrincipal AlouteUserPrincipal me,
                               @PathVariable UUID id,
                               @PathVariable UUID uid,
                               RedirectAttributes redirectAttributes) {
        try {
            groupService.removeMember(me.id(), id, uid);
            redirectAttributes.addFlashAttribute("notice", "Đã xóa thành viên khỏi nhóm.");
        } catch (ChatActionException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/groups/" + id + "?tab=members";
    }

    @PostMapping("/{id}/requests/{uid}/approve")
    public String approveRequest(@AuthenticationPrincipal AlouteUserPrincipal me,
                                 @PathVariable UUID id,
                                 @PathVariable UUID uid,
                                 RedirectAttributes redirectAttributes) {
        try {
            groupService.approveRequest(me.id(), id, uid);
            redirectAttributes.addFlashAttribute("notice", "Đã phê duyệt thành viên vào nhóm!");
        } catch (ChatActionException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/groups/" + id + "?tab=manage";
    }

    @PostMapping("/{id}/requests/{uid}/reject")
    public String rejectRequest(@AuthenticationPrincipal AlouteUserPrincipal me,
                                @PathVariable UUID id,
                                @PathVariable UUID uid,
                                RedirectAttributes redirectAttributes) {
        try {
            groupService.rejectRequest(me.id(), id, uid);
            redirectAttributes.addFlashAttribute("notice", "Đã từ chối yêu cầu gia nhập.");
        } catch (ChatActionException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/groups/" + id + "?tab=manage";
    }

    @PostMapping("/{id}/posts/{postId}/pin")
    public String pinPost(@AuthenticationPrincipal AlouteUserPrincipal me,
                          @PathVariable UUID id,
                          @PathVariable UUID postId,
                          @RequestParam(required = false) String next,
                          RedirectAttributes redirectAttributes) {
        try {
            groupService.pinPost(me.id(), id, postId);
            redirectAttributes.addFlashAttribute("notice", "Đã ghim bài viết lên đầu nhóm!");
        } catch (ChatActionException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + (next != null && !next.isBlank() ? next : "/groups/" + id);
    }

    @PostMapping("/{id}/posts/{postId}/unpin")
    public String unpinPost(@AuthenticationPrincipal AlouteUserPrincipal me,
                            @PathVariable UUID id,
                            @PathVariable UUID postId,
                            @RequestParam(required = false) String next,
                            RedirectAttributes redirectAttributes) {
        try {
            groupService.unpinPost(me.id(), id, postId);
            redirectAttributes.addFlashAttribute("notice", "Đã bỏ ghim bài viết.");
        } catch (ChatActionException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + (next != null && !next.isBlank() ? next : "/groups/" + id);
    }

    @PostMapping("/{id}/posts/{postId}/report")
    public String reportPost(@AuthenticationPrincipal AlouteUserPrincipal me,
                             @PathVariable UUID id,
                             @PathVariable UUID postId,
                             @RequestParam String reason,
                             @RequestParam(required = false) String next,
                             RedirectAttributes redirectAttributes) {
        try {
            groupService.reportPost(me.id(), id, postId, reason);
            redirectAttributes.addFlashAttribute("notice", "Đã gửi báo cáo cho ban quản trị nhóm xem xét.");
        } catch (ChatActionException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + (next != null && !next.isBlank() ? next : "/groups/" + id);
    }

    @PostMapping("/{id}/reports/{reportId}/dismiss")
    public String dismissReport(@AuthenticationPrincipal AlouteUserPrincipal me,
                                @PathVariable UUID id,
                                @PathVariable UUID reportId,
                                RedirectAttributes redirectAttributes) {
        try {
            groupService.dismissReport(me.id(), id, reportId);
            redirectAttributes.addFlashAttribute("notice", "Đã bỏ qua báo cáo.");
        } catch (ChatActionException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/groups/" + id + "?tab=manage";
    }

    @PostMapping("/{id}/reports/{reportId}/delete-post")
    public String deleteReportedPost(@AuthenticationPrincipal AlouteUserPrincipal me,
                                     @PathVariable UUID id,
                                     @PathVariable UUID reportId,
                                     RedirectAttributes redirectAttributes) {
        try {
            groupService.deleteReportedPost(me.id(), id, reportId);
            redirectAttributes.addFlashAttribute("notice", "Đã xóa bài viết vi phạm khỏi nhóm!");
        } catch (ChatActionException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/groups/" + id + "?tab=manage";
    }
}
