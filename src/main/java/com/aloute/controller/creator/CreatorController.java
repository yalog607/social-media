package com.aloute.controller.creator;

import com.aloute.dto.creator.ScheduledPostView;
import com.aloute.exception.creator.InvalidBroadcastException;
import com.aloute.model.creator.BroadcastAudience;
import com.aloute.service.creator.BroadcastService;
import com.aloute.service.creator.InsightsService;
import com.aloute.service.creator.PollService;

import com.aloute.exception.post.InvalidPostException;
import com.aloute.model.post.Post;
import com.aloute.service.post.PostService;
import com.aloute.dto.post.PostView;
import com.aloute.service.post.PostViewAssembler;
import com.aloute.util.post.ScheduleTime;
import com.aloute.security.AlouteUserPrincipal;
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
    private final BroadcastService broadcasts;
    private final PollService polls;

    public CreatorController(InsightsService insights, PostService posts, PostViewAssembler assembler,
                             BroadcastService broadcasts, PollService polls) {
        this.polls = polls;
        this.broadcasts = broadcasts;
        this.insights = insights;
        this.posts = posts;
        this.assembler = assembler;
    }

    @GetMapping("/creator")
    public String dashboard(@AuthenticationPrincipal AlouteUserPrincipal me,
                            @RequestParam(defaultValue = "7") int days, Model model) {
        model.addAttribute("active", "creator");
        model.addAttribute("insights", insights.insights(me.id(), days == 30 ? 30 : 7));
        model.addAttribute("followHistorySinceUtc", insights.followHistorySinceUtc());
        return "creator/dashboard";
    }

    @GetMapping("/creator/broadcasts")
    public String broadcasts(@AuthenticationPrincipal AlouteUserPrincipal me, Model model) {
        model.addAttribute("active", "creator");
        model.addAttribute("history", broadcasts.history(me.id()));
        model.addAttribute("audiences", BroadcastAudience.values());
        model.addAttribute("maxTitle", BroadcastService.MAX_TITLE);
        model.addAttribute("maxBody", BroadcastService.MAX_BODY);
        return "creator/broadcasts";
    }

    @PostMapping("/creator/broadcasts")
    public String sendBroadcast(@AuthenticationPrincipal AlouteUserPrincipal me,
                                @RequestParam BroadcastAudience audience, @RequestParam String title,
                                @RequestParam String body, RedirectAttributes flash) {
        try {
            int count = broadcasts.send(me.id(), audience, title, body);
            flash.addFlashAttribute("notice", "Đã gửi tới " + count + " người.");
        } catch (InvalidBroadcastException e) {
            flash.addFlashAttribute("error", e.getMessage());
            flash.addFlashAttribute("draftTitle", title);
            flash.addFlashAttribute("draftBody", body);
        }
        return "redirect:/creator/broadcasts";
    }

    @GetMapping("/creator/polls")
    public String newPoll(Model model) {
        model.addAttribute("active", "creator");
        model.addAttribute("maxOptions", PollService.MAX_OPTIONS);
        model.addAttribute("maxLabel", PollService.MAX_LABEL);
        model.addAttribute("maxDays", PollService.MAX_DAYS_OPEN);
        return "creator/poll-new";
    }

    @PostMapping("/creator/polls")
    public String createPoll(@AuthenticationPrincipal AlouteUserPrincipal me, @RequestParam String question,
                             @RequestParam(required = false) List<String> options,
                             @RequestParam(required = false) String closesAt,
                             @RequestParam(required = false) Integer tzOffset, RedirectAttributes flash) {
        try {
            Post poll = polls.create(me.id(), question, options, ScheduleTime.parse(closesAt, tzOffset));
            flash.addFlashAttribute("notice", "Đã đăng bình chọn.");
            return "redirect:/posts/" + poll.getId();
        } catch (InvalidPostException e) {
            flash.addFlashAttribute("error", e.getMessage());
            flash.addFlashAttribute("draftQuestion", question);
            flash.addFlashAttribute("draftOptions", options);
            return "redirect:/creator/polls";
        }
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
