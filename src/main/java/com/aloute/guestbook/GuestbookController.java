package com.aloute.guestbook;

import com.aloute.common.SafeRedirect;
import com.aloute.security.AlouteUserPrincipal;
import com.aloute.social.SocialActionException;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
public class GuestbookController {

    private final GuestbookService guestbook;

    public GuestbookController(GuestbookService guestbook) {
        this.guestbook = guestbook;
    }

    @PostMapping("/u/{username}/guestbook")
    public String addEntry(@AuthenticationPrincipal AlouteUserPrincipal me,
                           @PathVariable String username,
                           @Valid @ModelAttribute GuestbookForm form,
                           BindingResult binding,
                           @RequestParam(required = false) String next,
                           RedirectAttributes flash) {
        String fallback = "/u/" + username + "?tab=guestbook";
        String target = SafeRedirect.sanitize(next != null && !next.isBlank() ? next : fallback);

        if (binding.hasErrors()) {
            String errorMsg = binding.getAllErrors().get(0).getDefaultMessage();
            flash.addFlashAttribute("error", errorMsg);
            flash.addFlashAttribute("draftContent", form.getContent());
            flash.addFlashAttribute("draftColor", form.getColorTheme());
            flash.addFlashAttribute("draftSticker", form.getStickerIcon());
            return "redirect:" + target;
        }

        try {
            guestbook.addEntry(me.id(), username, form);
            flash.addFlashAttribute("notice", "Đã dán mẩu lưu bút thành công!");
        } catch (SocialActionException e) {
            flash.addFlashAttribute("error", e.getMessage());
            flash.addFlashAttribute("draftContent", form.getContent());
            flash.addFlashAttribute("draftColor", form.getColorTheme());
            flash.addFlashAttribute("draftSticker", form.getStickerIcon());
        } catch (Exception e) {
            flash.addFlashAttribute("error", "Không thể dán lưu bút lúc này. Thử lại sau nhé!");
            flash.addFlashAttribute("draftContent", form.getContent());
            flash.addFlashAttribute("draftColor", form.getColorTheme());
            flash.addFlashAttribute("draftSticker", form.getStickerIcon());
        }

        return "redirect:" + target;
    }

    @PostMapping("/guestbook/{id}/pin")
    public String togglePin(@AuthenticationPrincipal AlouteUserPrincipal me,
                            @PathVariable UUID id,
                            @RequestParam(required = false) String next,
                            RedirectAttributes flash) {
        String target = SafeRedirect.sanitize(next);
        try {
            guestbook.togglePin(me.id(), id);
            flash.addFlashAttribute("notice", "Đã cập nhật ghim lưu bút.");
        } catch (SocialActionException e) {
            flash.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            flash.addFlashAttribute("error", "Không thể ghim lưu bút lúc này.");
        }
        return "redirect:" + target;
    }

    @PostMapping("/guestbook/{id}/hide")
    public String toggleHide(@AuthenticationPrincipal AlouteUserPrincipal me,
                             @PathVariable UUID id,
                             @RequestParam(required = false) String next,
                             RedirectAttributes flash) {
        String target = SafeRedirect.sanitize(next);
        try {
            guestbook.toggleHide(me.id(), id);
            flash.addFlashAttribute("notice", "Đã cập nhật trạng thái hiển thị mẩu lưu bút.");
        } catch (SocialActionException e) {
            flash.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            flash.addFlashAttribute("error", "Không thể cập nhật trạng thái lúc này.");
        }
        return "redirect:" + target;
    }

    @PostMapping("/guestbook/{id}/delete")
    public String delete(@AuthenticationPrincipal AlouteUserPrincipal me,
                         @PathVariable UUID id,
                         @RequestParam(required = false) String next,
                         RedirectAttributes flash) {
        String target = SafeRedirect.sanitize(next);
        try {
            guestbook.delete(me.id(), id);
            flash.addFlashAttribute("notice", "Đã gỡ mẩu lưu bút.");
        } catch (SocialActionException e) {
            flash.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            flash.addFlashAttribute("error", "Không thể gỡ mẩu lưu bút lúc này.");
        }
        return "redirect:" + target;
    }
}
