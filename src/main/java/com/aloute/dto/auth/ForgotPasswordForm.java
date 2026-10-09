package com.aloute.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForgotPasswordForm {

    @NotBlank(message = "Nhập email của bạn nhé")
    @Email(message = "Email chưa đúng định dạng")
    private String email;
}
