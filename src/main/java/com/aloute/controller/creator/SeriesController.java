package com.aloute.controller.creator;

import com.aloute.dto.post.PostView;
import com.aloute.exception.creator.InvalidSeriesException;
import com.aloute.security.AlouteUserPrincipal;
import com.aloute.service.creator.SeriesService;
import com.aloute.service.post.PostViewAssembler;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.UUID;

/**
 * Series của Creator. Quản lý nằm dưới {@code /creator/series} (chỉ Creator, chặn ở SecurityConfig); trang xem
 * {@code /series/{id}} dành cho mọi người dùng đã đăng nhập, quyền xem từng bài theo {@code PostService#canView}.
 */
@Controller
public class SeriesController {

    private final SeriesService series;
    private final PostViewAssembler assembler;

    public SeriesController(SeriesService series, PostViewAssembler assembler) {
        this.series = series;
        this.assembler = assembler;
    }

    @GetMapping("/creator/series")
    public String list(@AuthenticationPrincipal AlouteUserPrincipal me, Model model) {
        model.addAttribute("active", "creator");
        model.addAttribute("items", series.mine(me.id()));
        model.addAttribute("maxTitle", SeriesService.MAX_TITLE);
        model.addAttribute("maxDescription", SeriesService.MAX_DESCRIPTION);
        return "creator/series";
    }

    @PostMapping("/creator/series")
    public String create(@AuthenticationPrincipal AlouteUserPrincipal me, @RequestParam String title,
                         @RequestParam(defaultValue = "") String description, RedirectAttributes flash) {
        try {
            UUID id = series.create(me.id(), title, description);
            flash.addFlashAttribute("notice", "Đã tạo series.");
            return "redirect:/creator/series/" + id;
        } catch (InvalidSeriesException e) {
            flash.addFlashAttribute("error", e.getMessage());
            flash.addFlashAttribute("draftTitle", title);
            flash.addFlashAttribute("draftDescription", description);
            return "redirect:/creator/series";
        }
    }

    @GetMapping("/creator/series/{id}")
    public String manage(@AuthenticationPrincipal AlouteUserPrincipal me, @PathVariable UUID id, Model model) {
        model.addAttribute("active", "creator");
        model.addAttribute("series", series.owned(me.id(), id));
        model.addAttribute("members", series.members(me.id(), id));
        model.addAttribute("candidates", series.candidates(me.id(), id));
        model.addAttribute("maxTitle", SeriesService.MAX_TITLE);
        model.addAttribute("maxDescription", SeriesService.MAX_DESCRIPTION);
        return "creator/series-manage";
    }

    @PostMapping("/creator/series/{id}")
    public String update(@AuthenticationPrincipal AlouteUserPrincipal me, @PathVariable UUID id,
                         @RequestParam String title, @RequestParam(defaultValue = "") String description,
                         RedirectAttributes flash) {
        run(flash, "Đã lưu series.", () -> series.update(me.id(), id, title, description));
        return "redirect:/creator/series/" + id;
    }

    @PostMapping("/creator/series/{id}/delete")
    public String delete(@AuthenticationPrincipal AlouteUserPrincipal me, @PathVariable UUID id, RedirectAttributes flash) {
        series.delete(me.id(), id);
        flash.addFlashAttribute("notice", "Đã xóa series (các bài vẫn còn).");
        return "redirect:/creator/series";
    }

    @PostMapping("/creator/series/{id}/posts")
    public String addPost(@AuthenticationPrincipal AlouteUserPrincipal me, @PathVariable UUID id,
                          @RequestParam UUID postId, RedirectAttributes flash) {
        run(flash, "Đã thêm bài vào series.", () -> series.addPost(me.id(), id, postId));
        return "redirect:/creator/series/" + id;
    }

    @PostMapping("/creator/series/{id}/posts/{postId}/remove")
    public String removePost(@AuthenticationPrincipal AlouteUserPrincipal me, @PathVariable UUID id,
                             @PathVariable UUID postId, RedirectAttributes flash) {
        series.removePost(me.id(), id, postId);
        flash.addFlashAttribute("notice", "Đã bỏ bài khỏi series.");
        return "redirect:/creator/series/" + id;
    }

    @GetMapping("/series/{id}")
    public String view(@AuthenticationPrincipal AlouteUserPrincipal viewer, @PathVariable UUID id, Model model) {
        UUID viewerId = viewer == null ? null : viewer.id();
        SeriesService.Page page = series.page(id, viewerId);
        List<PostView> views = assembler.assemble(page.posts(), viewerId);
        model.addAttribute("series", page.series());
        model.addAttribute("posts", views);
        return "series/view";
    }

    private static void run(RedirectAttributes flash, String success, Runnable action) {
        try {
            action.run();
            flash.addFlashAttribute("notice", success);
        } catch (InvalidSeriesException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
    }
}
