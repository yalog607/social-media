package com.aloute.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginForm {

    @NotBlank(message = "Nhập email hoặc username nhé")
    private String identifier;

    @NotBlank(message = "Nhập mật khẩu nhé")
    private String password;

    /** Trang cần quay lại sau khi đăng nhập (đã được kiểm tra là đường dẫn nội bộ). */
    private String next;
}
