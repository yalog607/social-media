package com.aloute.user;

import com.aloute.feed.FeedService;
import com.aloute.security.AlouteUserPrincipal;
import com.aloute.social.BlockService;
import com.aloute.social.FollowService;
import com.aloute.social.FriendService;
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
    private final ProfileVisibilityRules visibility;
    private final FriendService friends;
    private final FollowService follows;
    private final BlockService blocks;

    public ProfileController(UserRepository users, FeedService feed, ProfileVisibilityRules visibility,
                             FriendService friends, FollowService follows, BlockService blocks) {
        this.users = users;
        this.feed = feed;
        this.visibility = visibility;
        this.friends = friends;
        this.follows = follows;
        this.blocks = blocks;
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
        boolean blockedEitherWay = viewerId != null && blocks.isBlockedEitherWay(owner.getId(), viewerId);
        boolean canView = !blockedEitherWay && visibility.canView(owner, viewerId);

        model.addAttribute("owner", owner);
        model.addAttribute("isOwner", isOwner);
        model.addAttribute("canView", canView);
        model.addAttribute("joined", JOINED.format(owner.getCreatedAt()));
        model.addAttribute("friendCount", friends.friendCount(owner.getId()));
        model.addAttribute("followerCount", follows.followerCount(owner.getId()));
        model.addAttribute("followingCount", follows.followingCount(owner.getId()));
        model.addAttribute("blockedEitherWay", blockedEitherWay);
        // Ảnh đại diện luôn hiện dù hồ sơ riêng tư; phóng to thì vẫn phải theo đúng ý chủ hồ sơ (trừ chính họ)
        model.addAttribute("canZoomPhotos", isOwner || (!blockedEitherWay && owner.getProfile().isPhotoZoomEnabled()));
        if (!isOwner && viewerId != null) {
            model.addAttribute("friendState", friends.stateBetween(viewerId, owner.getId()));
            model.addAttribute("isFollowing", follows.isFollowing(viewerId, owner.getId()));
            model.addAttribute("hasBlocked", blocks.hasBlocked(viewerId, owner.getId()));
        }
        if (canView) {
            model.addAttribute("page", feed.byAuthor(owner.getId(), viewerId, null));
            model.addAttribute("moreUrl", "/u/" + owner.getUsername() + "/posts");
        }
        return "profile/view";
    }
}
