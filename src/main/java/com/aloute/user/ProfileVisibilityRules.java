package com.aloute.user;

import java.util.UUID;

/**
 * Ai được xem hồ sơ (và bài viết) của một người. Dùng chung cho trang cá nhân và danh sách bài của họ
 * để hai nơi không bao giờ lệch quy tắc.
 */
public final class ProfileVisibilityRules {

    private ProfileVisibilityRules() {
    }

    /**
     * Chủ hồ sơ luôn xem được; người khác (kể cả khách) chỉ xem khi hồ sơ công khai. "Chỉ bạn bè" tạm thời
     * chỉ mình chủ xem cho tới khi có tính năng kết bạn (giai đoạn 3).
     *
     * @param viewerId null nếu là khách
     */
    public static boolean canView(User owner, UUID viewerId) {
        boolean isOwner = viewerId != null && viewerId.equals(owner.getId());
        return isOwner || owner.getProfile().getProfileVisibility() == Visibility.PUBLIC;
    }
}
