package com.aloute.report;

import com.aloute.comment.Comment;
import com.aloute.comment.CommentNotFoundException;
import com.aloute.comment.CommentRepository;
import com.aloute.common.RateAction;
import com.aloute.common.RateLimiter;
import com.aloute.post.PostService;
import com.aloute.user.User;
import com.aloute.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Nhận báo cáo vi phạm. Người báo cáo phải THẤY được đối tượng (cùng quy tắc xem bài), không được tự báo cáo mình,
 * và mỗi người chỉ có một báo cáo đang mở cho mỗi đối tượng.
 */
@Service
public class ReportService {

    private final ReportRepository reports;
    private final PostService posts;
    private final CommentRepository comments;
    private final UserRepository users;
    private final RateLimiter rateLimiter;

    public ReportService(ReportRepository reports, PostService posts, CommentRepository comments,
                         UserRepository users, RateLimiter rateLimiter) {
        this.reports = reports;
        this.posts = posts;
        this.comments = comments;
        this.users = users;
        this.rateLimiter = rateLimiter;
    }

    /**
     * @throws com.aloute.post.PostNotFoundException bài không tồn tại hoặc người báo cáo không xem được
     * @throws CommentNotFoundException              bình luận không tồn tại, đã xóa, hoặc thuộc bài không xem được
     * @throws InvalidReportException                tự báo cáo mình, báo cáo trùng, hoặc chi tiết quá dài
     */
    @Transactional
    public Report submit(UUID reporterId, ReportTargetType type, UUID targetId, ReportReason reason, String detail) {
        rateLimiter.check(RateAction.REPORT, reporterId);
        String text = cleanDetail(detail);
        UUID ownerId = resolveOwner(reporterId, type, targetId);
        if (ownerId.equals(reporterId)) {
            throw new InvalidReportException("Bạn không thể báo cáo chính mình.");
        }
        if (reports.existsByReporterIdAndTargetTypeAndTargetIdAndStatus(reporterId, type, targetId, ReportStatus.OPEN)) {
            throw new InvalidReportException("Bạn đã báo cáo mục này rồi, chúng tôi đang xem xét.");
        }
        Report report = new Report();
        report.setReporter(users.getReferenceById(reporterId));
        report.setTargetType(type);
        report.setTargetId(targetId);
        report.setReason(reason);
        report.setDetail(text.isEmpty() ? null : text);
        return reports.save(report);
    }

    /** Kiểm tra đối tượng tồn tại và người báo cáo xem được; trả về chủ của đối tượng. */
    private UUID resolveOwner(UUID reporterId, ReportTargetType type, UUID targetId) {
        return switch (type) {
            case POST -> posts.getVisible(targetId, reporterId).getAuthor().getId();
            case COMMENT -> {
                Comment comment = comments.findById(targetId).filter(c -> !c.isDeleted())
                        .orElseThrow(CommentNotFoundException::new);
                posts.getVisible(comment.getPost().getId(), reporterId);
                yield comment.getAuthor().getId();
            }
            case USER -> users.findById(targetId).filter(User::isActive)
                    .orElseThrow(() -> new InvalidReportException("Không tìm thấy người dùng này."))
                    .getId();
        };
    }

    private static String cleanDetail(String detail) {
        String text = detail == null ? "" : detail.replaceAll("[\\p{Cntrl}&&[^\\n\\r\\t]]", "").strip();
        if (text.codePointCount(0, text.length()) > Report.MAX_DETAIL_LENGTH) {
            throw new InvalidReportException("Chi tiết tối đa " + Report.MAX_DETAIL_LENGTH + " ký tự.");
        }
        return text;
    }
}
