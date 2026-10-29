package com.aloute.controller.mood;

import com.aloute.dto.mood.MoodForm;
import com.aloute.model.user.Visibility;
import com.aloute.security.AlouteUserPrincipal;
import com.aloute.service.mood.MoodService;
import com.aloute.service.story.StoryService;
import com.aloute.util.common.SafeRedirect;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class MoodController {

    private final MoodService moodService;
    private final StoryService storyService;

    public MoodController(MoodService moodService, StoryService storyService) {
        this.moodService = moodService;
        this.storyService = storyService;
    }

    @GetMapping("/mood")
    public String getMood() {
        return "redirect:/";
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
            // Khi đăng Vibe, cũng tự động hiện lên Khoảnh khắc (Story Vibe) cho mọi người thấy
            try {
                storyService.createStory(
                        me.id(),
                        null,
                        form.getMoodText(),
                        form.getColorTheme(),
                        Visibility.FRIENDS,
                        "VIBE",
                        form.getEmoji()
                );
            } catch (Exception ignored) {
                // Nếu gặp giới hạn rate limit hoặc lỗi tạo story, mood vẫn được lưu thành công
            }
            flash.addFlashAttribute("notice", "Đã cập nhật Vibe và chia sẻ lên Khoảnh khắc: " + form.getEmoji() + " " + form.getMoodText());
        } catch (Exception e) {
            flash.addFlashAttribute("error", e.getMessage() != null ? e.getMessage() : "Có lỗi xảy ra khi cập nhật Vibe.");
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
        try {
            storyService.deleteActiveVibeStory(me.id());
        } catch (Exception ignored) {}
        flash.addFlashAttribute("notice", "Đã gỡ tâm trạng hôm nay.");
        return "redirect:" + SafeRedirect.sanitize(next);
    }
}
