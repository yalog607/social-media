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

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Nhận báo cáo vi phạm. Người báo cáo phải THẤY được đối tượng (cùng quy tắc xem bài), không được tự báo cáo mình,
 * và mỗi người chỉ có một báo cáo đang mở cho mỗi đối tượng.
 */
@Service
public class ReportService {

    public record MyReportItem(UUID id, UUID targetId, ReportTargetType type, ReportReason reason, String detail,
                               Instant createdAt, ReportStatus status, String preview, boolean removed,
                               String ownerName) {
    }

    private final ReportRepository reports;
    private final PostService posts;
    private final CommentRepository comments;
    private final UserRepository users;
    private final RateLimiter rateLimiter;
    private final JdbcTemplate jdbc;

    public ReportService(ReportRepository reports, PostService posts, CommentRepository comments,
                         UserRepository users, RateLimiter rateLimiter, JdbcTemplate jdbc) {
        this.reports = reports;
        this.posts = posts;
        this.comments = comments;
        this.users = users;
        this.rateLimiter = rateLimiter;
        this.jdbc = jdbc;
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

    @Transactional(readOnly = true)
    public List<MyReportItem> mine(UUID reporterId) {
        return jdbc.query("""
                select r.id, r.target_id, r.target_type, r.reason, r.detail, r.created_at, r.status,
                       coalesce(p.content, c.content, tp.display_name) as preview,
                       (p.deleted_at is not null or c.deleted_at is not null) as removed,
                       op.display_name
                from reports r
                left join posts p on r.target_type = 'POST' and p.id = r.target_id
                left join comments c on r.target_type = 'COMMENT' and c.id = r.target_id
                left join users tu on r.target_type = 'USER' and tu.id = r.target_id
                left join profiles tp on tp.user_id = tu.id
                left join profiles op on op.user_id = coalesce(p.author_id, c.author_id, tu.id)
                where r.reporter_id = ?
                order by r.created_at desc, r.id desc""",
                (rs, i) -> new MyReportItem(
                        rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        ReportTargetType.valueOf(rs.getString(3)),
                        ReportReason.valueOf(rs.getString(4)), rs.getString(5), rs.getTimestamp(6).toInstant(),
                        ReportStatus.valueOf(rs.getString(7)), clip(rs.getString(8)), rs.getBoolean(9),
                        rs.getString(10)),
                reporterId);
    }

    @Transactional(readOnly = true)
    public MyReportItem getMyReport(UUID reporterId, UUID reportId) {
        List<MyReportItem> items = jdbc.query("""
                select r.id, r.target_id, r.target_type, r.reason, r.detail, r.created_at, r.status,
                       coalesce(p.content, c.content, tp.display_name) as preview,
                       (p.deleted_at is not null or c.deleted_at is not null) as removed,
                       op.display_name
                from reports r
                left join posts p on r.target_type = 'POST' and p.id = r.target_id
                left join comments c on r.target_type = 'COMMENT' and c.id = r.target_id
                left join users tu on r.target_type = 'USER' and tu.id = r.target_id
                left join profiles tp on tp.user_id = tu.id
                left join profiles op on op.user_id = coalesce(p.author_id, c.author_id, tu.id)
                where r.id = ? and r.reporter_id = ?""",
                (rs, i) -> new MyReportItem(
                        rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        ReportTargetType.valueOf(rs.getString(3)),
                        ReportReason.valueOf(rs.getString(4)), rs.getString(5), rs.getTimestamp(6).toInstant(),
                        ReportStatus.valueOf(rs.getString(7)), clip(rs.getString(8)), rs.getBoolean(9),
                        rs.getString(10)),
                reportId, reporterId);
        if (items.isEmpty()) {
            throw new InvalidReportException("Báo cáo không tồn tại.");
        }
        return items.get(0);
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

    private static String clip(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.strip().replaceAll("\\s+", " ");
        return flat.codePointCount(0, flat.length()) <= 200 ? flat : flat.substring(0, flat.offsetByCodePoints(0, 200)) + "…";
    }
}
