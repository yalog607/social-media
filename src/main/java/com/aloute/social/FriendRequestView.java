package com.aloute.social;

import com.aloute.post.PostView;

import java.time.Instant;

/** Một lời mời kết bạn đang chờ, kèm thông tin người kia. */
public record FriendRequestView(PostView.AuthorView person, Instant createdAt) {
}
