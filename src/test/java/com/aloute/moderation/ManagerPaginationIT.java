package com.aloute.moderation;

import com.aloute.dto.moderation.ManagerPage;
import com.aloute.model.user.Role;
import com.aloute.service.moderation.*;
import com.aloute.service.category.CategoryService;
import com.aloute.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
class ManagerPaginationIT extends IntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ModerationService moderation;
    @Autowired SupportService support;
    @Autowired BannedHashtags tags;
    @Autowired CategoryService categories;
    @Autowired ManageStats stats;

    @Test void rendersAllListsNormalizesInputsAndKeepsRoles() throws Exception {
        var manager = createUser(Role.MANAGER);
        var admin = createUser(Role.ADMIN);
        var user = createUser();
        var creator = createUser(Role.CREATOR);
        for (String path : new String[]{"/manage", "/manage/suspended", "/manage/support", "/manage/hashtags", "/manage/categories", "/manage/stats"}) {
            mvc.perform(get(path).param("page", "bad").param("size", "999").with(asUser(manager)))
                    .andExpect(status().isOk()).andExpect(model().attributeExists("pagination"))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("manager-size")))
                    .andExpect(result -> assertThat((ManagerPage) result.getModelAndView().getModel().get("pagination"))
                            .satisfies(p -> { assertThat(p.number()).isEqualTo(1); assertThat(p.size()).isEqualTo(20); }));
            mvc.perform(get(path).with(asUser(admin))).andExpect(status().isOk());
            mvc.perform(get(path).with(asUser(user))).andExpect(status().isForbidden());
            mvc.perform(get(path).with(asUser(creator))).andExpect(status().isForbidden());
        }
    }

    @Test void sqlPagesGoBeyondOldLimitsWithoutDuplicatesAndPostClampsLastPage() throws Exception {
        var manager = createUser(Role.MANAGER);
        var owner = createUser();
        jdbc.update("delete from banned_hashtags");
        for (int i = 0; i < 121; i++) {
            jdbc.update("insert into banned_hashtags(tag,banned_by,created_at) values (?,?,now())", String.format("paging%03d", i), manager.getId());
            jdbc.update("insert into support_tickets(id,user_id,subject,body,created_at) values (?,?, 'paging','body',now())", UUID.randomUUID(), owner.getId());
            UUID target = UUID.randomUUID();
            String username = "p" + target.toString().replace("-", "").substring(0, 20);
            jdbc.update("insert into users(id,email,username,status) values (?,?,?,'SUSPENDED')", target, username + "@paging.test", username);
            jdbc.update("insert into profiles(user_id,display_name) values (?,?)", target, username);
            jdbc.update("insert into reports(id,reporter_id,target_type,target_id,reason,created_at) values (?,?,'USER',?,'SPAM',now())", UUID.randomUUID(), manager.getId(), target);
        }
        assertThat(tags.count()).isEqualTo(121);
        assertThat(tags.list(100, 0)).hasSize(100).doesNotContainAnyElementsOf(tags.list(100, 100));
        assertThat(tags.list(20, 120)).containsExactly("paging120");
        assertThat(support.open(100, 0)).hasSize(100).doesNotContainAnyElementsOf(support.open(100, 100));
        assertThat(moderation.openReports(100, 0)).hasSize(100).doesNotContainAnyElementsOf(moderation.openReports(100, 100));
        assertThat(moderation.suspended(100, 0)).hasSize(100).doesNotContainAnyElementsOf(moderation.suspended(100, 100));
        mvc.perform(post("/manage/hashtags/remove").param("tag", "paging120").param("page", "7").param("size", "20").with(csrf()).with(asUser(manager)))
                .andExpect(redirectedUrl("/manage/hashtags?page=6&size=20"));
        mvc.perform(post("/manage/hashtags/remove").param("tag", "invalid tag").param("page", "2").param("size", "50").with(csrf()).with(asUser(manager)))
                .andExpect(redirectedUrl("/manage/hashtags?page=2&size=50")).andExpect(flash().attributeExists("error"));
        mvc.perform(post("/manage/hashtags/remove").param("tag", "paging000").with(asUser(manager))).andExpect(status().isForbidden());
    }

    @Test void categoriesSuspendedAndGroupedReasonsHaveStablePages() {
        var owner = createUser();
        jdbc.update("update users set status='SUSPENDED' where id=?", owner.getId());
        assertThat(moderation.suspended(20, 0)).isNotEmpty();
        long before = categories.count();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        for (int i=0; i<21; i++) {
            jdbc.update("insert into categories(id,name,slug,active) values (?,?,?,true)", UUID.randomUUID(), "Page " + suffix + i, "page-" + suffix + i);
        }
        assertThat(categories.count()).isEqualTo(before + 21);
        assertThat(categories.all(20, 0)).hasSize(20).doesNotContainAnyElementsOf(categories.all(20, 20));
        assertThat(categories.active().size()).isGreaterThanOrEqualTo(21);
        assertThat(stats.reasons(20, 0)).isEqualTo(stats.reasons());
    }
}
