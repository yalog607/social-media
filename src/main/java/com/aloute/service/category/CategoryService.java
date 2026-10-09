package com.aloute.service.category;

import com.aloute.dto.category.CategoryView;
import com.aloute.exception.category.InvalidCategoryException;

import com.aloute.service.audit.AuditService;
import com.aloute.util.common.TextNormalizer;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Danh mục bài viết: Manager tạo/đổi tên/ngưng dùng; người đăng bài chọn một danh mục (không bắt buộc). */
@Service
public class CategoryService {

    public static final int MAX_NAME = 40;
    private static final int MAX_CATEGORIES = 100;

    private static final RowMapper<CategoryView> MAP = (rs, i) -> new CategoryView(
            rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("slug"), rs.getBoolean("active"));

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final Clock clock;

    public CategoryService(JdbcTemplate jdbc, AuditService audit, Clock clock) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    /** Danh mục đang dùng, theo tên — cho ô chọn khi đăng bài. */
    @Transactional(readOnly = true)
    public List<CategoryView> active() {
        return jdbc.query("select id, name, slug, active from categories where active order by lower(name)", MAP);
    }

    /** Mọi danh mục kể cả đã ngưng — cho trang quản lý. */
    @Transactional(readOnly = true)
    public List<CategoryView> all() {
        return jdbc.query("select id, name, slug, active from categories order by active desc, lower(name)", MAP);
    }

    @Transactional(readOnly = true)
    public Optional<CategoryView> bySlug(String slug) {
        return jdbc.query("select id, name, slug, active from categories where slug = ?", MAP, slug).stream().findFirst();
    }

    /** Nhiều danh mục cùng lúc (một truy vấn), để dựng cả trang bài mà không N+1. */
    @Transactional(readOnly = true)
    public Map<UUID, CategoryView> byIds(Collection<UUID> ids) {
        Map<UUID, CategoryView> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        String marks = String.join(",", Collections.nCopies(ids.size(), "?"));
        jdbc.query("select id, name, slug, active from categories where id in (" + marks + ")", MAP, ids.toArray())
                .forEach(c -> result.put(c.id(), c));
        return result;
    }

    /** @throws InvalidCategoryException không tồn tại hoặc đã ngưng dùng */
    @Transactional(readOnly = true)
    public CategoryView requireActive(UUID id) {
        return byIds(List.of(id)).values().stream().filter(CategoryView::active).findFirst()
                .orElseThrow(() -> new InvalidCategoryException("Danh mục này không còn dùng được."));
    }

    /** @throws InvalidCategoryException tên trống, quá dài, trùng, hoặc đã quá {@value #MAX_CATEGORIES} danh mục */
    @Transactional
    public CategoryView create(UUID managerId, String rawName) {
        String name = cleanName(rawName);
        Long total = jdbc.queryForObject("select count(*) from categories", Long.class);
        if (total != null && total >= MAX_CATEGORIES) {
            throw new InvalidCategoryException("Tối đa " + MAX_CATEGORIES + " danh mục.");
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into categories (id, name, slug, created_at) values (?, ?, ?, ?)",
                    id, name, slugOf(name), Timestamp.from(clock.instant()));
        } catch (DuplicateKeyException e) {
            throw new InvalidCategoryException("Đã có danh mục tên tương tự.");
        }
        audit.log(managerId, "CATEGORY_CREATED", "CATEGORY", id, name);
        return new CategoryView(id, name, slugOf(name), true);
    }

    /** Đổi tên (địa chỉ {@code slug} giữ nguyên để liên kết cũ không hỏng). */
    @Transactional
    public void rename(UUID managerId, UUID id, String rawName) {
        String name = cleanName(rawName);
        if (jdbc.update("update categories set name = ? where id = ?", name, id) == 0) {
            throw new InvalidCategoryException("Không tìm thấy danh mục này.");
        }
        audit.log(managerId, "CATEGORY_RENAMED", "CATEGORY", id, name);
    }

    @Transactional
    public void setActive(UUID managerId, UUID id, boolean active) {
        if (jdbc.update("update categories set active = ? where id = ?", active, id) == 0) {
            throw new InvalidCategoryException("Không tìm thấy danh mục này.");
        }
        audit.log(managerId, active ? "CATEGORY_ENABLED" : "CATEGORY_DISABLED", "CATEGORY", id, null);
    }

    static String slugOf(String name) {
        String slug = TextNormalizer.forSearch(name).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return slug.isEmpty() ? "muc" : (slug.length() > 50 ? slug.substring(0, 50) : slug);
    }

    private static String cleanName(String raw) {
        String name = raw == null ? "" : raw.replaceAll("[\\p{Cntrl}]", " ").strip().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            throw new InvalidCategoryException("Tên danh mục không được để trống.");
        }
        if (name.codePointCount(0, name.length()) > MAX_NAME) {
            throw new InvalidCategoryException("Tên danh mục tối đa " + MAX_NAME + " ký tự.");
        }
        return name;
    }
}
