package com.aloute.controller.moderation;

import com.aloute.exception.moderation.InvalidModerationException;
import com.aloute.service.moderation.SupportService;

import com.aloute.exception.common.RateLimitExceededException;
import com.aloute.security.AlouteUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Trang Hỗ trợ của người dùng: gửi phiếu và xem phản hồi của Manager. */
@Controller
public class SupportController {

    private final SupportService support;

    public SupportController(SupportService support) {
        this.support = support;
    }

    @GetMapping("/support")
    public String page(@AuthenticationPrincipal AlouteUserPrincipal me, Model model) {
        model.addAttribute("tickets", support.mine(me.id()));
        model.addAttribute("maxSubject", SupportService.MAX_SUBJECT);
        model.addAttribute("maxBody", SupportService.MAX_BODY);
        return "support/index";
    }

    @PostMapping("/support")
    public String submit(@AuthenticationPrincipal AlouteUserPrincipal me, @RequestParam String subject,
                         @RequestParam String body, RedirectAttributes flash) {
        try {
            support.submit(me.id(), subject, body);
            flash.addFlashAttribute("notice", "Đã gửi yêu cầu, chúng tôi sẽ phản hồi sớm.");
        } catch (InvalidModerationException | RateLimitExceededException e) {
            flash.addFlashAttribute("error", e.getMessage());
            flash.addFlashAttribute("draftSubject", subject);
            flash.addFlashAttribute("draftBody", body);
        }
        return "redirect:/support";
    }
}
