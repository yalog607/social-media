package com.aloute.controller.admin;

import com.aloute.dto.admin.StaffMember;
import com.aloute.exception.admin.InvalidAdminActionException;
import com.aloute.service.admin.AdminService;
import com.aloute.service.admin.SystemSettings;

import com.aloute.service.audit.AuditService;
import com.aloute.security.AlouteUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.aloute.service.moderation.ModerationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Khu Quản trị. Quyền vào {@code /admin/**} đã được chặn ở SecurityConfig (chỉ ADMIN). */
@Controller
public class AdminController {

    private final AdminService admin;
    private final AuditService audit;
    private final SystemSettings settings;
    private final ModerationService moderation;

    public AdminController(AdminService admin, AuditService audit, SystemSettings settings,
                           ModerationService moderation) {
        this.admin = admin;
        this.audit = audit;
        this.settings = settings;
        this.moderation = moderation;
    }

    @GetMapping("/admin")
    public String dashboard(@RequestParam(required = false) String period,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            Model model) {
        model.addAttribute("active", "admin");
        model.addAttribute("overviewData", admin.overview(period, from, to));
        return "admin/dashboard";
    }

    @GetMapping("/admin/staff")
    public String staff(@RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                        Model model) {
        model.addAttribute("active", "admin");
        model.addAttribute("managersPage", admin.managers(page, size));
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
    public String users(@RequestParam(required = false) String q,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                        Model model) {
        model.addAttribute("active", "admin");
        model.addAttribute("q", q);
        model.addAttribute("usersPage", admin.search(q, page, size));
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
    public String logs(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size,
                       @RequestParam(required = false) String action,
                       @RequestParam(required = false) String actor,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       Model model) {
        model.addAttribute("active", "admin");
        model.addAttribute("action", action);
        model.addAttribute("actor", actor);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("logsPage", audit.search(page, size, action, actor, from, to));
        return "admin/logs";
    }

    @GetMapping("/admin/reports")
    public String reportsHistory(@RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size,
                                 @RequestParam(required = false) String targetType,
                                 @RequestParam(required = false) String action,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                 Model model) {
        model.addAttribute("active", "admin");
        model.addAttribute("targetType", targetType);
        model.addAttribute("action", action);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("reportsPage", moderation.resolvedReports(page, size, targetType, action, from, to));
        return "admin/reports_history";
    }

    @GetMapping("/admin/reports/{id}")
    public String reportDetail(@PathVariable UUID id, Model model) {
        var report = moderation.getResolvedReport(id);
        if (report == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Báo cáo không tồn tại.");
        }
        model.addAttribute("active", "admin");
        model.addAttribute("report", report);
        model.addAttribute("coReports", moderation.getCoReports(id));
        return "admin/report_detail";
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
