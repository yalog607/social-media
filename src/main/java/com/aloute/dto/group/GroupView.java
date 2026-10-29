package com.aloute.dto.group;

import com.aloute.model.chat.GroupRole;

import java.time.Instant;
import java.util.UUID;

/**
 * Thông tin tổng quan của một hội nhóm phục vụ hiển thị trên trang /groups.
 */
public record GroupView(
        UUID id,
        String title,
        String description,
        String avatarUrl,
        long memberCount,
        boolean isMember,
        GroupRole myRole,
        boolean isPending,
        boolean approvalRequired,
        Instant createdAt,
        String ownerName,
        boolean chatEnabled,
        String rules,
        String approvalQuestion
) {
}
