package com.aloute.comment;

import com.aloute.security.AlouteUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;
import java.util.UUID;

/**
 * Danh sách bình luận trả về MẢNH HTML (như {@code FeedController}), thêm/xóa trả JSON — JS tự chèn mảnh mới
 * vào cuối danh sách sau khi thêm thành công, đỡ phải tải lại cả trang.
 */
@Controller
public class CommentController {

    private final CommentService comments;

    public CommentController(CommentService comments) {
        this.comments = comments;
    }

    @GetMapping("/api/posts/{postId}/comments")
    public String list(@PathVariable UUID postId, @AuthenticationPrincipal AlouteUserPrincipal viewer, Model model) {
        UUID viewerId = viewer == null ? null : viewer.id();
        model.addAttribute("postId", postId);
        model.addAttribute("comments", comments.list(postId, viewerId));
        return "fragments/comment :: list";
    }

    @PostMapping("/api/posts/{postId}/comments")
    @ResponseBody
    public Map<String, Object> create(@PathVariable UUID postId, @RequestParam(required = false) UUID parentId,
                                      @RequestParam String content, @AuthenticationPrincipal AlouteUserPrincipal me) {
        Comment created = comments.create(me.id(), postId, parentId, content);
        return Map.of("id", created.getId().toString());
    }

    @PostMapping("/api/comments/{id}/delete")
    @ResponseBody
    public Map<String, Object> delete(@PathVariable UUID id, @AuthenticationPrincipal AlouteUserPrincipal me) {
        comments.delete(me.id(), id);
        return Map.of("ok", true);
    }
}
