package com.aloute.service.search;

import com.aloute.util.common.TextNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Điền {@code profiles.search_name} cho các hồ sơ có sẵn TRƯỚC khi cột này tồn tại (V4). Hồ sơ tạo mới hay
 * sửa sau đó tự có giá trị đúng nhờ {@code Profile#computeSearchName}, nên lần chạy nào cũng chỉ còn thấy các
 * hàng thật sự chưa có (rỗng) — chạy mỗi lần khởi động vô hại, chỉ tốn một truy vấn khi không còn gì để điền.
 */
@Component
public class SearchNameBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SearchNameBackfill.class);

    private final JdbcTemplate jdbc;

    public SearchNameBackfill(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select p.user_id as id, u.username as username, p.display_name as display_name
                from profiles p join users u on u.id = p.user_id
                where p.search_name = ''""");
        for (Map<String, Object> row : rows) {
            String searchName = TextNormalizer.forSearch(row.get("username") + " " + row.get("display_name"));
            jdbc.update("update profiles set search_name = ? where user_id = ?", searchName, (UUID) row.get("id"));
        }
        if (!rows.isEmpty()) {
            log.info("Đã điền search_name cho {} hồ sơ có sẵn", rows.size());
        }
    }
}
