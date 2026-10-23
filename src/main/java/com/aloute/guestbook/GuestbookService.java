package com.aloute.guestbook;

import com.aloute.notification.NotificationService;
import com.aloute.post.PostView.AuthorView;
import com.aloute.social.BlockService;
import com.aloute.social.FriendService;
import com.aloute.social.SocialActionException;
import com.aloute.user.Profile;
import com.aloute.user.User;
import com.aloute.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class GuestbookService {

    private static final Set<String> ALLOWED_COLORS = Set.of("lemon", "pink", "mint", "lavender", "sky");
    private static final Set<String> ALLOWED_STICKERS = Set.of("🌸", "💌", "⭐", "🎓", "🧋", "🍀", "🔥");

    private final GuestbookRepository guestbooks;
    private final UserRepository users;
    private final FriendService friends;
    private final BlockService blocks;
    private final NotificationService notifications;

    public GuestbookService(GuestbookRepository guestbooks, UserRepository users, FriendService friends,
                            BlockService blocks, NotificationService notifications) {
        this.guestbooks = guestbooks;
        this.users = users;
        this.friends = friends;
        this.blocks = blocks;
        this.notifications = notifications;
    }

    @Transactional
    public GuestbookView addEntry(UUID actorId, String targetUsername, GuestbookForm form) {
        User actor = users.findById(actorId)
                .filter(User::isActive)
                .orElseThrow(() -> new SocialActionException("Tài khoản người dùng không hợp lệ."));

        User targetUser = users.findByUsername(targetUsername)
                .filter(User::isActive)
                .orElseThrow(() -> new SocialActionException("Không tìm thấy người dùng này."));

        if (blocks.isBlockedEitherWay(actorId, targetUser.getId())) {
            throw new SocialActionException("Bạn không thể dán lưu bút cho người này.");
        }

        boolean isSelf = actorId.equals(targetUser.getId());
        if (!isSelf && !friends.areFriends(actorId, targetUser.getId())) {
            throw new SocialActionException("Chỉ bạn bè mới có thể dán mẩu lưu bút nhé.");
        }

        String content = form.getContent() != null ? form.getContent().trim() : "";
        if (content.isEmpty()) {
            throw new SocialActionException("Nội dung lưu bút không được để trống.");
        }
        if (content.length() > 500) {
            throw new SocialActionException("Nội dung lưu bút tối đa 500 ký tự.");
        }

        String color = form.getColorTheme() != null && ALLOWED_COLORS.contains(form.getColorTheme())
                ? form.getColorTheme()
                : "lemon";

        String sticker = form.getStickerIcon() != null && ALLOWED_STICKERS.contains(form.getStickerIcon())
                ? form.getStickerIcon()
                : "🌸";

        GuestbookEntry entry = new GuestbookEntry(targetUser, actor, content, color, sticker);
        guestbooks.save(entry);

        if (!isSelf) {
            notifications.guestbookEntryAdded(actorId, targetUser.getId());
        }

        return toView(entry, actorId, targetUser.getId());
    }

    @Transactional
    public void togglePin(UUID actorId, UUID entryId) {
        GuestbookEntry entry = guestbooks.findWithUsersById(entryId)
                .orElseThrow(() -> new SocialActionException("Không tìm thấy mẩu lưu bút này."));

        if (!entry.getProfileUser().getId().equals(actorId)) {
            throw new SocialActionException("Chỉ chủ trang cá nhân mới có quyền ghim lưu bút.");
        }

        entry.setPinned(!entry.isPinned());
    }

    @Transactional
    public void toggleHide(UUID actorId, UUID entryId) {
        GuestbookEntry entry = guestbooks.findWithUsersById(entryId)
                .orElseThrow(() -> new SocialActionException("Không tìm thấy mẩu lưu bút này."));

        if (!entry.getProfileUser().getId().equals(actorId)) {
            throw new SocialActionException("Chỉ chủ trang cá nhân mới có quyền ẩn lưu bút.");
        }

        entry.setHidden(!entry.isHidden());
    }

    @Transactional
    public void delete(UUID actorId, UUID entryId) {
        GuestbookEntry entry = guestbooks.findWithUsersById(entryId)
                .orElseThrow(() -> new SocialActionException("Không tìm thấy mẩu lưu bút này."));

        boolean isProfileOwner = entry.getProfileUser().getId().equals(actorId);
        boolean isAuthor = entry.getAuthor().getId().equals(actorId);

        if (!isProfileOwner && !isAuthor) {
            throw new SocialActionException("Bạn không có quyền xóa mẩu lưu bút này.");
        }

        guestbooks.delete(entry);
    }

    @Transactional(readOnly = true)
    public List<GuestbookView> listEntries(UUID profileUserId, UUID viewerId) {
        boolean isOwner = viewerId != null && viewerId.equals(profileUserId);
        List<GuestbookEntry> entries = isOwner
                ? guestbooks.findByProfileUserIdForOwner(profileUserId)
                : guestbooks.findByProfileUserIdForVisitor(profileUserId);

        return entries.stream()
                .map(e -> toView(e, viewerId, profileUserId))
                .toList();
    }

    @Transactional(readOnly = true)
    public long countFor(UUID profileUserId, UUID viewerId) {
        boolean isOwner = viewerId != null && viewerId.equals(profileUserId);
        return isOwner
                ? guestbooks.countByProfileUserId(profileUserId)
                : guestbooks.countByProfileUserIdAndIsHiddenFalse(profileUserId);
    }

    @Transactional(readOnly = true)
    public boolean canSign(UUID viewerId, UUID profileUserId) {
        if (viewerId == null) {
            return false;
        }
        if (blocks.isBlockedEitherWay(viewerId, profileUserId)) {
            return false;
        }
        return viewerId.equals(profileUserId) || friends.areFriends(viewerId, profileUserId);
    }

    private GuestbookView toView(GuestbookEntry e, UUID viewerId, UUID profileUserId) {
        User a = e.getAuthor();
        Profile p = a.getProfile();
        AuthorView authorView = new AuthorView(
                a.getId(),
                a.getUsername(),
                p.getDisplayName(),
                p.getAvatarUrl(),
                a.primaryRole()
        );

        boolean isProfileOwner = viewerId != null && viewerId.equals(profileUserId);
        boolean isAuthor = viewerId != null && viewerId.equals(a.getId());

        return new GuestbookView(
                e.getId(),
                authorView,
                e.getContent(),
                e.getColorTheme(),
                e.getStickerIcon(),
                e.isPinned(),
                e.isHidden(),
                e.getCreatedAt(),
                isProfileOwner,               // canPin
                isProfileOwner,               // canHide
                isProfileOwner || isAuthor    // canDelete
        );
    }
}
