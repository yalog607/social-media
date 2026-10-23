package com.aloute.dto.user;

import com.aloute.model.user.MessagePermission;
import com.aloute.model.user.Visibility;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

/** Các form của trang Cài đặt. */
public final class SettingsForms {

    private SettingsForms() {
    }

    @Getter
    @Setter
    public static class ProfileForm {
        @NotBlank(message = "Tên hiển thị không được để trống")
        @Size(min = 2, max = 50, message = "Tên hiển thị dài 2–50 ký tự")
        private String displayName;

        @Size(max = 300, message = "Giới thiệu tối đa 300 ký tự")
        private String bio;

        private MultipartFile avatar;
        private MultipartFile cover;
    }

    @Getter
    @Setter
    public static class PasswordForm {
        private String currentPassword;

        @NotBlank(message = "Nhập mật khẩu mới nhé")
        @Size(max = 72, message = "Mật khẩu tối đa 72 ký tự")
        private String newPassword;

        @NotBlank(message = "Nhập lại mật khẩu mới để chắc chắn nhé")
        private String confirmPassword;
    }

    @Getter
    @Setter
    public static class PrivacyForm {
        @NotNull
        private Visibility profileVisibility = Visibility.PUBLIC;
        @NotNull
        private Visibility defaultPostVisibility = Visibility.PUBLIC;
        @NotNull
        private MessagePermission messagePermission = MessagePermission.EVERYONE;
        private boolean photoZoomEnabled = true;
    }
}
