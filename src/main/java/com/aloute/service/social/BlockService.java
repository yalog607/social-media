package com.aloute.service.social;

import com.aloute.dto.social.AuthorViews;
import com.aloute.exception.social.SocialActionException;
import com.aloute.model.social.Block;
import com.aloute.repository.social.BlockRepository;
import com.aloute.repository.social.FollowRepository;
import com.aloute.repository.social.FriendshipRepository;

import com.aloute.dto.post.PostView;
import com.aloute.repository.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Chặn cắt đứt mọi quan hệ hiện có: xóa bạn bè (nếu có) và theo dõi hai chiều, rồi ngăn kết bạn/theo dõi/nhắn
 * tin mới cho tới khi ai đó bỏ chặn (bỏ chặn KHÔNG tự khôi phục lại các quan hệ đã mất).
 */
@Service
public class BlockService {

    private final BlockRepository blocks;
    private final FriendshipRepository friendships;
    private final FollowRepository follows;
    private final UserRepository users;

    public BlockService(BlockRepository blocks, FriendshipRepository friendships, FollowRepository follows,
                        UserRepository users) {
        this.blocks = blocks;
        this.friendships = friendships;
        this.follows = follows;
        this.users = users;
    }

    @Transactional
    public void block(UUID actorId, UUID targetId) {
        if (actorId.equals(targetId)) {
            throw new SocialActionException("Không thể tự chặn chính mình.");
        }
        if (!blocks.existsByBlockerIdAndBlockedId(actorId, targetId)) {
            Block block = new Block();
            block.setBlocker(users.getReferenceById(actorId));
            block.setBlocked(users.getReferenceById(targetId));
            blocks.save(block);
        }
        // So sánh theo chuỗi hex (không dùng UUID#compareTo, xem FriendService#pair) để khớp thứ tự user_a_id/user_b_id trong DB
        UUID lo = actorId.toString().compareTo(targetId.toString()) < 0 ? actorId : targetId;
        UUID hi = actorId.toString().compareTo(targetId.toString()) < 0 ? targetId : actorId;
        friendships.findPair(lo, hi).ifPresent(friendships::delete);
        follows.deleteByFollowerIdAndFolloweeId(actorId, targetId);
        follows.deleteByFollowerIdAndFolloweeId(targetId, actorId);
    }

    @Transactional
    public void unblock(UUID actorId, UUID targetId) {
        blocks.deleteByBlockerIdAndBlockedId(actorId, targetId);
    }

    public boolean isBlockedEitherWay(UUID a, UUID b) {
        return blocks.existsEitherWay(a, b);
    }

    public boolean hasBlocked(UUID actorId, UUID targetId) {
        return blocks.existsByBlockerIdAndBlockedId(actorId, targetId);
    }

    @Transactional(readOnly = true)
    public List<PostView.AuthorView> listBlocked(UUID userId) {
        return blocks.findBlockedByBlocker(userId).stream().map(b -> AuthorViews.of(b.getBlocked())).toList();
    }
}
