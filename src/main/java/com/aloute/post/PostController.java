package com.aloute.post;

import com.aloute.common.RateLimitExceededException;
import com.aloute.common.SafeRedirect;
import com.aloute.media.InvalidMediaException;
import com.aloute.security.AlouteUserPrincipal;
import com.aloute.user.Visibility;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.UUID;

/**
 * Đăng, xem, sửa, xóa bài. Đăng bài dùng form thường (multipart POST rồi chuyển hướng) nên vẫn hoạt động khi tắt JS;
 * lỗi được đưa lại ô đăng bài qua flash attribute, giữ nguyên chữ người dùng đã gõ.
 */
@Controller
public class PostController {

    private final PostService posts;
    private final PostViewAssembler assembler;

    public PostController(PostService posts, PostViewAssembler assembler) {
        this.posts = posts;
        this.assembler = assembler;
    }

    @PostMapping("/posts")
    public String create(@AuthenticationPrincipal AlouteUserPrincipal me,
                         @RequestParam(required = false) String content,
                         @RequestParam(required = false) Visibility visibility,
                         @RequestParam(required = false) List<MultipartFile> images,
                         @RequestParam(required = false) MultipartFile video,
                         @RequestParam(required = false) List<UUID> taggedUserIds,
                         @RequestParam(required = false) Integer unlockPrice,
                         RedirectAttributes flash) {
        try {
            posts.create(me.id(), content, visibility, images, video, taggedUserIds, unlockPrice);
            flash.addFlashAttribute("notice", "Đã đăng bài!");
        } catch (InvalidPostException | InvalidMediaException | RateLimitExceededException e) {
            flash.addFlashAttribute("composerError", e.getMessage());
            flash.addFlashAttribute("composerContent", content);
            flash.addFlashAttribute("composerVisibility", visibility == null ? null : visibility.name());
            flash.addFlashAttribute("composerUnlockPrice", unlockPrice);
            flash.addFlashAttribute("composerTagged", taggedUserIds == null ? List.of() : taggedUserIds);
        }
        return "redirect:/";
    }

    /** Trang riêng của một bài (địa chỉ để chia sẻ). Khách xem được nếu bài công khai; còn lại 404. */
    @GetMapping("/posts/{id}")
    public String detail(@PathVariable UUID id, @AuthenticationPrincipal AlouteUserPrincipal viewer, Model model) {
        UUID viewerId = viewer == null ? null : viewer.id();
        Post post = posts.getVisible(id, viewerId);
        model.addAttribute("post", assembler.assemble(List.of(post), viewerId).get(0));
        return "post/detail";
    }

    @PostMapping("/posts/{id}/edit")
    public String edit(@PathVariable UUID id, @AuthenticationPrincipal AlouteUserPrincipal me,
                       @RequestParam(required = false) String content,
                       @RequestParam(required = false) Visibility visibility,
                       @RequestParam(required = false) String next,
                       RedirectAttributes flash) {
        try {
            posts.edit(me.id(), id, content, visibility);
            flash.addFlashAttribute("notice", "Đã lưu chỉnh sửa!");
        } catch (InvalidPostException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + SafeRedirect.sanitize(next);
    }

    @PostMapping("/posts/{id}/delete")
    public String delete(@PathVariable UUID id, @AuthenticationPrincipal AlouteUserPrincipal me,
                         @RequestParam(required = false) String next, RedirectAttributes flash) {
        posts.delete(me.id(), id);
        flash.addFlashAttribute("notice", "Đã xóa bài viết.");
        String target = SafeRedirect.sanitize(next);
        // Đang đứng ở chính trang chi tiết của bài vừa xóa thì quay về bảng tin, không thì gặp 404
        return "redirect:" + (target.contains(id.toString()) ? "/" : target);
    }

    @PostMapping("/posts/{id}/share")
    public String share(@PathVariable UUID id, @AuthenticationPrincipal AlouteUserPrincipal me,
                        @RequestParam(required = false) String caption,
                        @RequestParam(required = false) String next, RedirectAttributes flash) {
        try {
            posts.share(me.id(), id, caption);
            flash.addFlashAttribute("notice", "Đã chia sẻ bài viết!");
        } catch (InvalidPostException | RateLimitExceededException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + SafeRedirect.sanitize(next);
    }
}
