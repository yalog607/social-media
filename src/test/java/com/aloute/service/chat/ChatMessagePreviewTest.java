package com.aloute.service.chat;

import com.aloute.model.chat.Message;
import com.aloute.model.chat.MessageAttachment;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ChatMessagePreviewTest {
    private Message message(String content) {
        Message message = new Message();
        message.setContent(content);
        return message;
    }

    @Test void storyReplyHidesInternalLink() {
        assertThat(ChatMessagePreview.of(message("💬 [Khoảnh khắc](/stories/8bbe3e6b-b71e-4cf9-a3d8-619882dd578c): 123")))
                .isEqualTo("Trả lời khoảnh khắc: 123");
        assertThat(ChatMessagePreview.of(message("[Khoảnh khắc 24H]"))).isEqualTo("Đã trả lời khoảnh khắc");
    }

    @Test void messageReplyShowsReplyInsteadOfQuote() {
        assertThat(ChatMessagePreview.of(message("[reply:8bbe3e6b-b71e-4cf9-a3d8-619882dd578c:@Nhân: \"Tin cũ\"]\nXin chào")))
                .isEqualTo("Trả lời tin nhắn: Xin chào");
        assertThat(ChatMessagePreview.of(message("[Trả lời @Nhân: \"Tin cũ\"]\n"))).isEqualTo("Đã trả lời tin nhắn");
        Message reply = message("[reply:@Nhân: \"Tin cũ\"]");
        reply.setAttachment(new MessageAttachment());
        assertThat(ChatMessagePreview.of(reply)).isEqualTo("Trả lời tin nhắn: Tệp đính kèm");
    }

    @Test void ordinaryMessagesRemainReadable() {
        assertThat(ChatMessagePreview.of(message("Xin chào\nnhé"))).isEqualTo("Xin chào nhé");
        assertThat(ChatMessagePreview.of(null)).isEmpty();
    }
}
