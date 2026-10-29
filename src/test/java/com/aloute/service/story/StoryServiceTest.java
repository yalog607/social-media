package com.aloute.service.story;

import com.aloute.dto.chat.MessageView;
import com.aloute.dto.post.PostView.AuthorView;
import com.aloute.dto.story.StoryGroupView;
import com.aloute.dto.story.StoryItemView;
import com.aloute.dto.story.StoryTrayView;
import com.aloute.exception.media.InvalidMediaException;
import com.aloute.model.chat.Conversation;
import com.aloute.model.media.MediaKind;
import com.aloute.model.story.Story;
import com.aloute.model.story.StoryMediaType;
import com.aloute.model.story.StoryView;
import com.aloute.model.user.Profile;
import com.aloute.model.user.Role;
import com.aloute.model.user.User;
import com.aloute.model.user.Visibility;
import com.aloute.repository.story.StoryRepository;
import com.aloute.repository.story.StoryViewRepository;
import com.aloute.repository.user.UserRepository;
import com.aloute.service.chat.ChatService;
import com.aloute.service.chat.MessageService;
import com.aloute.service.common.RateLimiter;
import com.aloute.service.common.TimeAgo;
import com.aloute.service.media.MediaService;
import com.aloute.service.social.FriendService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoryServiceTest {

    @Mock
    private StoryRepository stories;

    @Mock
    private StoryViewRepository storyViews;

    @Mock
    private UserRepository users;

    @Mock
    private FriendService friends;

    @Mock
    private MediaService media;

    @Mock
    private ChatService chats;

    @Mock
    private MessageService messages;

    @Mock
    private SimpMessagingTemplate broker;

    @Mock
    private TimeAgo timeAgo;

    @Mock
    private RateLimiter rateLimiter;

    private Clock clock;
    private StoryService storyService;

    private final Instant now = Instant.parse("2026-10-29T10:00:00Z");
    private UUID myId;
    private User me;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(now, ZoneId.of("UTC"));
        storyService = new StoryService(
                stories, storyViews, users, friends, media, chats, messages, broker, clock, timeAgo, rateLimiter
        );

        myId = UUID.randomUUID();
        me = new User();
        me.setId(myId);
        me.setUsername("mochi");
        me.setRoles(Set.of(Role.USER));

        Profile myProfile = new Profile();
        myProfile.setDisplayName("Mochi");
        myProfile.setAvatarUrl("/uploads/mochi.jpg");
        me.setProfile(myProfile);
    }

    @Test
    void createStory_withImage_success() {
        MockMultipartFile imageFile = new MockMultipartFile("mediaFile", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(users.findById(myId)).thenReturn(Optional.of(me));
        when(media.storeStoryMedia(imageFile)).thenReturn(new MediaService.StoredMedia(MediaKind.IMAGE, "/uploads/stories/p1.jpg", "image/jpeg", 1024));
        when(stories.save(any(Story.class))).thenAnswer(inv -> inv.getArgument(0));

        Story created = storyService.createStory(myId, imageFile, "Vui quá xá!", "lemon", Visibility.FRIENDS, "MEDIA");

        assertThat(created).isNotNull();
        assertThat(created.getUser()).isEqualTo(me);
        assertThat(created.getMediaType()).isEqualTo(StoryMediaType.IMAGE);
        assertThat(created.getMediaUrl()).isEqualTo("/uploads/stories/p1.jpg");
        assertThat(created.getCaption()).isEqualTo("Vui quá xá!");
        assertThat(created.getBackgroundColor()).isEqualTo("lemon");
        assertThat(created.getExpiresAt()).isEqualTo(now.plus(24, ChronoUnit.HOURS));
    }

    @Test
    void createStory_withText_success() {
        when(users.findById(myId)).thenReturn(Optional.of(me));
        when(stories.save(any(Story.class))).thenAnswer(inv -> inv.getArgument(0));

        Story created = storyService.createStory(myId, null, "Một ngày mùa thu dịu dàng ✨", "rose", Visibility.FRIENDS, "TEXT");

        assertThat(created).isNotNull();
        assertThat(created.getUser()).isEqualTo(me);
        assertThat(created.getMediaType()).isEqualTo(StoryMediaType.TEXT);
        assertThat(created.getMediaUrl()).isNull();
        assertThat(created.getCaption()).isEqualTo("Một ngày mùa thu dịu dàng ✨");
        assertThat(created.getBackgroundColor()).isEqualTo("rose");
        assertThat(created.getExpiresAt()).isEqualTo(now.plus(24, ChronoUnit.HOURS));
    }

    @Test
    void createStory_withTextAndEmoji_success() {
        when(users.findById(myId)).thenReturn(Optional.of(me));
        when(stories.save(any(Story.class))).thenAnswer(inv -> inv.getArgument(0));

        Story created = storyService.createStory(myId, null, "Hôm nay vui quá!", "lemon", Visibility.FRIENDS, "TEXT", "🥳");

        assertThat(created).isNotNull();
        assertThat(created.getMediaType()).isEqualTo(StoryMediaType.TEXT);
        assertThat(created.getCaption()).isEqualTo("Hôm nay vui quá!");
        assertThat(created.getEmoji()).isEqualTo("🥳");
        assertThat(created.getBackgroundColor()).isEqualTo("lemon");
    }

    @Test
    void createStory_textEmpty_throwsException() {
        when(users.findById(myId)).thenReturn(Optional.of(me));

        assertThatThrownBy(() -> storyService.createStory(myId, null, "   ", "rose", Visibility.FRIENDS, "TEXT"))
                .isInstanceOf(InvalidMediaException.class)
                .hasMessageContaining("nội dung ghi chú");
    }

    @Test
    void getStoryTray_groupsStoriesByAuthor_andCalculatesAllSeen() {
        when(users.findById(myId)).thenReturn(Optional.of(me));
        when(stories.findByUserIdAndExpiresAtAfterOrderByCreatedAtAsc(myId, now)).thenReturn(List.of());

        UUID friend1Id = UUID.randomUUID();
        AuthorView friend1 = new AuthorView(friend1Id, "vyvy", "Vy Vy", "/avatar1.jpg", Role.USER);
        UUID friend2Id = UUID.randomUUID();
        AuthorView friend2 = new AuthorView(friend2Id, "bao", "Gia Bảo", "/avatar2.jpg", Role.USER);
        when(friends.friendsOf(myId)).thenReturn(List.of(friend1, friend2));

        User u1 = new User();
        u1.setId(friend1Id);
        u1.setUsername("vyvy");
        Profile p1 = new Profile();
        p1.setDisplayName("Vy Vy");
        u1.setProfile(p1);

        User u2 = new User();
        u2.setId(friend2Id);
        u2.setUsername("bao");
        Profile p2 = new Profile();
        p2.setDisplayName("Gia Bảo");
        u2.setProfile(p2);

        Story s1 = new Story();
        s1.setId(UUID.randomUUID());
        s1.setUser(u1);
        s1.setMediaType(StoryMediaType.IMAGE);
        s1.setCreatedAt(now.minus(2, ChronoUnit.HOURS));

        Story s2 = new Story();
        s2.setId(UUID.randomUUID());
        s2.setUser(u2);
        s2.setMediaType(StoryMediaType.TEXT);
        s2.setCreatedAt(now.minus(1, ChronoUnit.HOURS));

        when(stories.findByUserIdInAndExpiresAtAfterOrderByCreatedAtAsc(eq(List.of(friend1Id, friend2Id)), eq(now)))
                .thenReturn(List.of(s1, s2));

        when(storyViews.findViewedStoryIds(eq(myId), anyCollection())).thenReturn(Set.of(s1.getId()));

        StoryTrayView tray = storyService.getStoryTray(myId);

        assertThat(tray).isNotNull();
        assertThat(tray.friendGroups()).hasSize(2);

        StoryGroupView firstGroup = tray.friendGroups().get(0);
        assertThat(firstGroup.author().id()).isEqualTo(friend2Id);
        assertThat(firstGroup.allSeen()).isFalse();

        StoryGroupView secondGroup = tray.friendGroups().get(1);
        assertThat(secondGroup.author().id()).isEqualTo(friend1Id);
        assertThat(secondGroup.allSeen()).isTrue();
    }

    @Test
    void recordView_whenNotAuthor_recordsStoryView() {
        UUID otherId = UUID.randomUUID();
        User other = new User();
        other.setId(otherId);

        Story story = new Story();
        story.setId(UUID.randomUUID());
        story.setUser(other);

        when(stories.findById(story.getId())).thenReturn(Optional.of(story));
        when(storyViews.existsByStoryIdAndViewerId(story.getId(), myId)).thenReturn(false);
        when(users.getReferenceById(myId)).thenReturn(me);

        storyService.recordView(story.getId(), myId);

        verify(storyViews, times(1)).save(any(StoryView.class));
    }

    @Test
    void recordView_whenAuthor_doesNotRecordView() {
        Story story = new Story();
        story.setId(UUID.randomUUID());
        story.setUser(me);

        when(stories.findById(story.getId())).thenReturn(Optional.of(story));

        storyService.recordView(story.getId(), myId);

        verify(storyViews, never()).save(any());
    }

    @Test
    void replyToStory_startsDirectChat_andSendsMessage() {
        UUID authorId = UUID.randomUUID();
        User author = new User();
        author.setId(authorId);

        Story story = new Story();
        story.setId(UUID.randomUUID());
        story.setUser(author);
        when(stories.findById(story.getId())).thenReturn(Optional.of(story));

        Conversation directConv = new Conversation();
        directConv.setId(UUID.randomUUID());
        when(chats.startDirect(myId, authorId)).thenReturn(directConv);

        MessageView expectedView = new MessageView(
                UUID.randomUUID(), directConv.getId(),
                new AuthorView(myId, "mochi", "Mochi", null, Role.USER),
                "msg", null, now, false, false
        );

        when(messages.send(eq(myId), eq(directConv.getId()), any(), any())).thenReturn(expectedView);

        MessageView result = storyService.replyToStory(story.getId(), myId, "Trông xinh thế!");

        assertThat(result).isNotNull();
        assertThat(result.conversationId()).isEqualTo(directConv.getId());
        verify(broker).convertAndSend(eq("/topic/conversations/" + directConv.getId()), eq(expectedView));
    }

    @Test
    void deleteStory_owner_success() {
        Story story = new Story();
        story.setId(UUID.randomUUID());
        story.setUser(me);
        story.setMediaUrl("/uploads/stories/test.jpg");

        when(stories.findById(story.getId())).thenReturn(Optional.of(story));
        when(users.findById(myId)).thenReturn(Optional.of(me));

        storyService.deleteStory(story.getId(), myId);

        verify(media).deleteMedia("/uploads/stories/test.jpg");
        verify(stories).delete(story);
    }

    @Test
    void deleteStory_nonOwner_throwsException() {
        UUID otherId = UUID.randomUUID();
        User other = new User();
        other.setId(otherId);

        Story story = new Story();
        story.setId(UUID.randomUUID());
        story.setUser(other);

        when(stories.findById(story.getId())).thenReturn(Optional.of(story));
        when(users.findById(myId)).thenReturn(Optional.of(me));

        assertThatThrownBy(() -> storyService.deleteStory(story.getId(), myId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bạn không có quyền xóa khoảnh khắc này.");

        verify(stories, never()).delete(any());
    }
}
