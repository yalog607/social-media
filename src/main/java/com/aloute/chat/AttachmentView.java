package com.aloute.chat;

public record AttachmentView(AttachmentKind kind, String url, String contentType, String originalName) {
}
