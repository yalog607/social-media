package com.aloute.controller.moderation;

import com.aloute.exception.moderation.InvalidModerationException;
import com.aloute.model.moderation.ReportAction;
import com.aloute.service.moderation.BannedHashtags;
import com.aloute.service.moderation.ManageStats;
import com.aloute.service.moderation.ModerationService;
import com.aloute.service.moderation.SupportService;

import com.aloute.security.AlouteUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.UUID;
import com.aloute.dto.moderation.ManagerPage;

/** Khu Kiểm duyệt của Manager. Quyền vào {@code /manage/**} đã được chặn ở SecurityConfig. */
@Controller
public class ManageController {

    private final ModerationService moderation;
    private final SupportService support;
    private final BannedHashtags hashtags;
    private final ManageStats stats;

    public ManageController(ModerationService moderation, SupportService support, BannedHashtags hashtags,
                            ManageStats stats) {
        this.moderation = moderation;
        this.support = support;
        this.hashtags = hashtags;
        this.stats = stats;
    }

    @GetMapping("/manage")
    public String queue(@RequestParam(required = false) String page, @RequestParam(required = false) String size, Model model) {
        model.addAttribute("active", "manage");
        var pagination = ManagerPage.of(page, size, moderation.countOpenReports());
        model.addAttribute("pagination", pagination);
        model.addAttribute("pagePath", "/manage");
        model.addAttribute("reports", moderation.openReports(pagination.size(), pagination.offset()));
        model.addAttribute("actions", ReportAction.values());
        return "manage/queue";
    }

    @GetMapping("/manage/reports/history")
    public String reportsHistory(@RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size,
                                 @RequestParam(required = false) String targetType,
                                 @RequestParam(required = false) String action,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                 Model model) {
        model.addAttribute("active", "manage");
        model.addAttribute("targetType", targetType);
        model.addAttribute("action", action);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("reportsPage", moderation.resolvedReports(page, size, targetType, action, from, to));
        return "manage/reports_history";
    }

    @GetMapping("/manage/reports/history/{id}")
    public String reportDetail(@PathVariable UUID id, Model model) {
        var report = moderation.getResolvedReport(id);
        if (report == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Báo cáo không tồn tại.");
        }
        model.addAttribute("active", "manage");
        model.addAttribute("report", report);
        model.addAttribute("coReports", moderation.getCoReports(id));
        return "manage/report_detail";
    }

    @PostMapping("/manage/reports/{id}")
    public String handle(@AuthenticationPrincipal AlouteUserPrincipal me, @PathVariable UUID id,
                         @RequestParam ReportAction action, @RequestParam(required = false) String note,
                         @RequestParam(required = false) String page, @RequestParam(required = false) String size, RedirectAttributes flash) {
        try {
            moderation.handle(me.id(), id, action, note);
            flash.addFlashAttribute("notice", "Đã xử lý báo cáo.");
        } catch (InvalidModerationException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return ManagerPage.of(page, size, moderation.countOpenReports()).redirect("/manage");
    }

    @GetMapping("/manage/suspended")
    public String suspended(@RequestParam(required = false) String page, @RequestParam(required = false) String size, Model model) {
        model.addAttribute("active", "manage");
        var pagination = ManagerPage.of(page, size, moderation.countSuspended());
        model.addAttribute("pagination", pagination);
        model.addAttribute("pagePath", "/manage/suspended");
        model.addAttribute("users", moderation.suspended(pagination.size(), pagination.offset()));
        return "manage/suspended";
    }

    @PostMapping("/manage/users/{id}/lift")
    public String lift(@AuthenticationPrincipal AlouteUserPrincipal me, @PathVariable UUID id, @RequestParam(required = false) String page, @RequestParam(required = false) String size, RedirectAttributes flash) {
        try {
            moderation.lift(me.id(), id);
            flash.addFlashAttribute("notice", "Đã gỡ khóa tài khoản.");
        } catch (InvalidModerationException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return ManagerPage.of(page, size, moderation.countSuspended()).redirect("/manage/suspended");
    }

    @GetMapping("/manage/support")
    public String supportQueue(@RequestParam(required = false) String page, @RequestParam(required = false) String size, Model model) {
        model.addAttribute("active", "manage");
        var pagination = ManagerPage.of(page, size, support.countOpenTickets());
        model.addAttribute("pagination", pagination);
        model.addAttribute("pagePath", "/manage/support");
        model.addAttribute("tickets", support.open(pagination.size(), pagination.offset()));
        return "manage/support";
    }

    @PostMapping("/manage/support/{id}")
    public String reply(@AuthenticationPrincipal AlouteUserPrincipal me, @PathVariable UUID id,
                        @RequestParam String reply, @RequestParam(required = false) String page, @RequestParam(required = false) String size, RedirectAttributes flash) {
        try {
            support.reply(me.id(), id, reply);
            flash.addFlashAttribute("notice", "Đã phản hồi và đóng phiếu.");
        } catch (InvalidModerationException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return ManagerPage.of(page, size, support.countOpenTickets()).redirect("/manage/support");
    }

    @GetMapping("/manage/hashtags")
    public String bannedHashtags(@RequestParam(required = false) String page, @RequestParam(required = false) String size, Model model) {
        model.addAttribute("active", "manage");
        var pagination = ManagerPage.of(page, size, hashtags.count());
        model.addAttribute("pagination", pagination);
        model.addAttribute("pagePath", "/manage/hashtags");
        model.addAttribute("tags", hashtags.list(pagination.size(), pagination.offset()));
        return "manage/hashtags";
    }

    @PostMapping("/manage/hashtags")
    public String ban(@AuthenticationPrincipal AlouteUserPrincipal me, @RequestParam String tag, @RequestParam(required = false) String page, @RequestParam(required = false) String size, RedirectAttributes flash) {
        try {
            hashtags.ban(me.id(), tag);
            flash.addFlashAttribute("notice", "Đã cấm hashtag.");
        } catch (InvalidModerationException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return ManagerPage.of(page, size, hashtags.count()).redirect("/manage/hashtags");
    }

    @PostMapping("/manage/hashtags/remove")
    public String unban(@AuthenticationPrincipal AlouteUserPrincipal me, @RequestParam String tag, @RequestParam(required = false) String page, @RequestParam(required = false) String size, RedirectAttributes flash) {
        try {
            hashtags.unban(me.id(), tag);
            flash.addFlashAttribute("notice", "Đã gỡ cấm hashtag.");
        } catch (InvalidModerationException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return ManagerPage.of(page, size, hashtags.count()).redirect("/manage/hashtags");
    }

    @GetMapping("/manage/stats")
    public String stats(@RequestParam(required = false) String page, @RequestParam(required = false) String size, Model model) {
        model.addAttribute("active", "manage");
        model.addAttribute("overview", stats.overview());
        var pagination = ManagerPage.of(page, size, stats.countReasons());
        model.addAttribute("pagination", pagination);
        model.addAttribute("pagePath", "/manage/stats");
        model.addAttribute("reasons", stats.reasons(pagination.size(), pagination.offset()));
        return "manage/stats";
    }
}
