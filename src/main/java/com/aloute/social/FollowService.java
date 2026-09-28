package com.aloute.social;

import com.aloute.notification.NotificationService;
import com.aloute.post.PostView;
import com.aloute.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Theo dõi một chiều: không cần người kia đồng ý, khác {@link FriendService}. */
@Service
public class FollowService {

    private final FollowRepository follows;
    private final UserRepository users;
    private final BlockService blocks;
    private final NotificationService notifications;

    public FollowService(FollowRepository follows, UserRepository users, BlockService blocks,
                         NotificationService notifications) {
        this.follows = follows;
        this.users = users;
        this.blocks = blocks;
        this.notifications = notifications;
    }

    /** @throws SocialActionException tự theo dõi chính mình, hoặc một trong hai đã chặn người kia */
    @Transactional
    public void follow(UUID actorId, UUID targetId) {
        if (actorId.equals(targetId)) {
            throw new SocialActionException("Không thể tự theo dõi chính mình.");
        }
        if (blocks.isBlockedEitherWay(actorId, targetId)) {
            throw new SocialActionException("Không thể theo dõi người này.");
        }
        if (!follows.existsByFollowerIdAndFolloweeId(actorId, targetId)) {
            Follow follow = new Follow();
            follow.setFollower(users.getReferenceById(actorId));
            follow.setFollowee(users.getReferenceById(targetId));
            follows.save(follow);
            notifications.newFollower(actorId, targetId);
        }
    }

    @Transactional
    public void unfollow(UUID actorId, UUID targetId) {
        follows.deleteByFollowerIdAndFolloweeId(actorId, targetId);
    }

    public boolean isFollowing(UUID actorId, UUID targetId) {
        return follows.existsByFollowerIdAndFolloweeId(actorId, targetId);
    }

    public long followerCount(UUID userId) {
        return follows.countByFolloweeId(userId);
    }

    public long followingCount(UUID userId) {
        return follows.countByFollowerId(userId);
    }

    @Transactional(readOnly = true)
    public List<PostView.AuthorView> followers(UUID userId) {
        return follows.findFollowers(userId).stream().map(f -> AuthorViews.of(f.getFollower())).toList();
    }

    @Transactional(readOnly = true)
    public List<PostView.AuthorView> following(UUID userId) {
        return follows.findFollowing(userId).stream().map(f -> AuthorViews.of(f.getFollowee())).toList();
    }
}
