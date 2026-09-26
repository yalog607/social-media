package com.aloute.feed;

import com.aloute.post.Post;
import com.aloute.post.PostRepository;
import com.aloute.post.PostViewAssembler;
import com.aloute.user.Visibility;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Bảng tin và danh sách bài của một người, phân trang theo con trỏ. */
@Service
public class FeedService {

    public static final int PAGE_SIZE = 10;

    private static final Set<Visibility> PUBLIC_ONLY = EnumSet.of(Visibility.PUBLIC);
    /** Chủ hồ sơ thấy mọi bài của mình. "Bạn bè" tạm coi như riêng tư nên cũng chỉ chủ bài thấy. */
    private static final Set<Visibility> ALL = EnumSet.allOf(Visibility.class);

    private final PostRepository posts;
    private final PostViewAssembler assembler;

    public FeedService(PostRepository posts, PostViewAssembler assembler) {
        this.posts = posts;
        this.assembler = assembler;
    }

    /** Bài công khai của mọi người cộng bài của chính {@code viewerId}, mới nhất trước. */
    @Transactional(readOnly = true)
    public FeedPage home(UUID viewerId, String cursor) {
        Cursor from = Cursor.decode(cursor).orElse(Cursor.START);
        // Lấy dư một bản ghi để biết còn trang sau hay không
        List<Post> rows = posts.feed(viewerId, from.createdAt(), from.id(), PageRequest.of(0, PAGE_SIZE + 1));
        return toPage(rows, viewerId);
    }

    /** Bài của {@code authorId}; người xem chỉ thấy các mức quyền mình được phép (khách: {@code viewerId = null}). */
    @Transactional(readOnly = true)
    public FeedPage byAuthor(UUID authorId, UUID viewerId, String cursor) {
        Cursor from = Cursor.decode(cursor).orElse(Cursor.START);
        Set<Visibility> visible = authorId.equals(viewerId) ? ALL : PUBLIC_ONLY;
        List<Post> rows = posts.byAuthor(authorId, visible, from.createdAt(), from.id(), PageRequest.of(0, PAGE_SIZE + 1));
        return toPage(rows, viewerId);
    }

    private FeedPage toPage(List<Post> rows, UUID viewerId) {
        boolean hasMore = rows.size() > PAGE_SIZE;
        List<Post> page = hasMore ? rows.subList(0, PAGE_SIZE) : rows;
        String next = hasMore ? Cursor.of(page.get(page.size() - 1)).encode() : null;
        return new FeedPage(assembler.assemble(page, viewerId), next);
    }
}
