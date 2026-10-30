package com.aloute.controller.user;

import com.aloute.guestbook.GuestbookService;
import com.aloute.model.user.User;
import com.aloute.repository.user.UserRepository;
import com.aloute.service.user.ProfileVisibilityRules;
import com.aloute.service.feed.FeedService;
import com.aloute.security.AlouteUserPrincipal;
import com.aloute.service.social.BlockService;
import com.aloute.service.social.FollowService;
import com.aloute.service.social.FriendService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final GuestbookService guestbook;
    private final com.aloute.service.mood.MoodService moodService;

    public ProfileController(UserRepository users, FeedService feed, ProfileVisibilityRules visibility,
                             FriendService friends, FollowService follows, BlockService blocks,
                             GuestbookService guestbook, com.aloute.service.mood.MoodService moodService) {
        this.users = users;
        this.feed = feed;
        this.visibility = visibility;
        this.friends = friends;
        this.follows = follows;
        this.blocks = blocks;
        this.guestbook = guestbook;
        this.moodService = moodService;
    }

    @GetMapping("/me")
    public String me(@AuthenticationPrincipal AlouteUserPrincipal principal) {
        return "redirect:/u/" + principal.username();
    }

    @GetMapping("/u/{username}")
    public String view(@PathVariable String username,
                       @RequestParam(defaultValue = "posts") String tab,
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
        model.addAttribute("ownerMood", moodService.getUserMood(owner.getId()).orElse(null));
        model.addAttribute("moodPresets", moodService.getPresets());
        // Ảnh đại diện luôn hiện dù hồ sơ riêng tư; phóng to thì vẫn phải theo đúng ý chủ hồ sơ (trừ chính họ)
        model.addAttribute("canZoomPhotos", isOwner || (!blockedEitherWay && owner.getProfile().isPhotoZoomEnabled()));
        model.addAttribute("canViewFriends", canViewFriends(owner, viewerId));
        model.addAttribute("canViewFollowing", canViewFollowing(owner, viewerId));
        if (!isOwner && viewerId != null) {
            model.addAttribute("friendState", friends.stateBetween(viewerId, owner.getId()));
            model.addAttribute("isFollowing", follows.isFollowing(viewerId, owner.getId()));
            model.addAttribute("hasBlocked", blocks.hasBlocked(viewerId, owner.getId()));
        }
        if (canView) {
            model.addAttribute("page", feed.byAuthor(owner.getId(), viewerId, null));
            model.addAttribute("pinnedPosts", feed.pinnedByAuthor(owner.getId(), viewerId));
            model.addAttribute("moreUrl", "/u/" + owner.getUsername() + "/posts");
            model.addAttribute("activeTab", "guestbook".equalsIgnoreCase(tab) ? "guestbook" : "posts");
            model.addAttribute("guestbookCount", guestbook.countFor(owner.getId(), viewerId));
            model.addAttribute("guestbookEntries", guestbook.listEntries(owner.getId(), viewerId));
            model.addAttribute("canSignGuestbook", !blockedEitherWay && guestbook.canSign(viewerId, owner.getId()));
        }
        return "profile/view";
    }

    @GetMapping("/u/{username}/friends-fragment")
    public String friendsFragment(@PathVariable String username,
                                  @AuthenticationPrincipal AlouteUserPrincipal viewer, Model model) {
        User owner = users.findByUsername(username).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!canViewFriends(owner, viewer == null ? null : viewer.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        model.addAttribute("friendsList", friends.friendsOf(owner.getId()));
        return "profile/lists :: friends";
    }

    @GetMapping("/u/{username}/followers-fragment")
    public String followersFragment(@PathVariable String username, Model model) {
        User owner = users.findByUsername(username).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("followersList", follows.followers(owner.getId()));
        return "profile/lists :: followers";
    }

    @GetMapping("/u/{username}/following-fragment")
    public String followingFragment(@PathVariable String username,
                                    @AuthenticationPrincipal AlouteUserPrincipal viewer, Model model) {
        User owner = users.findByUsername(username).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!canViewFollowing(owner, viewer == null ? null : viewer.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        model.addAttribute("followingList", follows.following(owner.getId()));
        return "profile/lists :: following";
    }

    /** Chủ hồ sơ luôn xem được; người khác phải không bị chặn và chủ hồ sơ đang cho phép. */
    private boolean canViewFriends(User owner, UUID viewerId) {
        return canViewList(owner, viewerId, owner.getProfile().isFriendsListVisible());
    }

    private boolean canViewFollowing(User owner, UUID viewerId) {
        return canViewList(owner, viewerId, owner.getProfile().isFollowingListVisible());
    }

    private boolean canViewList(User owner, UUID viewerId, boolean allowed) {
        if (viewerId != null && viewerId.equals(owner.getId())) {
            return true;
        }
        return allowed && !(viewerId != null && blocks.isBlockedEitherWay(owner.getId(), viewerId));
    }
}
