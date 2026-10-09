package com.aloute.common;

import com.aloute.chat.ChatService;
import com.aloute.config.AlouteProperties;
import com.aloute.moderation.ModerationService;
import com.aloute.moderation.SupportService;
import com.aloute.notification.NotificationService;
import com.aloute.security.ActiveAccountInterceptor;
import com.aloute.security.AlouteUserPrincipal;
import com.aloute.user.User;
import com.aloute.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Set;
import java.util.stream.Collectors;

/** Dữ liệu dùng chung cho mọi template: người đang đăng nhập, quyền hiệu lực và cấu hình Firebase phía client. */
@ControllerAdvice
public class GlobalModelAdvice {

    private final UserRepository users;
    private final AlouteProperties props;
    private final RoleHierarchy roleHierarchy;
    private final NotificationService notifications;
    private final ChatService chats;
    private final ModerationService moderation;
    private final SupportService support;

    public GlobalModelAdvice(UserRepository users, AlouteProperties props, RoleHierarchy roleHierarchy,
                             NotificationService notifications, ChatService chats,
                             ModerationService moderation, SupportService support) {
        this.users = users;
        this.props = props;
        this.roleHierarchy = roleHierarchy;
        this.notifications = notifications;
        this.chats = chats;
        this.moderation = moderation;
        this.support = support;
    }

    /**
     * {@code null} nếu chưa đăng nhập. Người dùng đã được {@code ActiveAccountInterceptor} tải và kiểm tra
     * còn hoạt động; chỉ truy vấn lại ở các đường dẫn interceptor bỏ qua (như trang lỗi).
     */
    @ModelAttribute("me")
    public User me(HttpServletRequest request) {
        Object loaded = request.getAttribute(ActiveAccountInterceptor.CURRENT_USER_ATTRIBUTE);
        if (loaded instanceof User user) {
            return user;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AlouteUserPrincipal principal) {
            return users.findById(principal.id()).filter(User::isActive).orElse(null);
        }
        return null;
    }

    /**
     * Vai trò hiệu lực sau khi áp dụng kế thừa, ví dụ ADMIN → {ADMIN, MANAGER, USER}.
     * Template dùng {@code caps.contains('MANAGER')} để hiện/ẩn menu.
     */
    @ModelAttribute("caps")
    public Set<String> caps() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AlouteUserPrincipal)) {
            return Set.of();
        }
        return roleHierarchy.getReachableGrantedAuthorities(auth.getAuthorities()).stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Đường dẫn + query của trang hiện tại, để form (sửa/xóa bài...) quay lại đúng chỗ sau khi xử lý. */
    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        String query = request.getQueryString();
        return request.getRequestURI() + (query == null ? "" : "?" + query);
    }

    /** Số thông báo chưa đọc, hiện ở chuông thông báo trên topbar. 0 nếu chưa đăng nhập. */
    @ModelAttribute("unreadNotifications")
    public long unreadNotifications() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AlouteUserPrincipal principal)) {
            return 0;
        }
        return notifications.unreadCount(principal.id());
    }

    /** Số tin nhắn chưa đọc trên mọi hội thoại, hiện ở mục "Tin nhắn" trên thanh điều hướng. 0 nếu chưa đăng nhập. */
    @ModelAttribute("unreadMessages")
    public long unreadMessages() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AlouteUserPrincipal principal)) {
            return 0;
        }
        return chats.totalUnread(principal.id());
    }

    @ModelAttribute("firebaseWeb")
    public AlouteProperties.Firebase.Web firebaseWeb() {
        AlouteProperties.Firebase firebase = props.firebase();
        return firebase != null && firebase.webConfigured() && firebase.enabled() ? firebase.web() : null;
    }

    @ModelAttribute("pendingReports")
    public long pendingReports() {
        return caps().contains("MANAGER") ? moderation.countOpenReports() : 0;
    }

    @ModelAttribute("pendingSupportTickets")
    public long pendingSupportTickets() {
        return caps().contains("MANAGER") ? support.countOpenTickets() : 0;
    }
}
