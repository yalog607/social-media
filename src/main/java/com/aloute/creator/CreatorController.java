package com.aloute.creator;

import com.aloute.post.InvalidPostException;
import com.aloute.post.Post;
import com.aloute.post.PostService;
import com.aloute.post.PostView;
import com.aloute.post.PostViewAssembler;
import com.aloute.post.ScheduleTime;
import com.aloute.security.AlouteUserPrincipal;
import com.aloute.wallet.FanBadge;
import com.aloute.wallet.FanService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Khu Creator Studio. Quyền vào {@code /creator/**} đã được chặn ở SecurityConfig. */
@Controller
public class CreatorController {

    private final InsightsService insights;
    private final PostService posts;
    private final PostViewAssembler assembler;
    private final FanService fans;

    public CreatorController(InsightsService insights, PostService posts, PostViewAssembler assembler,
                             FanService fans) {
        this.fans = fans;
        this.insights = insights;
        this.posts = posts;
        this.assembler = assembler;
    }

    @GetMapping("/creator")
    public String dashboard(@AuthenticationPrincipal AlouteUserPrincipal me,
                            @RequestParam(defaultValue = "7") int days, Model model) {
        model.addAttribute("active", "creator");
        model.addAttribute("insights", insights.insights(me.id(), days == 30 ? 30 : 7));
        return "creator/dashboard";
    }

    @GetMapping("/creator/fans")
    public String fans(@AuthenticationPrincipal AlouteUserPrincipal me, Model model) {
        model.addAttribute("active", "creator");
        model.addAttribute("fans", fans.topFans(me.id()));
        model.addAttribute("badges", FanBadge.values());
        return "creator/fans";
    }

    @GetMapping("/creator/scheduled")
    public String scheduled(@AuthenticationPrincipal AlouteUserPrincipal me, Model model) {
        List<Post> due = posts.scheduledOf(me.id());
        List<PostView> views = assembler.assemble(due, me.id());
        List<ScheduledPostView> items = new ArrayList<>(views.size());
        for (int i = 0; i < views.size(); i++) {
            items.add(new ScheduledPostView(views.get(i), due.get(i).getScheduledAt()));
        }
        model.addAttribute("active", "creator");
        model.addAttribute("items", items);
        return "creator/scheduled";
    }

    @PostMapping("/creator/scheduled/{id}/reschedule")
    public String reschedule(@AuthenticationPrincipal AlouteUserPrincipal me, @PathVariable UUID id,
                             @RequestParam String scheduledAt, @RequestParam(required = false) Integer tzOffset,
                             RedirectAttributes flash) {
        try {
            posts.reschedule(me.id(), id, ScheduleTime.parse(scheduledAt, tzOffset));
            flash.addFlashAttribute("notice", "Đã đổi giờ hẹn.");
        } catch (InvalidPostException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/creator/scheduled";
    }

    @PostMapping("/creator/scheduled/{id}/cancel")
    public String cancel(@AuthenticationPrincipal AlouteUserPrincipal me, @PathVariable UUID id, RedirectAttributes flash) {
        posts.delete(me.id(), id);
        flash.addFlashAttribute("notice", "Đã hủy bài hẹn giờ.");
        return "redirect:/creator/scheduled";
    }
}
