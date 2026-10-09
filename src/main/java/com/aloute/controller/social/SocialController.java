package com.aloute.controller.social;

import com.aloute.exception.social.SocialActionException;
import com.aloute.service.social.BlockService;
import com.aloute.service.social.FollowService;
import com.aloute.service.social.FriendService;

import com.aloute.util.common.SafeRedirect;
import com.aloute.security.AlouteUserPrincipal;
import com.aloute.model.user.User;
import com.aloute.repository.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Kết bạn, theo dõi, chặn — mỗi thao tác là một form POST thường (không JS), quay lại đúng chỗ qua {@code next}.
 * Lỗi (tự thao tác với chính mình, đã bị chặn, sai trạng thái) đưa lại thành thông báo, không phải trang lỗi.
 */
@Controller
public class SocialController {

    private final UserRepository users;
    private final FriendService friends;
    private final FollowService follows;
    private final BlockService blocks;

    public SocialController(UserRepository users, FriendService friends, FollowService follows, BlockService blocks) {
        this.users = users;
        this.friends = friends;
        this.follows = follows;
        this.blocks = blocks;
    }

    @GetMapping("/friends")
    public String friends(@AuthenticationPrincipal AlouteUserPrincipal me, Model model) {
        model.addAttribute("friends", friends.friendsOf(me.id()));
        model.addAttribute("incoming", friends.incomingRequests(me.id()));
        model.addAttribute("outgoing", friends.outgoingRequests(me.id()));
        return "social/friends";
    }

    @PostMapping("/u/{username}/friend-request")
    public String sendRequest(@PathVariable String username, @AuthenticationPrincipal AlouteUserPrincipal me,
                              @RequestParam(required = false) String next, RedirectAttributes flash) {
        return act(username, me, next, flash, friends::sendRequest);
    }

    @PostMapping("/u/{username}/friend-accept")
    public String accept(@PathVariable String username, @AuthenticationPrincipal AlouteUserPrincipal me,
                         @RequestParam(required = false) String next, RedirectAttributes flash) {
        return act(username, me, next, flash, friends::accept);
    }

    @PostMapping("/u/{username}/friend-decline")
    public String decline(@PathVariable String username, @AuthenticationPrincipal AlouteUserPrincipal me,
                          @RequestParam(required = false) String next, RedirectAttributes flash) {
        return act(username, me, next, flash, friends::decline);
    }

    @PostMapping("/u/{username}/friend-cancel")
    public String cancel(@PathVariable String username, @AuthenticationPrincipal AlouteUserPrincipal me,
                         @RequestParam(required = false) String next, RedirectAttributes flash) {
        return act(username, me, next, flash, friends::cancel);
    }

    @PostMapping("/u/{username}/unfriend")
    public String unfriend(@PathVariable String username, @AuthenticationPrincipal AlouteUserPrincipal me,
                           @RequestParam(required = false) String next, RedirectAttributes flash) {
        return act(username, me, next, flash, friends::unfriend);
    }

    @PostMapping("/u/{username}/follow")
    public String follow(@PathVariable String username, @AuthenticationPrincipal AlouteUserPrincipal me,
                         @RequestParam(required = false) String next, RedirectAttributes flash) {
        return act(username, me, next, flash, follows::follow);
    }

    @PostMapping("/u/{username}/unfollow")
    public String unfollow(@PathVariable String username, @AuthenticationPrincipal AlouteUserPrincipal me,
                           @RequestParam(required = false) String next, RedirectAttributes flash) {
        return act(username, me, next, flash, follows::unfollow);
    }

    @PostMapping("/u/{username}/block")
    public String block(@PathVariable String username, @AuthenticationPrincipal AlouteUserPrincipal me,
                        @RequestParam(required = false) String next, RedirectAttributes flash) {
        return act(username, me, next, flash, blocks::block);
    }

    @PostMapping("/u/{username}/unblock")
    public String unblock(@PathVariable String username, @AuthenticationPrincipal AlouteUserPrincipal me,
                          @RequestParam(required = false) String next, RedirectAttributes flash) {
        return act(username, me, next, flash, blocks::unblock);
    }

    private String act(String username, AlouteUserPrincipal me, String next, RedirectAttributes flash,
                       BiConsumer<UUID, UUID> action) {
        User target = users.findByUsername(username).filter(User::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        try {
            action.accept(me.id(), target.getId());
        } catch (SocialActionException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + SafeRedirect.sanitize(next);
    }
}
