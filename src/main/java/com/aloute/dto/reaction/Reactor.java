package com.aloute.dto.reaction;

import com.aloute.model.reaction.ReactionType;

import com.aloute.dto.post.PostView;

/** Một người đã thả cảm xúc và loại cảm xúc của họ. */
public record Reactor(PostView.AuthorView user, ReactionType type) {
}
