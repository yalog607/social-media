package com.aloute.moderation;

import com.aloute.dto.moderation.ReportItem;
import com.aloute.dto.moderation.SuspendedUser;
import com.aloute.exception.moderation.InvalidModerationException;
import com.aloute.model.moderation.ReportAction;
import com.aloute.service.moderation.ModerationService;

import com.aloute.dto.audit.AuditEntry;
import com.aloute.service.audit.AuditService;
import com.aloute.model.comment.Comment;
import com.aloute.service.comment.CommentService;
import com.aloute.service.notification.NotificationService;
import com.aloute.model.post.Post;
import com.aloute.exception.post.PostNotFoundException;
import com.aloute.service.post.PostService;
import com.aloute.model.report.ReportReason;
import com.aloute.service.report.ReportService;
import com.aloute.model.report.ReportTargetType;
import com.aloute.security.RefreshTokenService;
import com.aloute.support.IntegrationTest;
import com.aloute.model.user.Role;
import com.aloute.model.user.User;
import com.aloute.model.user.UserStatus;
import com.aloute.model.user.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Hàng đợi báo cáo của Manager: xử lý, chế tài, nhật ký, và ranh giới quyền. */
class ModerationServiceIT extends IntegrationTest {

    @Autowired ModerationService moderation;
    @Autowired ReportService reports;
    @Autowired PostService posts;
    @Autowired CommentService comments;
    @Autowired AuditService audit;
    @Autowired NotificationService notifications;
    @Autowired RefreshTokenService refreshTokens;
    @Autowired JdbcTemplate jdbc;

    private Post publicPost(User author) {
        return posts.create(author.getId(), "bài vi phạm", Visibility.PUBLIC, List.of(), null);
    }

    private UUID report(User reporter, ReportTargetType type, UUID target) {
        return reports.submit(reporter.getId(), type, target, ReportReason.SPAM, null).getId();
    }

    private ReportItem queued(UUID reportId) {
        return moderation.openReports().stream().filter(r -> r.id().equals(reportId)).findFirst().orElseThrow();
    }

    @Test
    void queueShowsOpenReportsWithAPreviewOfTheTarget() {
        User author = createUser();
        Post post = publicPost(author);
        UUID id = report(createUser(), ReportTargetType.POST, post.getId());

        ReportItem item = queued(id);

        assertThat(item.preview()).isEqualTo("bài vi phạm");
        assertThat(item.ownerId()).isEqualTo(author.getId());
        assertThat(item.canRemoveContent()).isTrue();
    }

    @Test
    void removingContentHidesThePostAndClosesEveryReportOnIt() {
        User manager = createUser(Role.MANAGER);
        User author = createUser();
        Post post = publicPost(author);
        User reporter1 = createUser();
        User reporter2 = createUser();
        UUID first = report(reporter1, ReportTargetType.POST, post.getId());
        UUID second = report(reporter2, ReportTargetType.POST, post.getId());

        moderation.handle(manager.getId(), first, ReportAction.REMOVE_CONTENT, "spam");

        assertThatThrownBy(() -> posts.getVisible(post.getId(), null)).isInstanceOf(PostNotFoundException.class);
        assertThat(moderation.openReports()).extracting(ReportItem::id).doesNotContain(first, second);
        assertThat(jdbc.queryForObject("select status from reports where id = ?", String.class, second)).isEqualTo("RESOLVED");
        assertThat(jdbc.queryForObject("select handled_by from reports where id = ?", UUID.class, second)).isEqualTo(manager.getId());

        var reporterNotif = notifications.listRecent(reporter1.getId()).get(0);
        assertThat(reporterNotif.text()).isEqualTo("Báo cáo của bạn đã được xem xét và xác nhận có vi phạm.");
        assertThat(reporterNotif.detail()).isEqualTo("Nội dung bạn báo cáo đã được xác định là vi phạm Tiêu chuẩn cộng đồng và đã được xử lý.");

        var ownerNotif = notifications.listRecent(author.getId()).get(0);
        assertThat(ownerNotif.text()).isEqualTo("Bài viết của bạn đã bị gỡ do vi phạm Tiêu chuẩn cộng đồng.");
        assertThat(ownerNotif.detail()).isEqualTo("Bài viết: \"bài vi phạm\" • Lý do xử lý: spam");
    }

    @Test
    void removingACommentSoftDeletesIt() {
        User manager = createUser(Role.MANAGER);
        Post post = publicPost(createUser());
        User author = createUser();
        Comment comment = comments.create(author.getId(), post.getId(), null, "xấu");
        UUID id = report(createUser(), ReportTargetType.COMMENT, comment.getId());

        moderation.handle(manager.getId(), id, ReportAction.REMOVE_CONTENT, null);

        assertThat(jdbc.queryForObject("select deleted_at is not null from comments where id = ?", Boolean.class, comment.getId())).isTrue();
    }

