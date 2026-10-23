package com.aloute.report;

import com.aloute.exception.report.InvalidReportException;
import com.aloute.model.report.Report;
import com.aloute.model.report.ReportReason;
import com.aloute.model.report.ReportStatus;
import com.aloute.model.report.ReportTargetType;
import com.aloute.service.report.ReportService;

import com.aloute.model.comment.Comment;
import com.aloute.service.comment.CommentService;
import com.aloute.model.post.Post;
import com.aloute.exception.post.PostNotFoundException;
import com.aloute.service.post.PostService;
import com.aloute.support.IntegrationTest;
import com.aloute.model.user.User;
import com.aloute.model.user.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Gửi báo cáo bài viết, bình luận, người dùng: quyền xem, tự báo cáo, trùng lặp và HTTP. */
class ReportServiceIT extends IntegrationTest {

    @Autowired ReportService reports;
    @Autowired PostService posts;
    @Autowired CommentService comments;

    private Post publicPost(User author) {
        return posts.create(author.getId(), "bài", Visibility.PUBLIC, List.of(), null);
    }

    @Test
    void reportsAPostAsOpen() {
        User author = createUser();
        User reporter = createUser();
        Post post = publicPost(author);

        Report report = reports.submit(reporter.getId(), ReportTargetType.POST, post.getId(), ReportReason.SPAM, "  quảng cáo  ");

        assertThat(report.getStatus()).isEqualTo(ReportStatus.OPEN);
        assertThat(report.getDetail()).isEqualTo("quảng cáo");
    }

    @Test
    void reportsACommentAndAUser() {
        User author = createUser();
        User reporter = createUser();
        Post post = publicPost(author);
        Comment comment = comments.create(author.getId(), post.getId(), null, "bình luận");

        assertThat(reports.submit(reporter.getId(), ReportTargetType.COMMENT, comment.getId(), ReportReason.HARASSMENT, null)).isNotNull();
        assertThat(reports.submit(reporter.getId(), ReportTargetType.USER, author.getId(), ReportReason.OTHER, null)).isNotNull();
    }

    @Test
    void cannotReportYourselfOrYourOwnContent() {
        User author = createUser();
        Post post = publicPost(author);

        assertThatThrownBy(() -> reports.submit(author.getId(), ReportTargetType.POST, post.getId(), ReportReason.SPAM, null))
                .isInstanceOf(InvalidReportException.class);
        assertThatThrownBy(() -> reports.submit(author.getId(), ReportTargetType.USER, author.getId(), ReportReason.SPAM, null))
                .isInstanceOf(InvalidReportException.class);
    }

    @Test
    void cannotReportAPostYouCannotSee() {
        User author = createUser();
        Post hidden = posts.create(author.getId(), "riêng tư", Visibility.PRIVATE, List.of(), null);

        assertThatThrownBy(() -> reports.submit(createUser().getId(), ReportTargetType.POST, hidden.getId(), ReportReason.SPAM, null))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    void reportingTheSameTargetTwiceIsRejectedWhileOpen() {
        User reporter = createUser();
        Post post = publicPost(createUser());
        reports.submit(reporter.getId(), ReportTargetType.POST, post.getId(), ReportReason.SPAM, null);

        assertThatThrownBy(() -> reports.submit(reporter.getId(), ReportTargetType.POST, post.getId(), ReportReason.OTHER, null))
                .isInstanceOf(InvalidReportException.class).hasMessageContaining("đã báo cáo");
    }

    @Test
    void rejectsTooLongDetail() {
        Post post = publicPost(createUser());

        assertThatThrownBy(() -> reports.submit(createUser().getId(), ReportTargetType.POST, post.getId(),
                ReportReason.SPAM, "x".repeat(Report.MAX_DETAIL_LENGTH + 1)))
                .isInstanceOf(InvalidReportException.class);
    }

    @Test
    void httpEndpointAcceptsReportsRejectsDuplicatesAndGuests() throws Exception {
        Post post = publicPost(createUser());
        User reporter = createUser();

        mvc.perform(post("/api/reports").param("targetType", "POST").param("targetId", post.getId().toString())
                        .param("reason", "SPAM").with(csrf()).with(asUser(reporter)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/reports").param("targetType", "POST").param("targetId", post.getId().toString())
                        .param("reason", "SPAM").with(csrf()).with(asUser(reporter)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/reports").param("targetType", "POST").param("targetId", post.getId().toString())
                        .param("reason", "SPAM").with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
