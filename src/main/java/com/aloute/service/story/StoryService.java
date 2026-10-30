package com.aloute.service.story;

import com.aloute.dto.chat.MessageView;
import com.aloute.dto.post.PostView.AuthorView;
import com.aloute.dto.story.StoryGroupView;
import com.aloute.dto.story.StoryItemView;
import com.aloute.dto.story.StoryTrayView;
import com.aloute.dto.story.StoryViewerItem;
import com.aloute.exception.media.InvalidMediaException;
import com.aloute.model.chat.Conversation;
import com.aloute.model.common.RateAction;
import com.aloute.model.media.MediaKind;
import com.aloute.model.mood.DailyMood;
import com.aloute.model.story.Story;
import com.aloute.model.story.StoryMediaType;
import com.aloute.model.story.StoryView;
import com.aloute.model.user.Role;
import com.aloute.model.user.User;
import com.aloute.model.user.Visibility;
import com.aloute.repository.mood.DailyMoodRepository;
import com.aloute.repository.story.StoryRepository;
import com.aloute.repository.story.StoryViewRepository;
import com.aloute.repository.user.UserRepository;
import com.aloute.service.chat.ChatService;
import com.aloute.service.chat.MessageService;
import com.aloute.service.common.RateLimiter;
import com.aloute.service.common.TimeAgo;
import com.aloute.service.media.MediaService;
import com.aloute.service.social.FriendService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class StoryService {

    private final StoryRepository stories;
    private final StoryViewRepository storyViews;
    private final UserRepository users;
    private final FriendService friends;
    private final MediaService media;
    private final ChatService chats;
    private final MessageService messages;
    private final SimpMessagingTemplate broker;
    private final Clock clock;
    private final TimeAgo timeAgo;
    private final RateLimiter rateLimiter;
    private final DailyMoodRepository dailyMoods;

    @Autowired
    public StoryService(StoryRepository stories,
            StoryViewRepository storyViews,
            UserRepository users,
            FriendService friends,
            MediaService media,
            ChatService chats,
            MessageService messages,
            SimpMessagingTemplate broker,
            Clock clock,
            TimeAgo timeAgo,
            RateLimiter rateLimiter,
            DailyMoodRepository dailyMoods) {
        this.stories = stories;
        this.storyViews = storyViews;
        this.users = users;
        this.friends = friends;
        this.media = media;
        this.chats = chats;
        this.messages = messages;
        this.broker = broker;
        this.clock = clock;
        this.timeAgo = timeAgo;
        this.rateLimiter = rateLimiter;
        this.dailyMoods = dailyMoods;
    }

    public StoryService(StoryRepository stories,
            StoryViewRepository storyViews,
            UserRepository users,
            FriendService friends,
            MediaService media,
            ChatService chats,
            MessageService messages,
            SimpMessagingTemplate broker,
            Clock clock,
            TimeAgo timeAgo,
            RateLimiter rateLimiter) {
        this(stories, storyViews, users, friends, media, chats, messages, broker, clock, timeAgo, rateLimiter, null);
    }

    @Transactional
    public Story createStory(UUID authorId, MultipartFile mediaFile, String caption,
            String backgroundColor, Visibility visibility, String storyType) {
        return createStory(authorId, mediaFile, caption, backgroundColor, visibility, storyType, null);
    }

    public Instant calculateVibeExpiresAt(Instant now) {
        ZoneId zone = clock.getZone() != null ? clock.getZone() : ZoneId.systemDefault();
        return now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant();
    }

    @Transactional
    public Story createStory(UUID authorId, MultipartFile mediaFile, String caption,
            String backgroundColor, Visibility visibility, String storyType, String emoji) {
        rateLimiter.check(RateAction.POST, authorId);
        User author = users.findById(authorId).filter(User::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản không hợp lệ hoặc đã bị khóa."));

        boolean isVibe = "VIBE".equalsIgnoreCase(storyType);
        boolean isText = "TEXT".equalsIgnoreCase(storyType) || isVibe || (mediaFile == null || mediaFile.isEmpty());
        String cleanCaption = caption != null ? caption.trim() : "";
        if (cleanCaption.length() > 500) {
            cleanCaption = cleanCaption.substring(0, 500);
        }

        StoryMediaType mediaType;
        String mediaUrl = null;

        String bg = backgroundColor != null && !backgroundColor.isBlank() ? backgroundColor.trim() : "lavender";
        String cleanEmoji = emoji != null && !emoji.isBlank() ? emoji.trim() : null;
        Visibility chosenVisibility = visibility != null ? visibility : Visibility.FRIENDS;

        if (isVibe) {
            if (cleanCaption.isEmpty()) {
                throw new InvalidMediaException("Vui lòng nhập lời nhắn cho Vibe.");
            }
            mediaType = StoryMediaType.VIBE;
            Instant now = clock.instant();
            Instant vibeExpiresAt = calculateVibeExpiresAt(now);

            // "vibe chỉ được đăng 1 bài (hết hạn theo thời gian thực trong ngày 0h mỗi ngày
            // sẽ reset), nếu muốn đổi vibe thì chỉnh sửa cái đã đăng"
            List<Story> activeStories = stories.findByUserIdAndExpiresAtAfterOrderByCreatedAtAsc(authorId, now);
            List<Story> activeVibes = activeStories.stream()
                    .filter(s -> s.getMediaType() == StoryMediaType.VIBE || (s.getMediaType() == StoryMediaType.TEXT
                            && s.getEmoji() != null && !s.getEmoji().isBlank()))
                    .toList();

            if (!activeVibes.isEmpty()) {
                // Đã có bài Vibe trong ngày: CHỈNH SỬA cái đã đăng
                Story existingVibe = activeVibes.get(0);
                existingVibe.setMediaType(StoryMediaType.VIBE);
                existingVibe.setCaption(cleanCaption);
                existingVibe.setEmoji(cleanEmoji != null ? cleanEmoji : "🥰");
                existingVibe.setBackgroundColor(bg);
                existingVibe.setVisibility(chosenVisibility);
                existingVibe.setExpiresAt(vibeExpiresAt);
                existingVibe.setMediaUrl(null);

                // Dọn dẹp nếu có bài Vibe thừa trước đó
                for (int i = 1; i < activeVibes.size(); i++) {
                    try {
                        stories.delete(activeVibes.get(i));
                    } catch (Exception ignored) {
                    }
                }

                syncDailyMood(author, existingVibe.getEmoji(), cleanCaption, bg, vibeExpiresAt);
                return stories.save(existingVibe);
            }
        } else if (isText) {
            if (cleanCaption.isEmpty()) {
                throw new InvalidMediaException("Vui lòng nhập nội dung ghi chú cho khoảnh khắc.");
            }
            mediaType = StoryMediaType.TEXT;
        } else {
            MediaService.StoredMedia stored = media.storeStoryMedia(mediaFile);
            if (stored == null) {
                throw new InvalidMediaException("Không nhận diện được file tải lên.");
            }
            mediaType = stored.kind() == MediaKind.VIDEO ? StoryMediaType.VIDEO : StoryMediaType.IMAGE;
            mediaUrl = stored.url();
        }

        Story story = new Story();
        story.setUser(author);
        story.setMediaType(mediaType);
        story.setMediaUrl(mediaUrl);
        story.setCaption(cleanCaption);
        story.setEmoji(cleanEmoji != null ? cleanEmoji : (isVibe ? "🥰" : null));
        story.setBackgroundColor(bg);
        story.setVisibility(chosenVisibility);
        Instant expiresAt = isVibe ? calculateVibeExpiresAt(clock.instant())
                : clock.instant().plus(Duration.ofHours(24));
        story.setExpiresAt(expiresAt);

        Story saved = stories.save(story);
        if (isVibe) {
            syncDailyMood(author, saved.getEmoji(), cleanCaption, bg, saved.getExpiresAt());
        }
        return saved;
    }

    private void syncDailyMood(User user, String emoji, String text, String color, Instant expiresAt) {
        if (dailyMoods == null || user == null)
            return;
        try {
            DailyMood mood = dailyMoods.findByUserId(user.getId()).orElseGet(() -> {
                DailyMood m = new DailyMood();
                m.setUser(user);
                return m;
            });
            mood.setEmoji(emoji != null ? emoji : "🥰");
            mood.setMoodText(text);
            mood.setColorTheme(color != null ? color : "lemon");
            mood.setExpiresAt(expiresAt);
            dailyMoods.save(mood);
        } catch (Exception ignored) {
        }
    }

    @Transactional
    public void deleteActiveVibeStory(UUID authorId) {
        Instant now = clock.instant();
        List<Story> activeStories = stories.findByUserIdAndExpiresAtAfterOrderByCreatedAtAsc(authorId, now);
        for (Story s : activeStories) {
            if (s.getMediaType() == StoryMediaType.VIBE
                    || (s.getMediaType() == StoryMediaType.TEXT && s.getEmoji() != null && !s.getEmoji().isBlank())) {
                stories.delete(s);
            }
        }
    }

    @Transactional(readOnly = true)
    public StoryTrayView getStoryTray(UUID viewerId) {
        Instant now = clock.instant();
        User me = users.findById(viewerId).orElseThrow();

        // 1. Khoảnh khắc & Vibe của chính mình
        DailyMood myMood = dailyMoods != null ? dailyMoods.findActiveByUserId(viewerId, now).orElse(null) : null;
        String myVibeEmoji = myMood != null ? myMood.getEmoji() : null;
        String myVibeText = myMood != null ? myMood.getMoodText() : null;
        String myVibeColor = myMood != null ? myMood.getColorTheme() : null;

        List<Story> myActiveStories = stories.findByUserIdAndExpiresAtAfterOrderByCreatedAtAsc(viewerId, now);
        List<StoryItemView> myItemViews = new ArrayList<>(myActiveStories.stream()
                .map(s -> toItemView(s, viewerId, true, false))
                .toList());
        sortStoriesWithVibeFirst(myItemViews);
        AuthorView myAuthorView = toAuthorView(me);
        Instant myLatest = myActiveStories.isEmpty() ? null
                : myActiveStories.get(myActiveStories.size() - 1).getCreatedAt();
        StoryGroupView myGroup = new StoryGroupView(myAuthorView, myItemViews, false, myLatest, true, myVibeEmoji,
                myVibeText, myVibeColor);

        // 2. Danh sách bạn bè
        List<AuthorView> friendList = friends.friendsOf(viewerId);
        if (friendList.isEmpty()) {
            return new StoryTrayView(myGroup, List.of());
        }

        List<UUID> friendIds = friendList.stream().map(AuthorView::id).toList();

        // Lấy active vibe của bạn bè
        Map<UUID, DailyMood> friendMoodMap = new HashMap<>();
        if (dailyMoods != null) {
            List<DailyMood> activeFriendMoods = dailyMoods.findActiveByUserIds(friendIds, now);
            for (DailyMood m : activeFriendMoods) {
                if (m.getUser() != null) {
                    friendMoodMap.put(m.getUser().getId(), m);
                }
            }
        }

        List<Story> allFriendStories = stories.findByUserIdInAndExpiresAtAfterOrderByCreatedAtAsc(friendIds, now);
        if (allFriendStories.isEmpty()) {
            return new StoryTrayView(myGroup, List.of());
        }

        // Lấy danh sách ID story bạn bè đã xem
        List<UUID> storyIds = allFriendStories.stream().map(Story::getId).toList();
        Set<UUID> viewedStoryIds = storyViews.findViewedStoryIds(viewerId, storyIds);

        // Gom nhóm theo author ID
        Map<UUID, List<Story>> storiesByAuthor = new HashMap<>();
        for (Story s : allFriendStories) {
            // Lọc story theo quyền xem: Bạn bè hoặc Công khai (bỏ qua PRIVATE của người
            // khác)
            if (s.getVisibility() != Visibility.PRIVATE) {
                storiesByAuthor.computeIfAbsent(s.getUser().getId(), k -> new ArrayList<>()).add(s);
            }
        }

        Map<UUID, AuthorView> friendMap = new HashMap<>();
        for (AuthorView f : friendList) {
            friendMap.put(f.id(), f);
        }

        List<StoryGroupView> friendGroups = new ArrayList<>();
        for (Map.Entry<UUID, List<Story>> entry : storiesByAuthor.entrySet()) {
            UUID authorId = entry.getKey();
            List<Story> authorStories = entry.getValue();
            AuthorView authorView = friendMap.get(authorId);
            if (authorView == null || authorStories.isEmpty()) {
                continue;
            }

            DailyMood authorMood = friendMoodMap.get(authorId);
            String vibeEmoji = authorMood != null ? authorMood.getEmoji() : null;
            String vibeText = authorMood != null ? authorMood.getMoodText() : null;
            String vibeColor = authorMood != null ? authorMood.getColorTheme() : null;

            boolean allSeen = authorStories.stream().allMatch(s -> viewedStoryIds.contains(s.getId()));
            List<StoryItemView> items = new ArrayList<>(authorStories.stream()
                    .map(s -> toItemView(s, viewerId, false, viewedStoryIds.contains(s.getId())))
                    .toList());
            sortStoriesWithVibeFirst(items);
            Instant latest = authorStories.get(authorStories.size() - 1).getCreatedAt();

            friendGroups
                    .add(new StoryGroupView(authorView, items, allSeen, latest, false, vibeEmoji, vibeText, vibeColor));
        }

        // Sắp xếp: Story chưa xem lên đầu, sau đó theo thời gian đăng mới nhất
        friendGroups.sort((a, b) -> {
            if (!a.allSeen() && b.allSeen())
                return -1;
            if (a.allSeen() && !b.allSeen())
                return 1;
            Instant tA = a.latestCreatedAt() != null ? a.latestCreatedAt() : Instant.EPOCH;
            Instant tB = b.latestCreatedAt() != null ? b.latestCreatedAt() : Instant.EPOCH;
            return tB.compareTo(tA);
        });

        return new StoryTrayView(myGroup, friendGroups);
    }

    @Transactional(readOnly = true)
    public List<StoryItemView> getStoriesForUser(UUID authorId, UUID viewerId) {
        Instant now = clock.instant();
        List<Story> userStories = stories.findByUserIdAndExpiresAtAfterOrderByCreatedAtAsc(authorId, now);
        if (userStories.isEmpty()) {
            return List.of();
        }

        boolean isMe = authorId.equals(viewerId);
        List<UUID> storyIds = userStories.stream().map(Story::getId).toList();
        Set<UUID> viewedStoryIds = storyViews.findViewedStoryIds(viewerId, storyIds);

        List<StoryItemView> items = new ArrayList<>(userStories.stream()
                .filter(s -> isMe || s.getVisibility() != Visibility.PRIVATE)
                .map(s -> toItemView(s, viewerId, isMe, viewedStoryIds.contains(s.getId())))
                .toList());

        sortStoriesWithVibeFirst(items);
        return items;
    }

    private void sortStoriesWithVibeFirst(List<StoryItemView> list) {
        list.sort((a, b) -> {
            boolean aVibe = a.isVibe();
            boolean bVibe = b.isVibe();
            if (aVibe && !bVibe)
                return -1;
            if (!aVibe && bVibe)
                return 1;
            return a.createdAt().compareTo(b.createdAt());
        });
    }

    @Transactional
    public void recordView(UUID storyId, UUID viewerId) {
        Story story = stories.findById(storyId).orElse(null);
        if (story == null || story.getUser().getId().equals(viewerId)) {
            return;
        }
        if (!storyViews.existsByStoryIdAndViewerId(storyId, viewerId)) {
            User viewer = users.getReferenceById(viewerId);
            try {
                storyViews.save(new StoryView(story, viewer));
            } catch (Exception ignored) {
                // Ignore concurrent unique constraint violation
            }
        }
    }

    @Transactional(readOnly = true)
    public List<StoryViewerItem> getViewers(UUID storyId, UUID authorId) {
        Story story = stories.findById(storyId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khoảnh khắc."));
        if (!story.getUser().getId().equals(authorId)) {
            throw new IllegalArgumentException("Chỉ tác giả mới có quyền xem danh sách người xem.");
        }
        List<StoryView> views = storyViews.findByStoryIdOrderByViewedAtDesc(storyId);
        return views.stream().map(v -> {
            User u = v.getViewer();
            return new StoryViewerItem(
                    u.getId(),
                    u.getProfile().getDisplayName(),
                    u.getUsername(),
                    u.getProfile().getAvatarUrl(),
                    v.getViewedAt(),
                    timeAgo.format(v.getViewedAt()),
                    v.getReaction(), u.getProfile().getAvatarFrame());
        }).toList();
    }

    @Transactional
    public void recordReaction(UUID storyId, UUID viewerId, String emoji) {
        if (emoji == null || emoji.isBlank())
            return;
        Story story = stories.findById(storyId).orElse(null);
        if (story == null)
            return;

        StoryView view = storyViews.findByStoryIdAndViewerId(storyId, viewerId).orElse(null);
        if (view == null) {
            User viewer = users.getReferenceById(viewerId);
            view = new StoryView(story, viewer, emoji.trim());
        } else {
            String existing = view.getReaction();
            if (existing == null || existing.isBlank()) {
                view.setReaction(emoji.trim());
            } else {
                // Mỗi loại cảm xúc chỉ giữ 1 icon tượng trưng duy nhất
                Set<String> set = new LinkedHashSet<>(Arrays.asList(existing.split("\\s+")));
                set.add(emoji.trim());
                view.setReaction(String.join(" ", set));
            }
        }
        try {
            storyViews.save(view);
        } catch (Exception ignored) {
        }

        try {
            broker.convertAndSend("/topic/stories/" + storyId + "/reactions", Map.of(
                    "storyId", storyId.toString(),
                    "viewerId", viewerId.toString(),
                    "emoji", emoji.trim()));
        } catch (Exception ignored) {
        }
    }

    @Transactional
    public void deleteStory(UUID storyId, UUID actorId) {
        Story story = stories.findById(storyId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khoảnh khắc."));
        User actor = users.findById(actorId).orElseThrow();
        boolean isOwner = story.getUser().getId().equals(actorId);
        boolean isStaff = actor.hasRole(Role.ADMIN) || actor.hasRole(Role.MANAGER);
        if (!isOwner && !isStaff) {
            throw new IllegalArgumentException("Bạn không có quyền xóa khoảnh khắc này.");
        }
        if (story.getMediaUrl() != null) {
            media.deleteMedia(story.getMediaUrl());
        }
        boolean isVibe = story.getMediaType() == StoryMediaType.VIBE;
        if (isVibe && dailyMoods != null) {
            try {
                dailyMoods.findByUserId(story.getUser().getId()).ifPresent(dailyMoods::delete);
            } catch (Exception ignored) {
            }
        }
        stories.delete(story);
    }

    @Transactional
    public MessageView replyToStory(UUID storyId, UUID senderId, String messageText) {
        Story story = stories.findById(storyId)
                .orElseThrow(() -> new IllegalArgumentException("Khoảnh khắc không tồn tại hoặc đã hết hạn."));
        UUID authorId = story.getUser().getId();
        if (authorId.equals(senderId)) {
            throw new IllegalArgumentException("Không thể tự phản hồi khoảnh khắc của chính mình.");
        }
        String cleanText = messageText != null ? messageText.trim() : "";
        if (cleanText.isEmpty()) {
            throw new IllegalArgumentException("Nội dung tin nhắn phản hồi không được để trống.");
        }

        Conversation conv = chats.startDirect(senderId, authorId);
        String formatted = "💬 [Khoảnh khắc](/stories/" + storyId + "): " + cleanText;
        MessageView view = messages.send(senderId, conv.getId(), formatted, null);
        broker.convertAndSend("/topic/conversations/" + conv.getId(), view);
        return view;
    }

    @Transactional(readOnly = true)
    public StoryItemView getStoryById(UUID storyId, UUID viewerId) {
        Story story = stories.findById(storyId)
                .orElseThrow(() -> new IllegalArgumentException("Khoảnh khắc không tồn tại hoặc đã hết hạn."));
        if (story.isExpired(clock.instant())) {
            throw new IllegalArgumentException("Khoảnh khắc này đã biến mất sau 24h.");
        }
        boolean isOwner = viewerId != null && story.getUser().getId().equals(viewerId);
        boolean viewed = viewerId != null && storyViews.existsByStoryIdAndViewerId(story.getId(), viewerId);
        return toItemView(story, viewerId, isOwner, viewed);
    }

    private StoryItemView toItemView(Story s, UUID viewerId, boolean isOwner, boolean viewed) {
        long viewCount = isOwner ? storyViews.countByStoryId(s.getId()) : 0;
        User author = s.getUser();
        List<String> rawReactions = storyViews.findReactionsByStoryId(s.getId());
        List<String> reactions = new ArrayList<>();
        if (rawReactions != null) {
            for (String r : rawReactions) {
                if (r != null && !r.isBlank()) {
                    for (String part : r.split("\\s+")) {
                        if (!part.isBlank())
                            reactions.add(part);
                    }
                }
            }
        }
        return new StoryItemView(
                s.getId(),
                author.getId(),
                author.getProfile().getDisplayName(),
                author.getUsername(),
                author.getProfile().getAvatarUrl(),
                s.getMediaType(),
                s.getMediaUrl(),
                s.getCaption(),
                s.getEmoji(),
                s.getBackgroundColor(),
                s.getCreatedAt(),
                timeAgo.format(s.getCreatedAt()),
                viewed,
                viewCount,
                isOwner,
                reactions, author.getProfile().getAvatarFrame());
    }

    private AuthorView toAuthorView(User u) {
        return new AuthorView(
                u.getId(),
                u.getUsername(),
                u.getProfile().getDisplayName(),
                u.getProfile().getAvatarUrl(),
                u.primaryRole(), u.getProfile().getAvatarFrame());
    }
}
