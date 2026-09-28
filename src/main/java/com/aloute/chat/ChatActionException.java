package com.aloute.chat;

/** Một thao tác chat không hợp lệ (bị chặn, không được nhắn theo cài đặt riêng tư, sai vai trò nhóm...). */
public class ChatActionException extends RuntimeException {
    public ChatActionException(String message) {
        super(message);
    }
}
