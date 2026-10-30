package com.aloute.service.user;

import com.aloute.repository.user.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class AvatarFrameService {
    public record Frame(String code, String name) {}
    private final JdbcTemplate jdbc;
    private final UserRepository users;

    public AvatarFrameService(JdbcTemplate jdbc, UserRepository users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<Frame> available(UUID userId) {
        return jdbc.query("SELECT f.code, f.name FROM avatar_frames f WHERE f.code NOT IN ('streak-10', 'streak-100', 'streak-500') AND (f.is_free OR EXISTS (SELECT 1 FROM user_avatar_frames u WHERE u.user_id = ? AND u.frame_code = f.code)) ORDER BY f.is_free DESC, f.code",
                (rs, row) -> new Frame(rs.getString(1), rs.getString(2)), userId);
    }

    @Transactional
    public void select(UUID userId, String code) {
        String chosen = code == null || code.isBlank() ? null : code;
        if (chosen != null && available(userId).stream().noneMatch(f -> f.code().equals(chosen))) {
            throw new IllegalArgumentException("Khung này không khả dụng.");
        }
        users.findById(userId).orElseThrow().getProfile().setAvatarFrame(chosen);
    }

    /** Cấp khung một lần; gọi lặp lại không tạo bản ghi trùng. */
    @Transactional
    public void grant(UUID userId, String code) {
        jdbc.update("INSERT INTO user_avatar_frames (user_id, frame_code) VALUES (?, ?) ON CONFLICT (user_id, frame_code) DO NOTHING", userId, code);
    }
}
