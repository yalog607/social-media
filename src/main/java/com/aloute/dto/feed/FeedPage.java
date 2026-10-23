package com.aloute.dto.feed;

import com.aloute.dto.post.PostView;

import java.util.List;

/** Một trang bài. {@code nextCursor} là null khi đã hết. */
public record FeedPage(List<PostView> posts, String nextCursor) {

    public boolean hasMore() {
        return nextCursor != null;
    }
}
