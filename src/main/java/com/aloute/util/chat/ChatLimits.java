package com.aloute.util.chat;

/** Giới hạn cho file đính kèm tin nhắn (khác {@code MediaLimits} của bài đăng: chat nhận cả file thường). */
public final class ChatLimits {

    /** Dung lượng tối đa của MỘT file đính kèm, áp dụng cho cả ảnh/video/file thường. */
    public static final long MAX_ATTACHMENT_BYTES = 20L * 1024 * 1024;

    private ChatLimits() {
    }
}
