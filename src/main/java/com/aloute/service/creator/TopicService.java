package com.aloute.service.creator;

import com.aloute.dto.category.CategoryView;
import com.aloute.exception.category.InvalidCategoryException;
import com.aloute.exception.creator.InvalidTopicException;
import com.aloute.model.user.Role;
import com.aloute.model.user.User;
import com.aloute.repository.user.UserRepository;
import com.aloute.service.category.CategoryService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Chủ đề của Creator trên trang hồ sơ: tối đa {@value #MAX_TOPICS} danh mục (bảng {@code categories} có sẵn).
 * Mỗi lần chỉnh sửa thay thế toàn bộ danh sách trong một giao dịch (không có khái niệm thêm/bớt từng cái).
 */
@Service
public class TopicService {

    public static final int MAX_TOPICS = 5;

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final CategoryService categories;
    private final Clock clock;

    public TopicService(JdbcTemplate jdbc, UserRepository users, CategoryService categories, Clock clock) {
        this.jdbc = jdbc;
        this.users = users;
        this.categories = categories;
        this.clock = clock;
    }

    /**
     * Thay toàn bộ chủ đề của Creator bằng {@code categoryIds} (thứ tự giữ nguyên, bỏ trùng).
     *
     * @throws InvalidTopicException không phải Creator, quá {@value #MAX_TOPICS} mục, hoặc danh mục không còn dùng được
     */
    @Transactional
    public void replace(UUID creatorId, List<UUID> categoryIds) {
        users.findById(creatorId).filter(User::isActive).filter(u -> u.hasRole(Role.CREATOR))
                .orElseThrow(() -> new InvalidTopicException("Chỉ Creator mới chỉnh được chủ đề."));
        Set<UUID> unique = new LinkedHashSet<>(categoryIds == null ? List.of() : categoryIds);
        if (unique.size() > MAX_TOPICS) {
            throw new InvalidTopicException("Chỉ chọn tối đa " + MAX_TOPICS + " chủ đề.");
        }
        List<UUID> ordered = new ArrayList<>(unique);
        for (UUID id : ordered) {
            try {
                categories.requireActive(id);
            } catch (InvalidCategoryException e) {
                throw new InvalidTopicException(e.getMessage());
            }
        }
        jdbc.update("delete from creator_topics where user_id = ?", creatorId);
        Timestamp now = Timestamp.from(clock.instant());
        for (int i = 0; i < ordered.size(); i++) {
            jdbc.update("insert into creator_topics (user_id, category_id, position, created_at) values (?, ?, ?, ?)",
                    creatorId, ordered.get(i), (short) i, now);
        }
    }

    /** Chủ đề đã chọn của một Creator, theo thứ tự đã lưu. */
    @Transactional(readOnly = true)
    public List<CategoryView> topicsFor(UUID creatorId) {
        List<UUID> ids = jdbc.queryForList(
                "select category_id from creator_topics where user_id = ? order by position", UUID.class, creatorId);
        if (ids.isEmpty()) {
            return List.of();
        }
        var byId = categories.byIds(ids);
        List<CategoryView> result = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            CategoryView view = byId.get(id);
            if (view != null) {
                result.add(view);
            }
        }
        return result;
    }

    /** Id các chủ đề đã chọn (cho form chỉnh sửa), theo thứ tự đã lưu. */
    @Transactional(readOnly = true)
    public List<UUID> topicIdsFor(UUID creatorId) {
        return jdbc.queryForList(
                "select category_id from creator_topics where user_id = ? order by position", UUID.class, creatorId);
    }
}
