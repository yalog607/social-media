package com.aloute.service.sticker;

import com.aloute.dto.sticker.StickerPackView;
import com.aloute.model.sticker.Sticker;
import com.aloute.model.sticker.StickerPack;
import com.aloute.model.sticker.UserStickerPack;
import com.aloute.model.user.User;
import com.aloute.repository.sticker.StickerPackRepository;
import com.aloute.repository.sticker.StickerRepository;
import com.aloute.repository.sticker.UserStickerPackRepository;
import com.aloute.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StickerServiceTest {

    @Mock
    private StickerPackRepository packRepository;

    @Mock
    private StickerRepository stickerRepository;

    @Mock
    private UserStickerPackRepository userPackRepository;

    @Mock
    private UserRepository userRepository;

    private StickerService service;

    @BeforeEach
    void setUp() {
        service = new StickerService(packRepository, stickerRepository, userPackRepository, userRepository);
    }

    @Test
    void ensureDefaultPacksInstalled_whenNoExistingPacks_installsDefaults() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        StickerPack defaultPack = new StickerPack();
        defaultPack.setId("meo-cute");
        defaultPack.setDefault(true);

        when(userPackRepository.findByUserIdOrderByInstalledAtAsc(userId)).thenReturn(List.of());
        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(packRepository.findByIsDefaultTrueOrderBySortOrderAsc()).thenReturn(List.of(defaultPack));

        service.ensureDefaultPacksInstalled(userId);

        verify(userPackRepository).save(any(UserStickerPack.class));
    }

    @Test
    void getMyPacks_whenUserHasPacks_returnsInstalledPacks() {
        UUID userId = UUID.randomUUID();
        StickerPack pack = new StickerPack();
        pack.setId("meo-cute");
        pack.setName("Mèo Cute");

        UserStickerPack usp = new UserStickerPack();
        usp.setPack(pack);

        when(userPackRepository.findByUserIdOrderByInstalledAtAsc(userId)).thenReturn(List.of(usp));
        when(stickerRepository.findByPackIdOrderBySortOrderAsc("meo-cute")).thenReturn(List.of());

        List<StickerPackView> result = service.getMyPacks(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("meo-cute");
        assertThat(result.get(0).installed()).isTrue();
    }

    @Test
    void installPack_whenNew_savesUserStickerPack() {
        UUID userId = UUID.randomUUID();
        String packId = "gau-hoc-gioi";
        User user = new User();
        StickerPack pack = new StickerPack();
        pack.setId(packId);

        when(userPackRepository.existsByUserIdAndPackId(userId, packId)).thenReturn(false);
        when(packRepository.findById(packId)).thenReturn(Optional.of(pack));
        when(userRepository.getReferenceById(userId)).thenReturn(user);

        boolean ok = service.installPack(userId, packId);

        assertThat(ok).isTrue();
        verify(userPackRepository).save(any(UserStickerPack.class));
    }

    @Test
    void uninstallPack_whenExists_deletesUserStickerPack() {
        UUID userId = UUID.randomUUID();
        String packId = "gau-hoc-gioi";

        when(userPackRepository.existsByUserIdAndPackId(userId, packId)).thenReturn(true);

        boolean ok = service.uninstallPack(userId, packId);

        assertThat(ok).isTrue();
        verify(userPackRepository).deleteByUserIdAndPackId(userId, packId);
    }
}

