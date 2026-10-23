package com.aloute.exception.chat;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Hội thoại không tồn tại hoặc người xem không phải thành viên. Gộp chung để không lộ hội thoại có tồn tại hay không. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ConversationNotFoundException extends RuntimeException {

    public ConversationNotFoundException() {
        super("Không tìm thấy cuộc trò chuyện này");
    }
}
