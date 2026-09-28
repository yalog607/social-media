package com.aloute.notification;

/** Loại thông báo. {@link NotificationService} chọn câu chữ hiển thị theo loại này. */
public enum NotificationType {
    FRIEND_REQUEST,
    FRIEND_ACCEPTED,
    NEW_FOLLOWER,
    POST_REACTION,
    POST_COMMENT,
    COMMENT_REPLY,
    POST_SHARED
}
