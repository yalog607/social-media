package com.aloute.dto.chat;

import com.aloute.model.chat.AttachmentKind;

public record AttachmentView(AttachmentKind kind, String url, String contentType, String originalName) {
}
