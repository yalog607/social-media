package com.aloute.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordForm {

    private String token;

    @NotBlank(message = "Nhập mật khẩu mới nhé")
    @Size(min = 8, max = 72, message = "Mật khẩu dài 8–72 ký tự")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Mật khẩu cần có cả chữ và số")
    private String password;

    @NotBlank(message = "Nhập lại mật khẩu để chắc chắn nhé")
    private String confirmPassword;
}
