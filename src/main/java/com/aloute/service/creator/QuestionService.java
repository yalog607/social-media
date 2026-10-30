package com.aloute.service.creator;

import com.aloute.exception.post.InvalidPostException;
import com.aloute.model.notification.NotificationType;
import com.aloute.model.post.Post;
import com.aloute.model.post.PostKind;
import com.aloute.model.user.Role;
import com.aloute.model.user.User;
import com.aloute.model.user.Visibility;
import com.aloute.repository.post.PostRepository;
import com.aloute.repository.user.UserRepository;
import com.aloute.service.post.PostService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Hỏi đáp công khai: Creator đặt câu hỏi, mọi người trả lời bằng bình luận. Câu hỏi là một {@link Post}
 * ({@link PostKind#QUESTION}) luôn công khai; khi tạo, người theo dõi nhận thông báo, các câu trả lời sau đó không gửi thêm.
 */
@Service
public class QuestionService {

    private final PostService posts;
    private final PostRepository postRepository;
    private final UserRepository users;
    private final FollowerNotifier notifier;

    public QuestionService(PostService posts, PostRepository postRepository, UserRepository users, FollowerNotifier notifier) {
        this.posts = posts;
        this.postRepository = postRepository;
        this.users = users;
        this.notifier = notifier;
    }

    /** @throws InvalidPostException không phải Creator, câu hỏi trống/quá dài hoặc đăng quá nhanh */
    @Transactional
    public Post create(UUID creatorId, String question) {
        User creator = users.findById(creatorId).filter(User::isActive).filter(u -> u.hasRole(Role.CREATOR))
                .orElseThrow(() -> new InvalidPostException("Chỉ Creator mới đặt được câu hỏi."));
        if (question == null || question.isBlank()) {
            throw new InvalidPostException("Hãy nhập câu hỏi.");
        }
        Post post = posts.create(creator.getId(), question, Visibility.PUBLIC, null, null, List.of(), null, null, null, false);
        post.setPostKind(PostKind.QUESTION);
        notifier.notifyFollowers(creatorId, post.getId(), NotificationType.CREATOR_QUESTION);
        return post;
    }

    /** Các câu hỏi của một Creator cho khối "Hỏi đáp & Poll" trên trang hồ sơ (luôn công khai), mới nhất trước. */
    @Transactional(readOnly = true)
    public List<Post> forProfile(UUID creatorId) {
        return postRepository.findByAuthorIdAndPostKindIn(creatorId, List.of(PostKind.QUESTION));
    }
}
