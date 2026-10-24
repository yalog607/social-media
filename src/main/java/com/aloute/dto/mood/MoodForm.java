package com.aloute.dto.mood;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MoodForm {

    @NotBlank(message = "Vui lòng chọn hoặc nhập biểu tượng cảm xúc")
    @Size(max = 16, message = "Biểu tượng cảm xúc quá dài")
    private String emoji = "🥰";

    @NotBlank(message = "Hãy chia sẻ vài từ về tâm trạng hôm nay nhé")
    @Size(max = 60, message = "Tâm trạng tối đa 60 ký tự thôi nè")
    private String moodText;

    private String colorTheme = "lemon";
}
