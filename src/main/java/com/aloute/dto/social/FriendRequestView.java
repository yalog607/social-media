package com.aloute.dto.social;

import com.aloute.dto.post.PostView;

import java.time.Instant;

/** Một lời mời kết bạn đang chờ, kèm thông tin người kia. */
public record FriendRequestView(PostView.AuthorView person, Instant createdAt) {
}
