package com.aloute.notification;

import com.aloute.security.AlouteUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Trang thông báo: mở trang là các thông báo hiện có được đánh dấu đã đọc ngay (giống Facebook), nên danh sách
 * hiển thị ở trang này luôn dùng trạng thái đọc TRƯỚC khi đánh dấu để còn phân biệt thông báo mới.
 */
@Controller
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping("/notifications")
    public String list(@AuthenticationPrincipal AlouteUserPrincipal me, Model model) {
        model.addAttribute("items", notifications.listRecent(me.id()));
        notifications.markAllRead(me.id());
        return "notification/list";
    }
}
