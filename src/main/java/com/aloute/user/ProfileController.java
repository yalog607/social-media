package com.aloute.user;

import com.aloute.security.AlouteUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Trang cá nhân: của mình (/me) và của người khác (/u/{username}) theo quyền riêng tư. */
@Controller
public class ProfileController {

    private static final DateTimeFormatter JOINED =
            DateTimeFormatter.ofPattern("MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final UserRepository users;

    public ProfileController(UserRepository users) {
        this.users = users;
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

        boolean isOwner = viewer != null && viewer.id().equals(owner.getId());
        // Bạn bè chưa có ở GĐ1 nên "Chỉ bạn bè" tạm thời chỉ chủ hồ sơ xem được; GĐ3 sẽ mở cho bạn bè
        boolean canView = isOwner || owner.getProfile().getProfileVisibility() == Visibility.PUBLIC;

        model.addAttribute("owner", owner);
        model.addAttribute("isOwner", isOwner);
        model.addAttribute("canView", canView);
        model.addAttribute("joined", JOINED.format(owner.getCreatedAt()));
        return "profile/view";
    }
}
