package com.aloute.dto.creator;

import com.aloute.dto.post.PostView;

import java.time.Instant;

/** Một bài hẹn giờ chưa đăng, kèm giờ hẹn để hiển thị và đổi giờ. */
public record ScheduledPostView(PostView post, Instant scheduledAt) {
}
