package com.aloute.post;

import com.aloute.user.Profile;
import com.aloute.user.User;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chuyển một danh sách bài thành {@link PostView}. Mọi dữ liệu quan hệ được nạp theo LÔ cho cả danh sách
 * (không truy vấn riêng cho từng bài).
 */
@Component
public class PostViewAssembler {

    private final PostMediaRepository mediaRepository;

    public PostViewAssembler(PostMediaRepository mediaRepository) {
        this.mediaRepository = mediaRepository;
    }

    /** @param viewerId người xem, null nếu là khách (để đánh dấu bài "của tôi") */
    @Transactional(readOnly = true)
    public List<PostView> assemble(List<Post> posts, UUID viewerId) {
        if (posts.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<PostView.MediaView>> mediaByPost = loadMedia(posts.stream().map(Post::getId).toList());
        return posts.stream().map(post -> toView(post, viewerId, mediaByPost)).toList();
    }

    private Map<UUID, List<PostView.MediaView>> loadMedia(Collection<UUID> postIds) {
        Map<UUID, List<PostView.MediaView>> byPost = new HashMap<>();
        for (PostMedia item : mediaRepository.findByPostIds(postIds)) {
            byPost.computeIfAbsent(item.getPost().getId(), id -> new java.util.ArrayList<>())
                    .add(new PostView.MediaView(item.getKind(), item.getUrl(), item.getContentType()));
        }
        return byPost;
    }

    private static PostView toView(Post post, UUID viewerId, Map<UUID, List<PostView.MediaView>> mediaByPost) {
        User author = post.getAuthor();
        Profile profile = author.getProfile();
        boolean mine = viewerId != null && viewerId.equals(author.getId());
        return new PostView(
                post.getId(),
                new PostView.AuthorView(author.getId(), author.getUsername(), profile.getDisplayName(),
                        profile.getAvatarUrl(), author.primaryRole()),
                PostTextRenderer.toSafeHtml(post.getContent()),
                post.getVisibility(),
                post.getCreatedAt(),
                post.getEditedAt() != null,
                mediaByPost.getOrDefault(post.getId(), List.of()),
                mine,
                mine ? post.getContent() : null);
    }
}
