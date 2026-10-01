package com.aloute.admin;

import com.aloute.audit.AuditService;
import com.aloute.security.AlouteUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.UUID;

/** Khu Quản trị. Quyền vào {@code /admin/**} đã được chặn ở SecurityConfig (chỉ ADMIN). */
@Controller
public class AdminController {

    private final AdminService admin;
    private final AuditService audit;
    private final SystemSettings settings;

    public AdminController(AdminService admin, AuditService audit, SystemSettings settings) {
        this.admin = admin;
        this.audit = audit;
        this.settings = settings;
    }

    @GetMapping("/admin")
    public String dashboard(Model model) {
        model.addAttribute("active", "admin");
        model.addAttribute("overview", admin.overview());
        return "admin/dashboard";
    }

    @GetMapping("/admin/staff")
    public String staff(Model model) {
        model.addAttribute("active", "admin");
        model.addAttribute("managers", admin.managers());
        return "admin/staff";
    }

    @PostMapping("/admin/staff/grant")
    public String grant(@AuthenticationPrincipal AlouteUserPrincipal me, @RequestParam String username, RedirectAttributes flash) {
        return run(flash, "Đã cấp quyền Manager.", "/admin/staff", () -> admin.grantManager(me.id(), username));
    }

    @PostMapping("/admin/staff/{id}/revoke")
    public String revoke(@AuthenticationPrincipal AlouteUserPrincipal me, @PathVariable UUID id, RedirectAttributes flash) {
        return run(flash, "Đã thu hồi quyền Manager.", "/admin/staff", () -> admin.revokeManager(me.id(), id));
    }

    @GetMapping("/admin/users")
    public String users(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("active", "admin");
        model.addAttribute("q", q);
        List<StaffMember> found = admin.search(q);
        model.addAttribute("users", found);
        return "admin/users";
    }

    @PostMapping("/admin/users/{id}/suspend")
    public String suspend(@AuthenticationPrincipal AlouteUserPrincipal me, @PathVariable UUID id,
                          @RequestParam(required = false) String reason, RedirectAttributes flash) {
        return run(flash, "Đã khóa tài khoản.", "/admin/users", () -> admin.suspend(me.id(), id, reason));
    }

    @PostMapping("/admin/users/{id}/unsuspend")
    public String unsuspend(@AuthenticationPrincipal AlouteUserPrincipal me, @PathVariable UUID id, RedirectAttributes flash) {
        return run(flash, "Đã mở khóa tài khoản.", "/admin/users", () -> admin.unsuspend(me.id(), id));
    }

    @PostMapping("/admin/users/{id}/delete")
    public String delete(@AuthenticationPrincipal AlouteUserPrincipal me, @PathVariable UUID id,
                         @RequestParam(required = false) String reason, RedirectAttributes flash) {
        return run(flash, "Đã xóa vĩnh viễn tài khoản và ẩn nội dung của họ.", "/admin/users",
                () -> admin.deletePermanently(me.id(), id, reason));
    }

    @GetMapping("/admin/logs")
    public String logs(@RequestParam(defaultValue = "0") int page, @RequestParam(required = false) String action, Model model) {
        int safePage = Math.max(0, page);
        var entries = audit.page(safePage, action);
        boolean more = entries.size() > AuditService.PAGE_SIZE;
        model.addAttribute("active", "admin");
        model.addAttribute("entries", more ? entries.subList(0, AuditService.PAGE_SIZE) : entries);
        model.addAttribute("page", safePage);
        model.addAttribute("hasMore", more);
        model.addAttribute("action", action);
        return "admin/logs";
    }

    @GetMapping("/admin/settings")
    public String settings(Model model) {
        model.addAttribute("active", "admin");
        model.addAttribute("registrationOpen", settings.registrationOpen());
        return "admin/settings";
    }

    @PostMapping("/admin/settings")
    public String saveSettings(@AuthenticationPrincipal AlouteUserPrincipal me,
                               @RequestParam(defaultValue = "false") boolean registrationOpen, RedirectAttributes flash) {
        settings.setRegistrationOpen(me.id(), registrationOpen);
        flash.addFlashAttribute("notice", "Đã lưu cấu hình.");
        return "redirect:/admin/settings";
    }

    private static String run(RedirectAttributes flash, String success, String back, Runnable action) {
        try {
            action.run();
            flash.addFlashAttribute("notice", success);
        } catch (InvalidAdminActionException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + back;
    }
}
