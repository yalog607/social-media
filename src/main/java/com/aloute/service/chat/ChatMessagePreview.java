package com.aloute.service.chat;

import com.aloute.model.chat.Message;
import java.util.regex.Pattern;

/** Nội dung ngắn, dễ đọc cho danh sách hội thoại; không thay đổi tin nhắn gốc. */
final class ChatMessagePreview {
    static final Pattern REPLY_PATTERN = Pattern.compile("^\\[(?:reply:|reply\\s*@|Trả lời\\s*@|[^:\\]]+@)(?:([0-9a-fA-F-]{36}):)?(?:@)?([^:\\]]+):\\s*(?:&quot;|\"|\\u201c)(.*?)(?:&quot;|\"|\\u201d)\\]\\r?\\n?");
    private static final Pattern STORY = Pattern.compile("^\\s*(?:💬\\s*)?\\[Khoảnh khắc(?: 24H)?\\](?:\\(/stories/[a-fA-F0-9-]+\\))?\\s*:?\\s*");
    private ChatMessagePreview() {}
    static String of(Message message) {
        if (message == null) return "";
        String text = message.getContent() == null ? "" : message.getContent();
        var reply = REPLY_PATTERN.matcher(text);
        boolean isReply = reply.find();
        if (isReply) text = text.substring(reply.end()).stripLeading();
        var story = STORY.matcher(text);
        boolean isStory = story.find();
        if (isStory) text = text.substring(story.end()).stripLeading();
        text = text.replaceAll("\\s+", " ").strip();
        boolean sticker = message.getSticker() != null || message.getGiphyId() != null;
        if (text.isEmpty()) {
            if (sticker) text = "Nhãn dán";
            else if (message.getAttachment() != null) text = "Tệp đính kèm";
        }
        if (isStory) return text.isEmpty() ? "Đã trả lời khoảnh khắc" : "Trả lời khoảnh khắc: " + text;
        if (isReply) return text.isEmpty() ? "Đã trả lời tin nhắn" : "Trả lời tin nhắn: " + text;
        if (sticker) return "Đã gửi một nhãn dán";
        if (message.getAttachment() != null && (message.getContent() == null || message.getContent().isBlank())) return "Đã gửi một tệp đính kèm";
        return text;
    }
}
