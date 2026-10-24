package com.aloute.controller.mood;

import com.aloute.dto.mood.MoodForm;
import com.aloute.security.AlouteUserPrincipal;
import com.aloute.service.mood.MoodService;
import com.aloute.util.common.SafeRedirect;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class MoodController {

    private final MoodService moodService;

    public MoodController(MoodService moodService) {
        this.moodService = moodService;
    }

    @PostMapping("/mood")
    public String setMood(@AuthenticationPrincipal AlouteUserPrincipal me,
                          @Valid @ModelAttribute MoodForm form,
                          BindingResult binding,
                          @RequestParam(required = false) String next,
                          RedirectAttributes flash) {
        if (me == null) {
            return "redirect:/login";
        }
        if (binding.hasErrors()) {
            String error = binding.getAllErrors().getFirst().getDefaultMessage();
            flash.addFlashAttribute("error", error);
            return "redirect:" + SafeRedirect.sanitize(next);
        }
        try {
            moodService.setMood(me.id(), form);
            flash.addFlashAttribute("notice", "Đã cập nhật tâm trạng hôm nay: " + form.getEmoji() + " " + form.getMoodText());
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + SafeRedirect.sanitize(next);
    }

    @PostMapping("/mood/delete")
    public String deleteMood(@AuthenticationPrincipal AlouteUserPrincipal me,
                             @RequestParam(required = false) String next,
                             RedirectAttributes flash) {
        if (me == null) {
            return "redirect:/login";
        }
        moodService.clearMood(me.id());
        flash.addFlashAttribute("notice", "Đã gỡ tâm trạng hôm nay.");
        return "redirect:" + SafeRedirect.sanitize(next);
    }
}
