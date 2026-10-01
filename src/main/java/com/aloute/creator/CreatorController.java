package com.aloute.creator;

import com.aloute.security.AlouteUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Khu Creator Studio. Quyền vào {@code /creator/**} đã được chặn ở SecurityConfig. */
@Controller
public class CreatorController {

    private final InsightsService insights;

    public CreatorController(InsightsService insights) {
        this.insights = insights;
    }

    @GetMapping("/creator")
    public String dashboard(@AuthenticationPrincipal AlouteUserPrincipal me,
                            @RequestParam(defaultValue = "7") int days, Model model) {
        model.addAttribute("active", "creator");
        model.addAttribute("insights", insights.insights(me.id(), days == 30 ? 30 : 7));
        return "creator/dashboard";
    }
}
