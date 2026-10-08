package com.aloute.notification;

import com.aloute.post.PostRepository;
import com.aloute.post.PostService;
import com.aloute.report.ReportRepository;
import com.aloute.security.AlouteUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Trang thông báo: mở trang là các thông báo hiện có được đánh dấu đã đọc ngay (giống Facebook), ngoại trừ SUPPORT và REPORT.
 */
@Controller
public class NotificationController {

    private final NotificationService notifications;
    private final PostRepository posts;
    private final PostService postService;
    private final ReportRepository reports;

    public NotificationController(NotificationService notifications,
                                  PostRepository posts,
                                  PostService postService,
                                  ReportRepository reports) {
        this.notifications = notifications;
        this.posts = posts;
        this.postService = postService;
        this.reports = reports;
    }

    @GetMapping("/notifications")
    public String list(@AuthenticationPrincipal AlouteUserPrincipal me, Model model) {
        model.addAttribute("items", notifications.listRecent(me.id()));
        notifications.markAllRead(me.id());
        return "notification/list";
    }

    @GetMapping("/notifications/{id}/read")
    public String readAndRedirect(@AuthenticationPrincipal AlouteUserPrincipal me,
                                  @PathVariable UUID id,
                                  @RequestParam(required = false) String type,
                                  @RequestParam(required = false) UUID ref,
                                  @RequestParam(required = false) UUID postId,
                                  RedirectAttributes flash) {
        notifications.markRead(id, me.id());
        if ("SUPPORT".equalsIgnoreCase(type)) {
            return "redirect:/support" + (ref != null ? "#ticket-" + ref : "");
        } else if ("REPORT".equalsIgnoreCase(type)) {
            if (ref != null && reports.findByIdAndReporterId(ref, me.id()).isPresent()) {
                return "redirect:/reports/" + ref;
            }
            if (postId != null && isLiveAndVisible(postId, me.id())) {
                return "redirect:/posts/" + postId;
            }
            flash.addFlashAttribute("notice", "Thông báo đã được đánh dấu là đã đọc.");
            return "redirect:/notifications";
        } else if (postId != null) {
            if (isLiveAndVisible(postId, me.id())) {
                return "redirect:/posts/" + postId;
            }
            flash.addFlashAttribute("error", "Bài viết này không còn tồn tại hoặc đã bị gỡ bỏ.");
            return "redirect:/notifications";
        }
        return "redirect:/notifications";
    }

    private boolean isLiveAndVisible(UUID postId, UUID viewerId) {
        if (postId == null) {
            return false;
        }
        return posts.findLive(postId).map(p -> postService.canView(p, viewerId)).orElse(false);
    }
}
