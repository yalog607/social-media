package com.aloute.dto.chat;

/** Tên/avatar hiển thị cho MỘT hội thoại theo góc nhìn của một người xem cụ thể. */
public record ConversationHeader(String title, String avatarUrl, boolean group) {
}
