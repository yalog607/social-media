package com.aloute.chat;

import com.aloute.security.AlouteUserPrincipal;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

/**
 * Gửi tin nhắn qua AJAX (không tải lại trang) rồi đẩy ngay cho MỌI thành viên đang mở hội thoại này qua
 * WebSocket, kể cả người vừa gửi — nên client không cần tự chèn tin của mình, tất cả đều nhận qua cùng một kênh.
 */
@Controller
public class ChatMessageController {

    private final MessageService messages;
    private final SimpMessagingTemplate broker;

    public ChatMessageController(MessageService messages, SimpMessagingTemplate broker) {
        this.messages = messages;
        this.broker = broker;
    }

    @PostMapping("/api/conversations/{id}/messages")
    @ResponseBody
    public Map<String, Object> send(@PathVariable UUID id, @AuthenticationPrincipal AlouteUserPrincipal me,
                                    @RequestParam(required = false) String content,
                                    @RequestParam(required = false) MultipartFile attachment) {
        MessageView view = messages.send(me.id(), id, content, attachment);
        broker.convertAndSend("/topic/conversations/" + id, view);
        return Map.of("id", view.id().toString());
    }
}
