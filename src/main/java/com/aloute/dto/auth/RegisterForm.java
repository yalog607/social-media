package com.aloute.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterForm {

    @NotBlank(message = "Nhập tên hiển thị nhé")
    @Size(min = 2, max = 50, message = "Tên hiển thị dài 2–50 ký tự")
    private String displayName;

    @NotBlank(message = "Chọn một username nhé")
    @Pattern(regexp = "^[a-zA-Z0-9_.]{3,30}$",
            message = "Username 3–30 ký tự: chữ, số, dấu chấm hoặc gạch dưới")
    private String username;

    @NotBlank(message = "Nhập email nhé")
    @Email(message = "Email chưa đúng định dạng")
    @Size(max = 255, message = "Email quá dài")
    private String email;

    @NotBlank(message = "Nhập mật khẩu nhé")
    @Size(min = 8, max = 72, message = "Mật khẩu dài 8–72 ký tự")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Mật khẩu cần có cả chữ và số")
    private String password;

    @NotBlank(message = "Nhập lại mật khẩu để chắc chắn nhé")
    private String confirmPassword;
}
