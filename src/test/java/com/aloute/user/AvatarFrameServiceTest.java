package com.aloute.user;

import com.aloute.model.user.Profile;
import com.aloute.model.user.User;
import com.aloute.repository.user.UserRepository;
import com.aloute.service.user.AvatarFrameService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AvatarFrameServiceTest {
    @Test
    void selectionChecksAvailabilityAndCanBeCleared() {
        UserRepository users = mock(UserRepository.class);
        AvatarFrameService service = spy(new AvatarFrameService(mock(JdbcTemplate.class), users));
        UUID id = UUID.randomUUID();
        User user = new User();
        Profile profile = new Profile();
        user.attachProfile(profile);
        when(users.findById(id)).thenReturn(Optional.of(user));
        doReturn(List.of(new AvatarFrameService.Frame("pink", "Hồng"))).when(service).available(id);
        service.select(id, "pink");
        assertThat(profile.getAvatarFrame()).isEqualTo("pink");
        assertThatThrownBy(() -> service.select(id, "event-star")).isInstanceOf(IllegalArgumentException.class);
        assertThat(profile.getAvatarFrame()).isEqualTo("pink");
        service.select(id, "");
        assertThat(profile.getAvatarFrame()).isNull();
    }
}
