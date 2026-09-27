package com.aloute.user;

import com.aloute.social.FriendService;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Ai được xem hồ sơ (và bài viết) của một người. Dùng chung cho trang cá nhân và danh sách bài của họ
 * để hai nơi không bao giờ lệch quy tắc.
 */
@Component
public class ProfileVisibilityRules {

    private final FriendService friends;

    public ProfileVisibilityRules(FriendService friends) {
        this.friends = friends;
    }

    /**
     * Chủ hồ sơ luôn xem được; bài/hồ sơ công khai ai cũng xem; "Chỉ bạn bè" chỉ bạn bè đã kết bạn xem được.
     *
     * @param viewerId null nếu là khách
     */
    public boolean canView(User owner, UUID viewerId) {
        boolean isOwner = viewerId != null && viewerId.equals(owner.getId());
        if (isOwner) {
            return true;
        }
        return switch (owner.getProfile().getProfileVisibility()) {
            case PUBLIC -> true;
            case FRIENDS -> viewerId != null && friends.areFriends(owner.getId(), viewerId);
            case PRIVATE -> false;
        };
    }
}
