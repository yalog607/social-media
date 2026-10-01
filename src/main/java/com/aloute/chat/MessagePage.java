package com.aloute.chat;

import java.util.List;

/** Một trang tin nhắn (cũ → mới) và con trỏ để tải trang cũ hơn; {@code nextCursor == null} nghĩa là đã hết. */
public record MessagePage(List<MessageView> messages, String nextCursor) {

    public boolean hasMore() {
        return nextCursor != null;
    }
}
