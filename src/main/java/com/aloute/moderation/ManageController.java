package com.aloute.moderation;

import com.aloute.security.AlouteUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/** Khu Kiểm duyệt của Manager. Quyền vào {@code /manage/**} đã được chặn ở SecurityConfig. */
@Controller
public class ManageController {

    private final ModerationService moderation;

    public ManageController(ModerationService moderation) {
        this.moderation = moderation;
    }

    @GetMapping("/manage")
    public String queue(Model model) {
        model.addAttribute("active", "manage");
        model.addAttribute("reports", moderation.openReports());
        model.addAttribute("actions", ReportAction.values());
        return "manage/queue";
    }

    @PostMapping("/manage/reports/{id}")
    public String handle(@AuthenticationPrincipal AlouteUserPrincipal me, @PathVariable UUID id,
                         @RequestParam ReportAction action, @RequestParam(required = false) String note,
                         RedirectAttributes flash) {
        try {
            moderation.handle(me.id(), id, action, note);
            flash.addFlashAttribute("notice", "Đã xử lý báo cáo.");
        } catch (InvalidModerationException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/manage";
    }

    @GetMapping("/manage/suspended")
    public String suspended(Model model) {
        model.addAttribute("active", "manage");
        model.addAttribute("users", moderation.suspended());
        return "manage/suspended";
    }

    @PostMapping("/manage/users/{id}/lift")
    public String lift(@AuthenticationPrincipal AlouteUserPrincipal me, @PathVariable UUID id, RedirectAttributes flash) {
        try {
            moderation.lift(me.id(), id);
            flash.addFlashAttribute("notice", "Đã gỡ khóa tài khoản.");
        } catch (InvalidModerationException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/manage/suspended";
    }
}
