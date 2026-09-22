package com.aloute.user;

import com.aloute.security.AlouteUserPrincipal;
import com.aloute.security.SessionService;
import com.aloute.storage.StorageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Trang Cài đặt: hồ sơ, đổi mật khẩu, quyền riêng tư. */
@Controller
public class SettingsController {

    private static final String VIEW = "settings/index";

    private final UserRepository users;
    private final ProfileService profiles;
    private final SessionService sessions;

    public SettingsController(UserRepository users, ProfileService profiles, SessionService sessions) {
        this.users = users;
        this.profiles = profiles;
        this.sessions = sessions;
    }

    @GetMapping("/settings")
    public String settings(@AuthenticationPrincipal AlouteUserPrincipal principal, Model model) {
        User user = users.findById(principal.id()).orElseThrow();
        Profile p = user.getProfile();

        SettingsForms.ProfileForm profile = new SettingsForms.ProfileForm();
        profile.setDisplayName(p.getDisplayName());
        profile.setBio(p.getBio());
        SettingsForms.PrivacyForm privacy = new SettingsForms.PrivacyForm();
        privacy.setProfileVisibility(p.getProfileVisibility());
        privacy.setDefaultPostVisibility(p.getDefaultPostVisibility());
        privacy.setMessagePermission(p.getMessagePermission());

        model.addAttribute("profileForm", profile);
        model.addAttribute("privacyForm", privacy);
        return render(model, user, null, null, null);
    }

    @PostMapping("/settings/profile")
    public String saveProfile(@AuthenticationPrincipal AlouteUserPrincipal principal,
                              @Valid @ModelAttribute("profileForm") SettingsForms.ProfileForm form,
                              BindingResult errors, Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                profiles.updateProfile(principal.id(), form.getDisplayName(), form.getBio(),
                        form.getAvatar(), form.getCover());
                redirect.addFlashAttribute("notice", "Đã lưu hồ sơ!");
                return "redirect:/settings";
            } catch (StorageService.InvalidUploadException e) {
                errors.reject("upload.invalid", e.getMessage());
            }
        }
        User user = users.findById(principal.id()).orElseThrow();
        model.addAttribute("privacyForm", currentPrivacy(user));
        return render(model, user, form, null, null);
    }

    @PostMapping("/settings/password")
    public String changePassword(@AuthenticationPrincipal AlouteUserPrincipal principal,
                                 @Valid @ModelAttribute("passwordForm") SettingsForms.PasswordForm form,
                                 BindingResult errors, Model model, RedirectAttributes redirect,
                                 HttpServletRequest request, HttpServletResponse response) {
        if (form.getNewPassword() != null && !form.getNewPassword().equals(form.getConfirmPassword())) {
            errors.rejectValue("confirmPassword", "mismatch", "Hai mật khẩu chưa khớp nhau");
        }
        if (!errors.hasErrors()) {
            try {
                User user = profiles.changePassword(principal.id(), form.getCurrentPassword(), form.getNewPassword());
                // Các thiết bị khác đã bị đăng xuất; cấp lại phiên cho thiết bị đang dùng
                sessions.start(user, request, response);
                redirect.addFlashAttribute("notice", "Đã đổi mật khẩu. Các thiết bị khác đã được đăng xuất.");
                return "redirect:/settings";
            } catch (ProfileService.WrongCurrentPasswordException e) {
                errors.rejectValue("currentPassword", "wrong", e.getMessage());
            } catch (IllegalArgumentException e) {
                errors.rejectValue("newPassword", "weak", e.getMessage());
            }
        }
        User user = users.findById(principal.id()).orElseThrow();
        model.addAttribute("profileForm", currentProfile(user));
        model.addAttribute("privacyForm", currentPrivacy(user));
        return render(model, user, null, form, null);
    }

    @PostMapping("/settings/privacy")
    public String savePrivacy(@AuthenticationPrincipal AlouteUserPrincipal principal,
                              @Valid @ModelAttribute("privacyForm") SettingsForms.PrivacyForm form,
                              BindingResult errors, Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            User user = users.findById(principal.id()).orElseThrow();
            model.addAttribute("profileForm", currentProfile(user));
            return render(model, user, null, null, form);
        }
        profiles.updatePrivacy(principal.id(), form.getProfileVisibility(),
                form.getDefaultPostVisibility(), form.getMessagePermission());
        redirect.addFlashAttribute("notice", "Đã lưu quyền riêng tư!");
        return "redirect:/settings";
    }

    /** Điền các form chưa có trong model rồi trả về view. Tham số không null là form đang có lỗi cần giữ lại. */
    private String render(Model model, User user, SettingsForms.ProfileForm profile,
                          SettingsForms.PasswordForm password, SettingsForms.PrivacyForm privacy) {
        if (profile != null) {
            model.addAttribute("profileForm", profile);
        }
        if (privacy != null) {
            model.addAttribute("privacyForm", privacy);
        }
        model.addAttribute("passwordForm", password != null ? password : new SettingsForms.PasswordForm());
        model.addAttribute("hasPassword", profiles.hasPassword(user));
        model.addAttribute("visibilities", Visibility.values());
        model.addAttribute("messagePermissions", MessagePermission.values());
        return VIEW;
    }

    private static SettingsForms.ProfileForm currentProfile(User user) {
        SettingsForms.ProfileForm form = new SettingsForms.ProfileForm();
        form.setDisplayName(user.getProfile().getDisplayName());
        form.setBio(user.getProfile().getBio());
        return form;
    }

    private static SettingsForms.PrivacyForm currentPrivacy(User user) {
        Profile p = user.getProfile();
        SettingsForms.PrivacyForm form = new SettingsForms.PrivacyForm();
        form.setProfileVisibility(p.getProfileVisibility());
        form.setDefaultPostVisibility(p.getDefaultPostVisibility());
        form.setMessagePermission(p.getMessagePermission());
        return form;
    }
}
