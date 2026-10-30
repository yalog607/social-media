package com.aloute.controller.season;

import com.aloute.security.AlouteUserPrincipal;
import com.aloute.service.season.SeasonService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.web.multipart.MultipartFile;
import com.aloute.service.storage.StorageService;
import com.aloute.util.storage.ImageSniffer;
import java.io.IOException;
import java.time.Clock;
import java.time.ZoneId;
import java.util.UUID;

@Controller
@RequestMapping("/admin/seasons")
public class SeasonManagementController {
    private final SeasonService seasons;
    private final Clock clock;
    private final StorageService storage;
    public SeasonManagementController(SeasonService seasons, Clock clock, StorageService storage) { this.seasons = seasons; this.clock = clock; this.storage = storage; }

    @GetMapping
    public String list(Model model) {
        LocalDate start = clock.instant().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate().plusDays(1);
        model.addAttribute("defaultStart", start.atStartOfDay()); model.addAttribute("defaultEnd", start.plusDays(30).atStartOfDay());
        model.addAttribute("seasons", seasons.list()); return "season/manage";
    }

    @PostMapping
    public String create(@AuthenticationPrincipal AlouteUserPrincipal me, @RequestParam String name,
                         @RequestParam(defaultValue = "") String description, @RequestParam String hashtag,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end, RedirectAttributes flash) {
        try { return "redirect:/admin/seasons/" + seasons.create(me.id(), name, description, hashtag, start, end); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("error", e.getMessage()); return "redirect:/admin/seasons"; }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        model.addAttribute("season", seasons.get(id)); model.addAttribute("entries", seasons.entries(id, true));
        model.addAttribute("standings", seasons.standings(id)); model.addAttribute("bannedParticipants", seasons.bannedParticipants(id)); return "season/manage-detail";
    }

    @PostMapping("/{id}/image")
    public String image(@PathVariable UUID id, @RequestParam MultipartFile image, @AuthenticationPrincipal AlouteUserPrincipal me, RedirectAttributes flash) {
        try {
            seasons.get(id);
            if (image.isEmpty()) throw new IllegalArgumentException("Vui lòng chọn ảnh.");
            if (image.getSize() > 10L * 1024 * 1024) throw new IllegalArgumentException("Ảnh quá lớn, tối đa 10 MB");
            byte[] data = image.getBytes();
            var type = ImageSniffer.sniff(data, 10L * 1024 * 1024);
            seasons.setImage(id, storage.storeBytes(data, "seasons", type.extension()));
            flash.addFlashAttribute("notice", "Đã cập nhật ảnh mùa giải.");
        } catch (IllegalArgumentException | StorageService.InvalidUploadException e) { flash.addFlashAttribute("error", e.getMessage()); }
        catch (IOException e) { flash.addFlashAttribute("error", "Không đọc được ảnh, vui lòng thử lại."); }
        return "redirect:/admin/seasons/" + id;
    }

    @PostMapping("/{id}/publish")
    public String publish(@PathVariable UUID id, RedirectAttributes flash) {
        try { seasons.publish(id); flash.addFlashAttribute("notice", "Đã công bố mùa giải."); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/seasons/" + id;
    }
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, @AuthenticationPrincipal AlouteUserPrincipal me, RedirectAttributes flash) {
        try {
            seasons.delete(id, me.id());
            flash.addFlashAttribute("notice", "Đã xóa mùa giải.");
            return "redirect:/admin/seasons";
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/seasons";
        }
    }
    @PostMapping("/{id}/save")
    public String save(@PathVariable UUID id, @RequestParam String name, @RequestParam(defaultValue = "") String description,
                       @RequestParam String hashtag, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end, RedirectAttributes flash) {
        try { seasons.updateDraft(id, name, description, hashtag, start, end); flash.addFlashAttribute("notice", "Đã lưu bản nháp."); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/seasons/" + id;
    }
    @PostMapping("/{id}/active/save")
    public String saveActive(@PathVariable UUID id, @RequestParam String name, @RequestParam(defaultValue = "") String description,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                             @AuthenticationPrincipal AlouteUserPrincipal me, RedirectAttributes flash) {
        try { seasons.updateActive(id, me.id(), name, description, end); flash.addFlashAttribute("notice", "Đã cập nhật mùa giải."); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/seasons/" + id;
    }
    @PostMapping("/{id}/ban/{userId}")
    public String ban(@PathVariable UUID id, @PathVariable UUID userId, @RequestParam String reason, @AuthenticationPrincipal AlouteUserPrincipal me, RedirectAttributes flash) {
        try { seasons.banParticipant(id, userId, me.id(), reason); flash.addFlashAttribute("notice", "Đã loại người tham gia khỏi mùa giải."); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/seasons/" + id;
    }
    @PostMapping("/{id}/unban/{userId}")
    public String unban(@PathVariable UUID id, @PathVariable UUID userId, @AuthenticationPrincipal AlouteUserPrincipal me, RedirectAttributes flash) {
        try { seasons.unbanParticipant(id, userId, me.id()); flash.addFlashAttribute("notice", "Đã khôi phục người tham gia."); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/seasons/" + id;
    }
    @PostMapping("/{id}/exclude/{postId}")
    public String exclude(@PathVariable UUID id, @PathVariable UUID postId, @RequestParam String reason,
                          @AuthenticationPrincipal AlouteUserPrincipal me, RedirectAttributes flash) {
        try { seasons.exclude(id, postId, me.id(), reason); flash.addFlashAttribute("notice", "Đã loại bài dự thi."); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/seasons/" + id;
    }
    @PostMapping("/{id}/settle")
    public String settle(@PathVariable UUID id, @AuthenticationPrincipal AlouteUserPrincipal me, RedirectAttributes flash) {
        try { seasons.settle(id, me.id()); flash.addFlashAttribute("notice", "Đã công bố kết quả mùa giải."); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/seasons/" + id;
    }
}
