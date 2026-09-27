package com.aloute.social;

import com.aloute.post.PostView;
import com.aloute.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Kết bạn hai chiều, cần bên kia chấp nhận (khác {@link FollowService}). Mỗi cặp chỉ một hàng trong
 * {@code friendships}, id nhỏ hơn luôn đứng ở {@code userA} — {@link #pair} chuẩn hóa thứ tự này ở MỌI thao tác.
 */
@Service
public class FriendService {

    private final FriendshipRepository friendships;
    private final UserRepository users;
    private final BlockService blocks;
    private final Clock clock;

    public FriendService(FriendshipRepository friendships, UserRepository users, BlockService blocks, Clock clock) {
        this.friendships = friendships;
        this.users = users;
        this.blocks = blocks;
        this.clock = clock;
    }

    record Pair(UUID a, UUID b) {
    }

    /**
     * So sánh theo chuỗi hex, KHÔNG dùng {@link UUID#compareTo}: nó so sánh có dấu (signed) trên từng nửa 64-bit,
     * còn ràng buộc {@code CHECK (user_a_id < user_b_id)} ở Postgres so sánh 128-bit không dấu — hai cách này
     * xếp thứ tự khác nhau với một số cặp UUID, gây lỗi vi phạm ràng buộc dù đã "chuẩn hóa" ở phía Java
     * (xem {@link com.aloute.social.FriendServicePairTest}). Gói-riêng (không {@code private}) để test được trực tiếp.
     */
    static Pair pair(UUID x, UUID y) {
        return x.toString().compareTo(y.toString()) < 0 ? new Pair(x, y) : new Pair(y, x);
    }

    /**
     * Gửi lời mời kết bạn. Nếu người kia đã mời mình trước thì tự động thành bạn luôn (khớp lời mời của cả hai
     * bên), giống Facebook/Instagram.
     *
     * @throws SocialActionException tự mời chính mình, đã bị chặn, hoặc đã là bạn/đã gửi lời mời rồi
     */
    @Transactional
    public void sendRequest(UUID actorId, UUID targetId) {
        if (actorId.equals(targetId)) {
            throw new SocialActionException("Không thể tự kết bạn với chính mình.");
        }
        if (blocks.isBlockedEitherWay(actorId, targetId)) {
            throw new SocialActionException("Không thể kết bạn với người này.");
        }
        Pair p = pair(actorId, targetId);
        Optional<Friendship> existing = friendships.findPair(p.a(), p.b());
        if (existing.isEmpty()) {
            Friendship friendship = new Friendship();
            friendship.setUserA(users.getReferenceById(p.a()));
            friendship.setUserB(users.getReferenceById(p.b()));
            friendship.setStatus(FriendshipStatus.PENDING);
            friendship.setRequestedBy(users.getReferenceById(actorId));
            friendships.save(friendship);
            return;
        }
        Friendship friendship = existing.get();
        if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
            throw new SocialActionException("Hai bạn đã là bạn bè rồi.");
        }
        if (friendship.getRequestedBy().getId().equals(actorId)) {
            throw new SocialActionException("Bạn đã gửi lời mời rồi, chờ người ta trả lời nhé.");
        }
        // Người kia đã mời mình từ trước: coi như mình vừa chấp nhận
        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendship.setRespondedAt(clock.instant());
    }

    /** @throws SocialActionException không có lời mời nào từ {@code requesterId} đang chờ {@code actorId} */
    @Transactional
    public void accept(UUID actorId, UUID requesterId) {
        Friendship friendship = pendingFrom(requesterId, actorId);
        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendship.setRespondedAt(clock.instant());
    }

    /** Từ chối lời mời {@code requesterId} đã gửi cho {@code actorId}. */
    @Transactional
    public void decline(UUID actorId, UUID requesterId) {
        friendships.delete(pendingFrom(requesterId, actorId));
    }

    /** Hủy lời mời chính {@code actorId} đã gửi cho {@code targetId}. */
    @Transactional
    public void cancel(UUID actorId, UUID targetId) {
        friendships.delete(pendingFrom(actorId, targetId));
    }

    @Transactional
    public void unfriend(UUID actorId, UUID targetId) {
        Pair p = pair(actorId, targetId);
        Friendship friendship = friendships.findPair(p.a(), p.b())
                .filter(f -> f.getStatus() == FriendshipStatus.ACCEPTED)
                .orElseThrow(() -> new SocialActionException("Hai bạn chưa phải bạn bè."));
        friendships.delete(friendship);
    }

    private Friendship pendingFrom(UUID requesterId, UUID receiverId) {
        Pair p = pair(requesterId, receiverId);
        return friendships.findPair(p.a(), p.b())
                .filter(f -> f.getStatus() == FriendshipStatus.PENDING && f.getRequestedBy().getId().equals(requesterId))
                .orElseThrow(() -> new SocialActionException("Không tìm thấy lời mời kết bạn này."));
    }

    public boolean areFriends(UUID a, UUID b) {
        Pair p = pair(a, b);
        return friendships.areFriends(p.a(), p.b());
    }

    public long friendCount(UUID userId) {
        return friendships.countAcceptedFor(userId);
    }

    /** Trạng thái kết bạn giữa {@code viewerId} và {@code otherId}, nhìn từ phía người xem. */
    @Transactional(readOnly = true)
    public FriendState stateBetween(UUID viewerId, UUID otherId) {
        if (viewerId == null || viewerId.equals(otherId)) {
            return FriendState.NONE;
        }
        Pair p = pair(viewerId, otherId);
        return friendships.findPair(p.a(), p.b()).map(f -> switch (f.getStatus()) {
            case ACCEPTED -> FriendState.FRIENDS;
            case PENDING -> f.getRequestedBy().getId().equals(viewerId) ? FriendState.OUTGOING : FriendState.INCOMING;
        }).orElse(FriendState.NONE);
    }

    @Transactional(readOnly = true)
    public List<PostView.AuthorView> friendsOf(UUID userId) {
        return friendships.findAcceptedFor(userId).stream().map(f -> AuthorViews.of(f.other(userId))).toList();
    }

    /** Lời mời người khác đang chờ {@code userId} trả lời. */
    @Transactional(readOnly = true)
    public List<FriendRequestView> incomingRequests(UUID userId) {
        return friendships.findPendingFor(userId).stream()
                .filter(f -> !f.getRequestedBy().getId().equals(userId))
                .map(f -> new FriendRequestView(AuthorViews.of(f.other(userId)), f.getCreatedAt()))
                .toList();
    }

    /** Lời mời chính {@code userId} đã gửi, đang chờ người ta trả lời. */
    @Transactional(readOnly = true)
    public List<FriendRequestView> outgoingRequests(UUID userId) {
        return friendships.findPendingFor(userId).stream()
                .filter(f -> f.getRequestedBy().getId().equals(userId))
                .map(f -> new FriendRequestView(AuthorViews.of(f.other(userId)), f.getCreatedAt()))
                .toList();
    }
}
