package com.aloute.controller.comment;

import com.aloute.exception.comment.InvalidCommentException;
import com.aloute.model.comment.Comment;
import com.aloute.service.comment.CommentService;

import com.aloute.exception.media.InvalidMediaException;
import com.aloute.security.AlouteUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.aloute.dto.reaction.ReactionSummary;
import com.aloute.model.reaction.ReactionType;
import com.aloute.service.comment.CommentReactionService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Danh sách bình luận trả về MẢNH HTML (như {@code FeedController}), thêm/xóa trả JSON — JS tự chèn mảnh mới
 * vào cuối danh sách sau khi thêm thành công, đỡ phải tải lại cả trang.
 */
@Controller
public class CommentController {

    private final CommentService comments;
    private final CommentReactionService commentReactions;

    public CommentController(CommentService comments, CommentReactionService commentReactions) {
        this.comments = comments;
        this.commentReactions = commentReactions;
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
                                      @RequestParam(required = false) String content,
                                      @RequestParam(required = false) MultipartFile image,
                                      @AuthenticationPrincipal AlouteUserPrincipal me) {
        Comment created = comments.create(me.id(), postId, parentId, content, image);
        return Map.of("id", created.getId().toString());
    }

    /** Nội dung/ảnh không hợp lệ: 400 kèm thông báo tiếng Việt để JS hiện ngay cho người dùng. */
    @ExceptionHandler({InvalidCommentException.class, InvalidMediaException.class})
    public ResponseEntity<Map<String, String>> invalid(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }

    @PostMapping("/api/comments/{id}/delete")
    @ResponseBody
    public Map<String, Object> delete(@PathVariable UUID id, @AuthenticationPrincipal AlouteUserPrincipal me) {
        comments.delete(me.id(), id);
        return Map.of("ok", true);
    }

    @PostMapping("/api/comments/{id}/reaction")
    @ResponseBody
    public Map<String, Object> react(@PathVariable UUID id, @RequestParam ReactionType type,
                                     @AuthenticationPrincipal AlouteUserPrincipal me) {
        ReactionSummary summary = commentReactions.toggle(me.id(), id, type);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ReactionType value : ReactionType.values()) {
            counts.put(value.name(), summary.count(value));
        }
        return Map.of(
                "counts", counts,
                "total", summary.total(),
                "mine", summary.mine() == null ? "" : summary.mine().name());
    }

    @GetMapping("/api/comments/{id}/reactions")
    public String reactors(@PathVariable UUID id, @AuthenticationPrincipal AlouteUserPrincipal me, Model model) {
        UUID viewerId = me == null ? null : me.id();
        model.addAttribute("reactors", commentReactions.reactors(id, viewerId));
        return "fragments/reactors :: list";
    }
}
