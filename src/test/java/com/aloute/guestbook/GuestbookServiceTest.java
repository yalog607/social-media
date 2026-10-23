package com.aloute.guestbook;

import com.aloute.exception.social.SocialActionException;
import com.aloute.model.user.Profile;
import com.aloute.model.user.User;
import com.aloute.repository.user.UserRepository;
import com.aloute.service.notification.NotificationService;
import com.aloute.service.social.BlockService;
import com.aloute.service.social.FriendService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GuestbookServiceTest {

    @Mock
    private GuestbookRepository guestbooks;

    @Mock
    private UserRepository users;

    @Mock
    private FriendService friends;

    @Mock
    private BlockService blocks;

    @Mock
    private NotificationService notifications;

    @InjectMocks
    private GuestbookService service;

    private User userA;
    private User userB;
    private UUID idA;
    private UUID idB;

    @BeforeEach
    void setUp() {
        idA = UUID.randomUUID();
        idB = UUID.randomUUID();

        userA = new User();
        userA.setId(idA);
        userA.setUsername("userA");
        Profile profileA = new Profile();
        profileA.setDisplayName("User A");
        userA.setProfile(profileA);

        userB = new User();
        userB.setId(idB);
        userB.setUsername("userB");
        Profile profileB = new Profile();
        profileB.setDisplayName("User B");
        userB.setProfile(profileB);
    }

    @Test
    void addEntry_Success_WhenFriends() {
        when(users.findById(idA)).thenReturn(Optional.of(userA));
        when(users.findByUsername("userB")).thenReturn(Optional.of(userB));
        when(blocks.isBlockedEitherWay(idA, idB)).thenReturn(false);
        when(friends.areFriends(idA, idB)).thenReturn(true);

        GuestbookForm form = new GuestbookForm();
        form.setContent("Chúc bạn thi tốt nhé!");
        form.setColorTheme("pink");
        form.setStickerIcon("🧋");

        GuestbookView view = service.addEntry(idA, "userB", form);

        assertThat(view).isNotNull();
        assertThat(view.content()).isEqualTo("Chúc bạn thi tốt nhé!");
        assertThat(view.colorTheme()).isEqualTo("pink");
        assertThat(view.stickerIcon()).isEqualTo("🧋");

        verify(guestbooks).save(any(GuestbookEntry.class));
        verify(notifications).guestbookEntryAdded(idA, idB);
    }

    @Test
    void addEntry_Success_WhenSelf() {
        when(users.findById(idA)).thenReturn(Optional.of(userA));
        when(users.findByUsername("userA")).thenReturn(Optional.of(userA));
        when(blocks.isBlockedEitherWay(idA, idA)).thenReturn(false);

        GuestbookForm form = new GuestbookForm();
        form.setContent("Chào mừng các bạn ghé thăm lưu bút của mình!");
        form.setColorTheme("lemon");
        form.setStickerIcon("🌸");

        GuestbookView view = service.addEntry(idA, "userA", form);

        assertThat(view).isNotNull();
        assertThat(view.content()).isEqualTo("Chào mừng các bạn ghé thăm lưu bút của mình!");
        verify(guestbooks).save(any(GuestbookEntry.class));
        verifyNoInteractions(notifications);
    }

    @Test
    void addEntry_Fails_WhenNotFriends() {
        when(users.findById(idA)).thenReturn(Optional.of(userA));
        when(users.findByUsername("userB")).thenReturn(Optional.of(userB));
        when(blocks.isBlockedEitherWay(idA, idB)).thenReturn(false);
        when(friends.areFriends(idA, idB)).thenReturn(false);

        GuestbookForm form = new GuestbookForm();
        form.setContent("Xin chào");

        assertThatThrownBy(() -> service.addEntry(idA, "userB", form))
                .isInstanceOf(SocialActionException.class)
                .hasMessageContaining("Chỉ bạn bè mới có thể dán mẩu lưu bút nhé");

        verify(guestbooks, never()).save(any());
        verifyNoInteractions(notifications);
    }

    @Test
    void addEntry_Fails_WhenBlocked() {
        when(users.findById(idA)).thenReturn(Optional.of(userA));
        when(users.findByUsername("userB")).thenReturn(Optional.of(userB));
        when(blocks.isBlockedEitherWay(idA, idB)).thenReturn(true);

        GuestbookForm form = new GuestbookForm();
        form.setContent("Chào");

        assertThatThrownBy(() -> service.addEntry(idA, "userB", form))
                .isInstanceOf(SocialActionException.class)
                .hasMessageContaining("Bạn không thể dán lưu bút cho người này");

        verify(guestbooks, never()).save(any());
    }

    @Test
    void togglePin_Success_WhenOwner() {
        UUID entryId = UUID.randomUUID();
        GuestbookEntry entry = new GuestbookEntry(userA, userB, "Lời chúc", "lemon", "🌸");
        entry.setPinned(false);

        when(guestbooks.findWithUsersById(entryId)).thenReturn(Optional.of(entry));

        service.togglePin(idA, entryId);
        assertThat(entry.isPinned()).isTrue();

        service.togglePin(idA, entryId);
        assertThat(entry.isPinned()).isFalse();
    }

    @Test
    void togglePin_Fails_WhenNotOwner() {
        UUID entryId = UUID.randomUUID();
        GuestbookEntry entry = new GuestbookEntry(userA, userB, "Lời chúc", "lemon", "🌸");

        when(guestbooks.findWithUsersById(entryId)).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> service.togglePin(idB, entryId))
                .isInstanceOf(SocialActionException.class)
                .hasMessageContaining("Chỉ chủ trang cá nhân mới có quyền ghim");
    }

    @Test
    void delete_Success_WhenAuthorOrOwner() {
        UUID entryId = UUID.randomUUID();
        GuestbookEntry entry = new GuestbookEntry(userA, userB, "Lời chúc", "lemon", "🌸");

        when(guestbooks.findWithUsersById(entryId)).thenReturn(Optional.of(entry));

        // Author deletes
        service.delete(idB, entryId);
        verify(guestbooks).delete(entry);

        // Owner deletes
        service.delete(idA, entryId);
        verify(guestbooks, times(2)).delete(entry);
    }

    @Test
    void delete_Fails_WhenThirdParty() {
        UUID entryId = UUID.randomUUID();
        GuestbookEntry entry = new GuestbookEntry(userA, userB, "Lời chúc", "lemon", "🌸");

        when(guestbooks.findWithUsersById(entryId)).thenReturn(Optional.of(entry));

        UUID strangerId = UUID.randomUUID();
        assertThatThrownBy(() -> service.delete(strangerId, entryId))
                .isInstanceOf(SocialActionException.class)
                .hasMessageContaining("Bạn không có quyền xóa");

        verify(guestbooks, never()).delete(any());
    }
}
