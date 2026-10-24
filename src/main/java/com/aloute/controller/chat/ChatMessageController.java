package com.aloute.controller.chat;

import com.aloute.dto.chat.MessageReactionEvent;
import com.aloute.dto.chat.MessageView;
import com.aloute.dto.reaction.ReactionSummary;
import com.aloute.model.reaction.ReactionType;
import com.aloute.service.chat.ChatService;
import com.aloute.service.chat.MessageReactionService;
import com.aloute.service.chat.MessageService;

import com.aloute.security.AlouteUserPrincipal;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
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
    private final ChatService chats;
    private final MessageReactionService messageReactions;

    public ChatMessageController(MessageService messages, SimpMessagingTemplate broker,
                                 ChatService chats, MessageReactionService messageReactions) {
        this.chats = chats;
        this.messages = messages;
        this.broker = broker;
        this.messageReactions = messageReactions;
    }

    /** Thành viên (trừ chính mình) cho gợi ý @ khi gõ tin trong nhóm. */
    @GetMapping("/api/conversations/{id}/members")
    @ResponseBody
    public List<Map<String, String>> members(@PathVariable UUID id, @AuthenticationPrincipal AlouteUserPrincipal me) {
        chats.requireMembership(me.id(), id);
        return chats.memberViews(id).stream().filter(m -> !m.id().equals(me.id()))
                .map(m -> {
                    Map<String, String> row = new java.util.LinkedHashMap<>();
                    row.put("username", m.username());
                    row.put("displayName", m.displayName());
                    row.put("nickname", m.nickname());
                    return row;
                }).toList();
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

    @PostMapping("/api/conversations/{id}/messages/{messageId}/reaction")
    @ResponseBody
    public Map<String, Object> react(@PathVariable UUID id, @PathVariable UUID messageId,
                                     @RequestParam ReactionType type,
                                     @AuthenticationPrincipal AlouteUserPrincipal me) {
        ReactionSummary summary = messageReactions.toggle(me.id(), messageId, type);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ReactionType value : ReactionType.values()) {
            counts.put(value.name(), summary.count(value));
        }
        MessageReactionEvent event = new MessageReactionEvent(
                "REACTION",
                messageId,
                id,
                me.id(),
                summary.mine() == null ? "" : summary.mine().name(),
                counts,
                summary.total()
        );
        broker.convertAndSend("/topic/conversations/" + id, event);
        return Map.of(
                "counts", counts,
                "total", summary.total(),
                "mine", summary.mine() == null ? "" : summary.mine().name());
    }

    @GetMapping("/api/conversations/{id}/messages/{messageId}/reactions")
    public String reactors(@PathVariable UUID id, @PathVariable UUID messageId,
                           @AuthenticationPrincipal AlouteUserPrincipal me, Model model) {
        UUID viewerId = me == null ? null : me.id();
        model.addAttribute("reactors", messageReactions.reactors(messageId, viewerId));
        return "fragments/reactors :: list";
    }
}
