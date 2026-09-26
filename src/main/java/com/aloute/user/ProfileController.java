package com.aloute.user;

import com.aloute.feed.FeedService;
import com.aloute.security.AlouteUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneId;
import java.util.UUID;
import java.time.format.DateTimeFormatter;

/** Trang cá nhân: của mình (/me) và của người khác (/u/{username}) theo quyền riêng tư. */
@Controller
public class ProfileController {

    private static final DateTimeFormatter JOINED =
            DateTimeFormatter.ofPattern("MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final UserRepository users;
    private final FeedService feed;

    public ProfileController(UserRepository users, FeedService feed) {
        this.users = users;
        this.feed = feed;
    }

    @GetMapping("/me")
    public String me(@AuthenticationPrincipal AlouteUserPrincipal principal) {
        return "redirect:/u/" + principal.username();
    }

    @GetMapping("/u/{username}")
    public String view(@PathVariable String username,
                       @AuthenticationPrincipal AlouteUserPrincipal viewer, Model model) {
        User owner = users.findByUsername(username)
                .filter(User::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        UUID viewerId = viewer == null ? null : viewer.id();
        boolean isOwner = viewerId != null && viewerId.equals(owner.getId());
        boolean canView = ProfileVisibilityRules.canView(owner, viewerId);

        model.addAttribute("owner", owner);
        model.addAttribute("isOwner", isOwner);
        model.addAttribute("canView", canView);
        model.addAttribute("joined", JOINED.format(owner.getCreatedAt()));
        if (canView) {
            model.addAttribute("page", feed.byAuthor(owner.getId(), viewerId, null));
            model.addAttribute("moreUrl", "/u/" + owner.getUsername() + "/posts");
        }
        return "profile/view";
    }
}
