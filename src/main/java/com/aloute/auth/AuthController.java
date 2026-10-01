package com.aloute.auth;

import com.aloute.common.SafeRedirect;
import com.aloute.security.SessionService;
import com.aloute.user.AccountExistsException;
import com.aloute.user.User;
import com.aloute.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Các trang đăng ký, đăng nhập, quên/đặt lại mật khẩu và đăng xuất. */
@Controller
public class AuthController {

    private static final String LOGIN_VIEW = "auth/login";
    private static final String REGISTER_VIEW = "auth/register";
    private static final String FORGOT_VIEW = "auth/forgot-password";
    private static final String RESET_VIEW = "auth/reset-password";

    private final AuthService authService;
    private final UserService userService;
    private final PasswordResetService passwordResets;
    private final SessionService sessions;
    private final LoginAttemptService attempts;

    public AuthController(AuthService authService, UserService userService,
                          PasswordResetService passwordResets, SessionService sessions,
                          LoginAttemptService attempts) {
        this.authService = authService;
        this.userService = userService;
        this.passwordResets = passwordResets;
        this.sessions = sessions;
        this.attempts = attempts;
    }

    // ---------- Đăng nhập ----------

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String next, Model model) {
        if (isLoggedIn()) {
            return "redirect:" + SafeRedirect.sanitize(next);
        }
        LoginForm form = new LoginForm();
        form.setNext(SafeRedirect.sanitize(next));
        model.addAttribute("loginForm", form);
        return LOGIN_VIEW;
    }

    @PostMapping("/login")
    public String login(@Valid @ModelAttribute("loginForm") LoginForm form, BindingResult errors,
                        HttpServletRequest request, HttpServletResponse response) {
        form.setNext(SafeRedirect.sanitize(form.getNext()));
        if (errors.hasErrors()) {
            return LOGIN_VIEW;
        }
        String ip = request.getRemoteAddr();
        if (attempts.isBlocked(ip, form.getIdentifier())) {
            errors.reject("login.blocked", "Bạn thử sai nhiều lần rồi. Đợi "
                    + attempts.window().toMinutes() + " phút rồi thử lại nhé!");
            return LOGIN_VIEW;
        }
        try {
            User user = authService.authenticate(form.getIdentifier(), form.getPassword());
            attempts.reset(ip, form.getIdentifier());
            sessions.start(user, request, response);
            return "redirect:" + form.getNext();
        } catch (AuthService.InvalidCredentialsException e) {
            attempts.recordFailure(ip, form.getIdentifier());
            errors.reject("login.invalid", "Email/username hoặc mật khẩu chưa đúng");
        } catch (AuthService.AccountSuspendedException e) {
            errors.reject("login.suspended", "Tài khoản của bạn đang bị khóa. Liên hệ đội ngũ hỗ trợ để được giúp nhé");
        }
        return LOGIN_VIEW;
    }

    // ---------- Đăng ký ----------

    @GetMapping("/register")
    public String registerPage(Model model) {
        if (isLoggedIn()) {
            return "redirect:/";
        }
        model.addAttribute("registerForm", new RegisterForm());
        return REGISTER_VIEW;
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registerForm") RegisterForm form, BindingResult errors,
                           HttpServletRequest request, HttpServletResponse response) {
        if (form.getPassword() != null && !form.getPassword().equals(form.getConfirmPassword())) {
            errors.rejectValue("confirmPassword", "mismatch", "Hai mật khẩu chưa khớp nhau");
        }
        if (errors.hasErrors()) {
            return REGISTER_VIEW;
        }
        try {
            User user = userService.registerLocal(form.getDisplayName(), form.getUsername(),
                    form.getEmail(), form.getPassword());
            sessions.start(user, request, response);
            return "redirect:/";
        } catch (AccountExistsException e) {
            errors.rejectValue(e.field(), "exists", e.getMessage());
        } catch (IllegalArgumentException e) {
            errors.rejectValue("password", "weak", e.getMessage());
        } catch (com.aloute.user.RegistrationClosedException e) {
            errors.reject("registration.closed", e.getMessage());
        }
        return REGISTER_VIEW;
    }

    // ---------- Quên / đặt lại mật khẩu ----------

    @GetMapping("/forgot-password")
    public String forgotPage(Model model) {
        model.addAttribute("forgotPasswordForm", new ForgotPasswordForm());
        return FORGOT_VIEW;
    }

    @PostMapping("/forgot-password")
    public String forgot(@Valid @ModelAttribute("forgotPasswordForm") ForgotPasswordForm form,
                         BindingResult errors, Model model) {
        if (errors.hasErrors()) {
            return FORGOT_VIEW;
        }
        passwordResets.request(form.getEmail());
        // Luôn hiển thị cùng một thông báo dù email có tồn tại hay không
        model.addAttribute("sent", true);
        return FORGOT_VIEW;
    }

    @GetMapping("/reset-password")
    public String resetPage(@RequestParam(required = false) String token, Model model) {
        if (!passwordResets.isUsable(token)) {
            model.addAttribute("invalidToken", true);
            return RESET_VIEW;
        }
        ResetPasswordForm form = new ResetPasswordForm();
        form.setToken(token);
        model.addAttribute("resetPasswordForm", form);
        return RESET_VIEW;
    }

    @PostMapping("/reset-password")
    public String reset(@Valid @ModelAttribute("resetPasswordForm") ResetPasswordForm form,
                        BindingResult errors, Model model, RedirectAttributes redirect) {
        if (form.getPassword() != null && !form.getPassword().equals(form.getConfirmPassword())) {
            errors.rejectValue("confirmPassword", "mismatch", "Hai mật khẩu chưa khớp nhau");
        }
        if (errors.hasErrors()) {
            return RESET_VIEW;
        }
        try {
            if (!passwordResets.reset(form.getToken(), form.getPassword())) {
                model.addAttribute("invalidToken", true);
                return RESET_VIEW;
            }
        } catch (IllegalArgumentException e) {
            errors.rejectValue("password", "weak", e.getMessage());
            return RESET_VIEW;
        }
        redirect.addFlashAttribute("notice", "Đã đổi mật khẩu! Đăng nhập lại bằng mật khẩu mới nhé.");
        return "redirect:/login";
    }

    // ---------- Đăng xuất ----------

    @PostMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirect) {
        sessions.end(request, response);
        redirect.addFlashAttribute("notice", "Đã đăng xuất. Hẹn gặp lại nha!");
        return "redirect:/login";
    }

    private static boolean isLoggedIn() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }
}
