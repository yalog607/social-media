package com.aloute.guestbook;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GuestbookForm {

    @NotBlank(message = "Nội dung lưu bút không được để trống nhé")
    @Size(max = 500, message = "Lời nhắn tối đa 500 ký tự thôi nè")
    private String content;

    private String colorTheme = "lemon";

    private String stickerIcon = "🌸";
}
