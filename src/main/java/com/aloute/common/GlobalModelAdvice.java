package com.aloute.common;

import com.aloute.config.AlouteProperties;
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

    public GlobalModelAdvice(UserRepository users, AlouteProperties props, RoleHierarchy roleHierarchy) {
        this.users = users;
        this.props = props;
        this.roleHierarchy = roleHierarchy;
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

    @ModelAttribute("firebaseWeb")
    public AlouteProperties.Firebase.Web firebaseWeb() {
        AlouteProperties.Firebase firebase = props.firebase();
        return firebase != null && firebase.webConfigured() && firebase.enabled() ? firebase.web() : null;
    }
}
