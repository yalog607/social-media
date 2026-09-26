package com.aloute.feed;

import com.aloute.security.AlouteUserPrincipal;
import com.aloute.user.ProfileVisibilityRules;
import com.aloute.user.User;
import com.aloute.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Trả về MẢNH HTML (không phải cả trang) cho nút "Xem thêm": JS chèn thẳng vào cuối danh sách hiện có.
 * Mảnh chứa các thẻ bài kế tiếp và, nếu còn, một nút "Xem thêm" mới mang con trỏ của trang kế.
 */
@Controller
public class FeedController {

    private static final String LIST_FRAGMENT = "feed/list :: list";

    private final FeedService feed;
    private final UserRepository users;

    public FeedController(FeedService feed, UserRepository users) {
        this.feed = feed;
        this.users = users;
    }

    @GetMapping("/feed")
    public String more(@AuthenticationPrincipal AlouteUserPrincipal me,
                       @RequestParam(required = false) String cursor, Model model) {
        model.addAttribute("page", feed.home(me.id(), cursor));
        model.addAttribute("moreUrl", "/feed");
        return LIST_FRAGMENT;
    }

    @GetMapping("/u/{username}/posts")
    public String userPosts(@PathVariable String username, @AuthenticationPrincipal AlouteUserPrincipal viewer,
                            @RequestParam(required = false) String cursor, Model model) {
        User owner = users.findByUsername(username)
                .filter(User::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        UUID viewerId = viewer == null ? null : viewer.id();
        if (!ProfileVisibilityRules.canView(owner, viewerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("page", feed.byAuthor(owner.getId(), viewerId, cursor));
        model.addAttribute("moreUrl", "/u/" + owner.getUsername() + "/posts");
        return LIST_FRAGMENT;
    }
}
