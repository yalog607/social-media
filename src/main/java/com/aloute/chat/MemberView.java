package com.aloute.chat;

import java.util.UUID;

/** Một thành viên của hội thoại để hiển thị: tên thật, biệt danh (nếu có) và vai trò. */
public record MemberView(UUID id, String username, String displayName, String nickname, String avatarUrl, GroupRole role) {

    /** Tên hiển thị trong chat: biệt danh nếu có, không thì tên thật. */
    public String shownName() {
        return nickname != null ? nickname : displayName;
    }

    public boolean isOwner() {
        return role == GroupRole.OWNER;
    }
}
