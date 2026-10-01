package com.aloute.reaction;

import com.aloute.post.PostView;

/** Một người đã thả cảm xúc và loại cảm xúc của họ. */
public record Reactor(PostView.AuthorView user, ReactionType type) {
}
