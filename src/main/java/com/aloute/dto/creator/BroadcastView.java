package com.aloute.dto.creator;

import com.aloute.model.creator.BroadcastAudience;

import java.time.Instant;

/** Một lần nhắn hàng loạt trong lịch sử của Creator. */
public record BroadcastView(String title, String body, BroadcastAudience audience, int recipientCount, Instant createdAt) {
}
