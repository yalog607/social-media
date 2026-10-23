package com.aloute.report;

import com.aloute.model.report.Report;
import com.aloute.model.report.ReportReason;
import com.aloute.model.report.ReportTargetType;
import com.aloute.service.report.ReportService;

import com.aloute.model.comment.Comment;
import com.aloute.service.comment.CommentService;
import com.aloute.model.post.Post;
import com.aloute.service.post.PostService;
import com.aloute.dto.post.PostView;
import com.aloute.support.IntegrationTest;
import com.aloute.support.TestMedia;
import com.aloute.model.user.Role;
import com.aloute.model.user.User;
import com.aloute.model.user.Visibility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class UserReportControllerIT extends IntegrationTest {

    @Autowired ReportService reports;
    @Autowired PostService posts;
    @Autowired CommentService comments;

    @Test
    void detail_withTextAndHashtagPost_rendersFullPostCard() throws Exception {
        User creator = createUser(Role.CREATOR);
        User reporter = createUser();
        Post post = posts.create(creator.getId(), "Bài viết thú vị #DKIES rất hay", Visibility.PUBLIC, List.of(), null);

        Report report = reports.submit(reporter.getId(), ReportTargetType.POST, post.getId(), ReportReason.SPAM, "Spam bài viết");

        mvc.perform(get("/reports/" + report.getId()).with(asUser(reporter)))
                .andExpect(status().isOk())
                .andExpect(view().name("report/detail"))
                .andExpect(model().attributeExists("report"))
                .andExpect(model().attributeExists("reportedPost"))
                .andExpect(model().attributeExists("post"))
                .andExpect(result -> {
                    PostView view = (PostView) result.getModelAndView().getModel().get("reportedPost");
                    assertThat(view.author().username()).isEqualTo(creator.getUsername());
                    assertThat(view.author().primaryRole()).isEqualTo(Role.CREATOR);
                    assertThat(view.contentHtml()).contains("#DKIES");
                })
                .andExpect(content().string(containsString("id=\"feed\"")))
                .andExpect(content().string(containsString("id=\"post-" + post.getId() + "\"")))
                .andExpect(content().string(containsString("#DKIES")))
                .andExpect(content().string(containsString("Nhà sáng tạo")));
    }

    @Test
    void detail_withImagesPost_rendersMediaGrid() throws Exception {
        User author = createUser();
        User reporter = createUser();
        MockMultipartFile img1 = new MockMultipartFile("images", "img1.jpg", "image/jpeg", TestMedia.jpeg(100, 100));
        MockMultipartFile img2 = new MockMultipartFile("images", "img2.jpg", "image/jpeg", TestMedia.jpeg(100, 100));
        Post post = posts.create(author.getId(), "Bài có nhiều ảnh", Visibility.PUBLIC, List.of(img1, img2), null);

        Report report = reports.submit(reporter.getId(), ReportTargetType.POST, post.getId(), ReportReason.INAPPROPRIATE, "Ảnh không phù hợp");

        mvc.perform(get("/reports/" + report.getId()).with(asUser(reporter)))
                .andExpect(status().isOk())
                .andExpect(view().name("report/detail"))
                .andExpect(model().attributeExists("reportedPost"))
                .andExpect(result -> {
                    PostView view = (PostView) result.getModelAndView().getModel().get("reportedPost");
                    assertThat(view.hasMedia()).isTrue();
                    assertThat(view.media()).hasSize(2);
                })
                .andExpect(content().string(containsString("media-grid")))
                .andExpect(content().string(containsString("media-grid--2")));
    }

    @Test
    void detail_withVideoPost_rendersVideoPlayer() throws Exception {
        User author = createUser();
        User reporter = createUser();
        MockMultipartFile video = new MockMultipartFile("video", "clip.mp4", "video/mp4", TestMedia.mp4(1024));
        Post post = posts.create(author.getId(), "Bài có clip", Visibility.PUBLIC, List.of(), video);

        Report report = reports.submit(reporter.getId(), ReportTargetType.POST, post.getId(), ReportReason.OTHER, "Video vi phạm");

        mvc.perform(get("/reports/" + report.getId()).with(asUser(reporter)))
                .andExpect(status().isOk())
                .andExpect(view().name("report/detail"))
                .andExpect(model().attributeExists("reportedPost"))
                .andExpect(result -> {
                    PostView view = (PostView) result.getModelAndView().getModel().get("reportedPost");
                    assertThat(view.hasVideo()).isTrue();
                })
                .andExpect(content().string(containsString("class=\"post-video\"")));
    }

    @Test
    void detail_withCommentReport_loadsParentPostFullCard() throws Exception {
        User author = createUser();
        User commenter = createUser();
        User reporter = createUser();
        Post post = posts.create(author.getId(), "Bài viết gốc của bình luận", Visibility.PUBLIC, List.of(), null);
        Comment comment = comments.create(commenter.getId(), post.getId(), null, "Bình luận khiêu khích");

        Report report = reports.submit(reporter.getId(), ReportTargetType.COMMENT, comment.getId(), ReportReason.HARASSMENT, "Quấy rối");

        mvc.perform(get("/reports/" + report.getId()).with(asUser(reporter)))
                .andExpect(status().isOk())
                .andExpect(view().name("report/detail"))
                .andExpect(model().attributeExists("reportedPost"))
                .andExpect(result -> {
                    PostView view = (PostView) result.getModelAndView().getModel().get("reportedPost");
                    assertThat(view.id()).isEqualTo(post.getId());
                    assertThat(view.contentHtml()).contains("Bài viết gốc của bình luận");
                })
                .andExpect(content().string(containsString("id=\"post-" + post.getId() + "\"")));
    }

    @Test
    void detail_whenPostDeleted_rendersFallbackSafely() throws Exception {
        User author = createUser();
        User reporter = createUser();
        Post post = posts.create(author.getId(), "Bài viết sắp bị xóa", Visibility.PUBLIC, List.of(), null);

        Report report = reports.submit(reporter.getId(), ReportTargetType.POST, post.getId(), ReportReason.SPAM, "Spam");

        // Tác giả xóa bài viết
        posts.delete(author.getId(), post.getId());

        mvc.perform(get("/reports/" + report.getId()).with(asUser(reporter)))
                .andExpect(status().isOk())
                .andExpect(view().name("report/detail"))
                .andExpect(model().attributeDoesNotExist("reportedPost"))
                .andExpect(content().string(containsString("Nội dung này không còn tồn tại hoặc đã bị quản trị viên xóa.")));
    }
}
