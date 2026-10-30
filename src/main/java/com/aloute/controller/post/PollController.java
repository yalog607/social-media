package com.aloute.controller.post;

import com.aloute.exception.post.InvalidPostException;
import com.aloute.security.AlouteUserPrincipal;
import com.aloute.service.creator.PollService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;
import java.util.UUID;

/** Bầu một lựa chọn trong bình chọn của Creator. Mọi người dùng đã đăng nhập đều bầu được (nếu xem được bài). */
@Controller
public class PollController {

    private final PollService polls;

    public PollController(PollService polls) {
        this.polls = polls;
    }

    @PostMapping("/polls/{postId}/vote")
    public String vote(@AuthenticationPrincipal AlouteUserPrincipal me, @PathVariable UUID postId,
                       @RequestParam UUID optionId, HttpServletRequest request, RedirectAttributes flash) {
        try {
            polls.vote(me.id(), postId, optionId);
        } catch (InvalidPostException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + backTo(request, postId);
    }

    /** Quay lại trang vừa bấm nếu cùng máy chủ (tránh chuyển hướng ra ngoài); ngược lại về trang riêng của bài. */
    private static String backTo(HttpServletRequest request, UUID postId) {
        String referer = request.getHeader("Referer");
        if (referer != null) {
            try {
                URI uri = URI.create(referer);
                String path = uri.getRawPath();
                if (request.getServerName().equalsIgnoreCase(uri.getHost()) && path != null && path.startsWith("/")
                        && !path.startsWith("//") && !path.contains("\\")) {
                    return uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
                }
            } catch (IllegalArgumentException ignored) {
                // Referer lỗi định dạng: dùng trang mặc định
            }
        }
        return "/posts/" + postId;
    }
}
