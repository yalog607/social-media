package com.aloute.service.season;

import com.aloute.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class SeasonHallIT extends IntegrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test void anonymousCannotReadPageOrJson() throws Exception {
        mvc.perform(get("/seasons")).andExpect(status().isFound());
        mvc.perform(get("/api/seasons/hall").accept("application/json")).andExpect(status().isUnauthorized());
    }

    @Test void emptyHallHasBootstrapAndKeepsMvcFallback() throws Exception {
        var user = createUser();
        mvc.perform(get("/api/seasons/hall").with(asUser(user)))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/seasons").with(asUser(user))).andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"season-hall-root\"")))
                .andExpect(content().string(containsString("data-seasons=\"[]\"")))
                .andExpect(content().string(containsString("Bảng vàng đang chờ những cái tên đầu tiên")))
                .andExpect(content().string(containsString("/js/react/season-hall.js")));
    }

    @Test void settledOnlyInExistingOrderWithSafeDisplayContract() throws Exception {
        var user = createUser();
        UUID older = insert(user.getId(), "Older", "SETTLED", "2025-01-01T00:00:00Z");
        UUID newer = insert(user.getId(), "New </script> \" & <img onerror=alert(1)>", "SETTLED", "2025-01-02T00:00:00Z");
        insert(user.getId(), "Hidden draft", "DRAFT", "2025-02-01T00:00:00Z");
        insert(user.getId(), "Hidden active", "ACTIVE", "2025-02-02T00:00:00Z");
        mvc.perform(get("/api/seasons/hall").with(asUser(user))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(newer.toString()))
                .andExpect(jsonPath("$[1].id").value(older.toString()))
                .andExpect(jsonPath("$[0].startDate").value("07:00 02/01/2025"))
                .andExpect(jsonPath("$[0].status").doesNotExist())
                .andExpect(jsonPath("$[0].description").doesNotExist());
        mvc.perform(get("/seasons").with(asUser(user))).andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;/script&gt;")))
                .andExpect(content().string(containsString("/seasons/" + newer)));
    }

    private UUID insert(UUID actor, String name, String status, String start) {
        UUID id = UUID.randomUUID();
        Instant instant = Instant.parse(start);
        jdbc.update("insert into seasons(id,name,required_hashtag,status,start_time,end_time,created_by) values (?,?,?,?,?,?,?)",
                id, name, "pilot", status, Timestamp.from(instant), Timestamp.from(instant.plusSeconds(86400)), actor);
        return id;
    }
}