    @Test
    void dismissingLeavesContentUntouched() {
        User manager = createUser(Role.MANAGER);
        User author = createUser();
        Post post = publicPost(author);
        User reporter = createUser();
        UUID id = report(reporter, ReportTargetType.POST, post.getId());

        moderation.handle(manager.getId(), id, ReportAction.DISMISS, null);

        assertThat(posts.getVisible(post.getId(), null).getId()).isEqualTo(post.getId());
        assertThat(jdbc.queryForObject("select status from reports where id = ?", String.class, id)).isEqualTo("DISMISSED");

        var reporterNotif = notifications.listRecent(reporter.getId()).get(0);
        assertThat(reporterNotif.text()).isEqualTo("Báo cáo của bạn đã được xem xét và chưa phát hiện vi phạm.");
        assertThat(reporterNotif.detail()).isEqualTo("Chúng tôi chưa phát hiện nội dung được báo cáo vi phạm Tiêu chuẩn cộng đồng.");

        var ownerNotif = notifications.listRecent(author.getId()).get(0);
        assertThat(ownerNotif.text()).isEqualTo("Một báo cáo về bài viết của bạn đã được xem xét và không ghi nhận vi phạm.");
        assertThat(ownerNotif.detail()).isEqualTo("Bài viết: \"bài vi phạm\"");
    }

    @Test
    void warningNotifiesTheOwnerAndNeedsAReason() {
        User manager = createUser(Role.MANAGER);
        User author = createUser();
        UUID id = report(createUser(), ReportTargetType.POST, publicPost(author).getId());

        assertThatThrownBy(() -> moderation.handle(manager.getId(), id, ReportAction.WARN, " "))
                .isInstanceOf(InvalidModerationException.class);
        moderation.handle(manager.getId(), id, ReportAction.WARN, "ngôn từ không phù hợp");

        var notifs = notifications.listRecent(author.getId());
        assertThat(notifs).anyMatch(n -> n.text().equals("Bạn đã nhận được cảnh báo về việc vi phạm Tiêu chuẩn cộng đồng."));
        assertThat(notifs).anyMatch(n -> n.text().equals("Một bài viết của bạn đã được xem xét và xác định vi phạm Tiêu chuẩn cộng đồng.")
                && "Bài viết: \"bài vi phạm\" • Lý do xử lý: ngôn từ không phù hợp".equals(n.detail()));
        assertThat(jdbc.queryForObject("select count(*) from sanctions where user_id = ? and type = 'WARNING'", Long.class, author.getId()))
                .isEqualTo(1);
        assertThat(users.findById(author.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void suspendingLocksTheAccountRevokesSessionsAndCanBeLifted() {
        User manager = createUser(Role.MANAGER);
        User author = createUser();
        String refresh = refreshTokens.issue(author, "test");
        UUID id = report(createUser(), ReportTargetType.POST, publicPost(author).getId());

        moderation.handle(manager.getId(), id, ReportAction.SUSPEND, "vi phạm nghiêm trọng");

        assertThat(users.findById(author.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(refreshTokens.rotate(refresh, "test")).as("phiên cũ bị thu hồi").isEmpty();
        assertThat(moderation.suspended()).extracting(SuspendedUser::userId).contains(author.getId());

        moderation.lift(manager.getId(), author.getId());

        assertThat(users.findById(author.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThatThrownBy(() -> moderation.lift(manager.getId(), author.getId())).isInstanceOf(InvalidModerationException.class);
    }

    @Test
    void cannotSanctionStaffOrYourselfAndAReportIsHandledOnce() {
        User manager = createUser(Role.MANAGER);
        User otherManager = createUser(Role.MANAGER);
        UUID staff = report(createUser(), ReportTargetType.USER, otherManager.getId());
        UUID self = report(createUser(), ReportTargetType.USER, manager.getId());

        assertThatThrownBy(() -> moderation.handle(manager.getId(), staff, ReportAction.SUSPEND, "x"))
                .hasMessageContaining("nhân sự");
        assertThatThrownBy(() -> moderation.handle(manager.getId(), self, ReportAction.WARN, "x"))
                .hasMessageContaining("chính mình");
        assertThatThrownBy(() -> moderation.handle(manager.getId(), staff, ReportAction.REMOVE_CONTENT, null))
                .isInstanceOf(InvalidModerationException.class);

        moderation.handle(manager.getId(), staff, ReportAction.DISMISS, null);
        assertThatThrownBy(() -> moderation.handle(manager.getId(), staff, ReportAction.DISMISS, null))
                .hasMessageContaining("đã được xử lý");
    }

    @Test
    void everyActionIsWrittenToTheAuditLog() {
        User manager = createUser(Role.MANAGER);
        UUID id = report(createUser(), ReportTargetType.POST, publicPost(createUser()).getId());

        moderation.handle(manager.getId(), id, ReportAction.DISMISS, "không vi phạm");

        List<AuditEntry> log = audit.page(0, "REPORT_DISMISS");
        assertThat(log).anyMatch(e -> "không vi phạm".equals(e.detail()) && e.actorUsername().equals(manager.getUsername()));
    }

    @Test
    void pagesAreRestrictedToManagers() throws Exception {
        User manager = createUser(Role.MANAGER);
        User creator = createUser(Role.CREATOR);
        UUID id = report(createUser(), ReportTargetType.POST, publicPost(createUser()).getId());

        mvc.perform(get("/manage").with(asUser(manager))).andExpect(status().isOk())
                .andExpect(content().string(containsString("bài vi phạm")));
        mvc.perform(get("/manage/suspended").with(asUser(manager))).andExpect(status().isOk());
        mvc.perform(post("/manage/reports/" + id).param("action", "DISMISS").with(csrf()).with(asUser(creator)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/manage/reports/" + id).param("action", "DISMISS").with(csrf()).with(asUser(manager)))
                .andExpect(status().is3xxRedirection());
    }
}
