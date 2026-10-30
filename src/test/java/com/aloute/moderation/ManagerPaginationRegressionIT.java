package com.aloute.moderation;

import com.aloute.dto.moderation.ManagerPage;
import com.aloute.model.user.Role;
import com.aloute.service.moderation.*;
import com.aloute.service.category.CategoryService;
import com.aloute.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.sql.Timestamp;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Fixtures and cleanup are rolled back with each test; never changes the dev database. */
@Transactional
class ManagerPaginationRegressionIT extends IntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ModerationService moderation;
    @Autowired SupportService support;
    @Autowired CategoryService categories;
    @Autowired BannedHashtags hashtags;
    @Autowired ManageStats stats;
    private static final Timestamp SAME_TIME = Timestamp.from(Instant.now().minusSeconds(60));

    @BeforeEach void isolateQueues() {
        jdbc.update("update reports set status='DISMISSED' where status='OPEN'");
        jdbc.update("update reports set created_at=now()-interval '60 days'");
        jdbc.update("update support_tickets set status='CLOSED' where status='OPEN'");
        jdbc.update("update users set status='ACTIVE' where status='SUSPENDED'");
        jdbc.update("update posts set category_id=null where category_id is not null");
        jdbc.update("delete from categories");
        jdbc.update("delete from banned_hashtags");
    }

    private UUID rawUser(boolean profile) {
        UUID id = UUID.randomUUID();
        String name = "p" + id.toString().replace("-", "").substring(0,20);
        jdbc.update("insert into users(id,email,username,status,updated_at) values (?,?,?,'SUSPENDED',?)", id,name+"@paging.test",name,SAME_TIME);
        jdbc.update("insert into user_roles(user_id,role) values (?,'USER')",id);
        if(profile) jdbc.update("insert into profiles(user_id,display_name) values (?,?)",id,name);
        return id;
    }

    private UUID report(UUID reporter, UUID target) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into reports(id,reporter_id,target_type,target_id,reason,created_at) values (?,?,'USER',?,'SPAM',?)",id,reporter,target,SAME_TIME);
        return id;
    }

    private UUID ticket(UUID user) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into support_tickets(id,user_id,subject,body,created_at) values (?,?,'paging','body',?)",id,user,SAME_TIME);
        return id;
    }

    @Test void countsAndLastPageExcludeRowsWithoutProfiles() throws Exception {
        var manager=createUser(Role.MANAGER);
        for(int i=0;i<20;i++) {
            UUID visible=rawUser(true);
            report(manager.getId(),visible);
            ticket(visible);
        }
        UUID hidden=rawUser(false);
        report(hidden,manager.getId());
        ticket(hidden);
        assertThat(moderation.countOpenReports()).isEqualTo(20);
        assertThat(support.countOpenTickets()).isEqualTo(20);
        assertThat(moderation.countSuspended()).isEqualTo(20);
        assertThat(moderation.openReports(20,20)).isEmpty();
        assertThat(support.open(20,20)).isEmpty();
        assertThat(moderation.suspended(20,20)).isEmpty();
        for(String path:List.of("/manage","/manage/support","/manage/suspended")) {
            mvc.perform(get(path).param("page","2").with(asUser(manager)))
                .andExpect(status().isOk()).andExpect(model().attribute("pagination",ManagerPage.of("1","20",20)));
        }
        UUID visible=rawUser(true);
        UUID selectedReport=report(manager.getId(),visible), selectedTicket=ticket(visible);
        mvc.perform(post("/manage/reports/"+selectedReport).param("action","DISMISS").param("page","2").param("size","20").with(csrf()).with(asUser(manager)))
            .andExpect(redirectedUrl("/manage?page=1&size=20"));
        mvc.perform(post("/manage/support/"+selectedTicket).param("reply","done").param("page","2").param("size","20").with(csrf()).with(asUser(manager)))
            .andExpect(redirectedUrl("/manage/support?page=1&size=20"));
        mvc.perform(post("/manage/users/"+visible+"/lift").param("page","2").param("size","20").with(csrf()).with(asUser(manager)))
            .andExpect(redirectedUrl("/manage/suspended?page=1&size=20"));
    }

    private void fill(UUID manager, int n) {
        for(int i=0;i<n;i++) {
            UUID user=rawUser(true);
            report(manager,user);
            ticket(user);
            jdbc.update("insert into banned_hashtags(tag,banned_by,created_at) values (?,?,?)",String.format("page%03d",i),manager,SAME_TIME);
            if(i<100) jdbc.update("insert into categories(id,name,slug,active) values (?,'Same',?,?)", UUID.randomUUID(),"page-"+i,i%2==0);
        }
    }

    @ParameterizedTest @ValueSource(ints={0,20,21,121})
    void exactBoundariesAndTiedOrderingForEveryList(int n) throws Exception {
        var manager=createUser(Role.MANAGER);
        fill(manager.getId(),n);
        var reportIds=jdbc.queryForList("select id from reports where status='OPEN' order by created_at,id",UUID.class);
        var ticketIds=jdbc.queryForList("select id from support_tickets where status='OPEN' order by created_at,id",UUID.class);
        var suspendedIds=jdbc.queryForList("select u.id from users u join profiles p on p.user_id=u.id where u.status='SUSPENDED' order by u.updated_at desc,u.id desc",UUID.class);
        var categoryIds=jdbc.queryForList("select id from categories order by active desc,lower(name),id",UUID.class);
        assertThat(reportIds).isSortedAccordingTo(Comparator.comparing(UUID::toString));
        assertThat(ticketIds).isSortedAccordingTo(Comparator.comparing(UUID::toString));
        assertThat(suspendedIds).isSortedAccordingTo(Comparator.comparing(UUID::toString).reversed());
        for(int size:List.of(20,50,100)) {
            List<UUID> actualReports=new ArrayList<>(),actualTickets=new ArrayList<>(),actualSuspended=new ArrayList<>(),actualCategories=new ArrayList<>();
            List<String> actualTags=new ArrayList<>();
            for(long offset=0;offset<=n;offset+=size) {
                actualReports.addAll(moderation.openReports(size,offset).stream().map(r->r.id()).toList());
                actualTickets.addAll(support.open(size,offset).stream().map(t->t.id()).toList());
                actualSuspended.addAll(moderation.suspended(size,offset).stream().map(u->u.userId()).toList());
                actualCategories.addAll(categories.all(size,offset).stream().map(c->c.id()).toList());
                actualTags.addAll(hashtags.list(size,offset));
            }
            assertThat(actualReports).containsExactlyElementsOf(reportIds);
            assertThat(actualTickets).containsExactlyElementsOf(ticketIds);
            assertThat(actualSuspended).containsExactlyElementsOf(suspendedIds);
            assertThat(actualCategories).containsExactlyElementsOf(categoryIds);
            assertThat(actualTags).containsExactlyElementsOf(java.util.stream.IntStream.range(0,n).mapToObj(i->String.format("page%03d",i)).toList());
            for(String path:List.of("/manage","/manage/support","/manage/suspended","/manage/hashtags","/manage/categories")) {
                long total=path.endsWith("categories")?Math.min(n,100):n;
                mvc.perform(get(path).param("page","999").param("size",""+size).with(asUser(manager)))
                    .andExpect(status().isOk()).andExpect(model().attribute("pagination",ManagerPage.of("999",""+size,total)));
            }
        }
        assertThat(categories.active()).hasSize((Math.min(n,100)+1)/2);
    }

    @Test void groupedReasonsHaveCountsTiesCutoffAndDeterministicOffsets() throws Exception {
        var manager=createUser(Role.MANAGER);
        UUID target=rawUser(true);
        String[] reasons={"SPAM","HARASSMENT","INAPPROPRIATE","MISINFO","OTHER"};
        UUID old=report(manager.getId(),target);
        jdbc.update("update reports set status='DISMISSED',created_at=now()-interval '31 days' where id=?",old);
        for(String reason:reasons) {
            UUID id=report(manager.getId(),target);
            jdbc.update("update reports set reason=?,status='DISMISSED' where id=?",reason,id);
        }
        assertThat(stats.countReasons()).isEqualTo(5);
        assertThat(stats.reasons().keySet()).containsExactly("HARASSMENT","INAPPROPRIATE","MISINFO","OTHER","SPAM");
        assertThat(stats.reasons().values()).containsOnly(1L);
        assertThat(stats.reasons(2,2).keySet()).containsExactly("MISINFO","OTHER");
        assertThat(stats.reasons(2,4).keySet()).containsExactly("SPAM");
        assertThat(stats.reasons(2,5)).isEmpty();
        mvc.perform(get("/manage/stats").param("page","999").with(asUser(manager)))
            .andExpect(status().isOk()).andExpect(model().attribute("pagination",ManagerPage.of("1","20",5)));
        report(rawUser(true),target);
        assertThat(stats.reasons().keySet()).containsExactly("SPAM","HARASSMENT","INAPPROPRIATE","MISINFO","OTHER");
        assertThat(stats.reasons().get("SPAM")).isEqualTo(2L);
    }

    @Test void reportPostKeepsPositionClampsAfterClosingWholeTargetAndPreservesError() throws Exception {
        var manager=createUser(Role.MANAGER);
        fill(manager.getId(),41);
        UUID id=moderation.openReports(20,40).getFirst().id();
        mvc.perform(post("/manage/reports/"+id).param("action","DISMISS").param("page","3").param("size","20").with(csrf()).with(asUser(manager)))
            .andExpect(redirectedUrl("/manage?page=2&size=20")).andExpect(flash().attributeExists("notice"));
        UUID second=moderation.openReports(20,20).getFirst().id();
        mvc.perform(post("/manage/reports/"+second).param("action","DISMISS").param("page","2").param("size","20").with(csrf()).with(asUser(manager)))
            .andExpect(redirectedUrl("/manage?page=2&size=20"));
        mvc.perform(post("/manage/reports/"+second).param("action","DISMISS").param("page","2").param("size","20").with(csrf()).with(asUser(manager)))
            .andExpect(redirectedUrl("/manage?page=2&size=20")).andExpect(flash().attributeExists("error"));
        jdbc.update("update reports set status='DISMISSED' where status='OPEN'");
        UUID target=rawUser(true), selected=null;
        for(int i=0;i<21;i++) selected=report(rawUser(true),target);
        mvc.perform(post("/manage/reports/"+selected).param("action","DISMISS").param("page","2").param("size","20").with(csrf()).with(asUser(manager)))
            .andExpect(redirectedUrl("/manage?page=1&size=20"));
        assertThat(moderation.countOpenReports()).isZero();
    }

    @Test void supportPostKeepsPositionClampsAndPreservesError() throws Exception {
        var manager=createUser(Role.MANAGER);
        fill(manager.getId(),41);
        UUID id=support.open(20,40).getFirst().id();
        mvc.perform(post("/manage/support/"+id).param("reply","done").param("page","3").param("size","20").with(csrf()).with(asUser(manager)))
            .andExpect(redirectedUrl("/manage/support?page=2&size=20"));
        UUID second=support.open(20,20).getFirst().id();
        mvc.perform(post("/manage/support/"+second).param("reply","done").param("page","2").param("size","20").with(csrf()).with(asUser(manager)))
            .andExpect(redirectedUrl("/manage/support?page=2&size=20"));
        mvc.perform(post("/manage/support/"+second).param("reply","done").param("page","2").param("size","20").with(csrf()).with(asUser(manager)))
            .andExpect(redirectedUrl("/manage/support?page=2&size=20")).andExpect(flash().attributeExists("error"));
        jdbc.update("update support_tickets set status='CLOSED' where status='OPEN'");
        for(int i=0;i<21;i++) ticket(manager.getId());
        UUID last=support.open(20,20).getFirst().id();
        mvc.perform(post("/manage/support/"+last).param("reply","done").param("page","2").param("size","20").with(csrf()).with(asUser(manager)))
            .andExpect(redirectedUrl("/manage/support?page=1&size=20"));
    }

    @Test void liftPostKeepsPositionClampsAndPreservesError() throws Exception {
        var manager=createUser(Role.MANAGER);
        fill(manager.getId(),41);
        UUID id=moderation.suspended(20,40).getFirst().userId();
        mvc.perform(post("/manage/users/"+id+"/lift").param("page","3").param("size","20").with(csrf()).with(asUser(manager)))
            .andExpect(redirectedUrl("/manage/suspended?page=2&size=20"));
        assertThat(jdbc.queryForObject("select status from users where id=?",String.class,id)).isEqualTo("ACTIVE");
        assertThat(moderation.countSuspended()).isEqualTo(40);
        UUID second=moderation.suspended(20,20).getFirst().userId();
        mvc.perform(post("/manage/users/"+second+"/lift").param("page","2").param("size","20").with(csrf()).with(asUser(manager)))
            .andExpect(redirectedUrl("/manage/suspended?page=2&size=20"));
        mvc.perform(post("/manage/users/"+second+"/lift").param("page","2").param("size","20").with(csrf()).with(asUser(manager)))
            .andExpect(redirectedUrl("/manage/suspended?page=2&size=20")).andExpect(flash().attributeExists("error"));
        jdbc.update("update users set status='ACTIVE' where status='SUSPENDED'");
        for(int i=0;i<21;i++) rawUser(true);
        UUID last=moderation.suspended(20,20).getFirst().userId();
        mvc.perform(post("/manage/users/"+last+"/lift").param("page","2").param("size","20").with(csrf()).with(asUser(manager)))
            .andExpect(redirectedUrl("/manage/suspended?page=1&size=20"));
    }

    @Test void everyCategoryPostKeepsSizeAndPositionIncludingErrors() throws Exception {
        var manager=createUser(Role.MANAGER);
        fill(manager.getId(),21);
        UUID id=categories.all(20,20).getFirst().id();
        mvc.perform(post("/manage/categories/"+id+"/rename").param("name","Renamed").param("page","2").param("size","20").with(csrf()).with(asUser(manager)))
            .andExpect(redirectedUrl("/manage/categories?page=2&size=20"));
        mvc.perform(post("/manage/categories/"+id+"/active").param("active","false").param("page","2").param("size","20").with(csrf()).with(asUser(manager)))
            .andExpect(redirectedUrl("/manage/categories?page=2&size=20"));
        mvc.perform(post("/manage/categories").param("name","Created").param("page","2").param("size","20").with(csrf()).with(asUser(manager)))
            .andExpect(redirectedUrl("/manage/categories?page=2&size=20"));
        for(String path:List.of("/manage/categories","/manage/categories/"+id+"/rename","/manage/categories/"+UUID.randomUUID()+"/active")) {
            mvc.perform(post(path).param("name","").param("active","false").param("page","2").param("size","20").with(csrf()).with(asUser(manager)))
                .andExpect(redirectedUrl("/manage/categories?page=2&size=20")).andExpect(flash().attributeExists("error"));
        }
        mvc.perform(post("/manage/categories").param("name","Size preserved").param("page","999").param("size","50").with(csrf()).with(asUser(manager)))
            .andExpect(redirectedUrl("/manage/categories?page=1&size=50"));
        // Category actions do not remove rows: clamp stale input, keep the current page otherwise.
        assertThat(categories.count()).isEqualTo(23);
    }
}
