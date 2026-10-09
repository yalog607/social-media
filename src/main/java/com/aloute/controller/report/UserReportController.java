package com.aloute.controller.report;

import com.aloute.exception.report.InvalidReportException;
import com.aloute.model.report.ReportTargetType;
import com.aloute.service.report.ReportService;

import com.aloute.repository.comment.CommentRepository;
import com.aloute.repository.post.PostRepository;
import com.aloute.service.post.PostViewAssembler;
import com.aloute.security.AlouteUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Controller
public class UserReportController {

    private final ReportService reports;
    private final PostRepository posts;
    private final PostViewAssembler postAssembler;
    private final CommentRepository comments;

    public UserReportController(ReportService reports,
                                PostRepository posts,
                                PostViewAssembler postAssembler,
                                CommentRepository comments) {
        this.reports = reports;
        this.posts = posts;
        this.postAssembler = postAssembler;
        this.comments = comments;
    }

    @GetMapping("/reports")
    public String mine(@AuthenticationPrincipal AlouteUserPrincipal me, Model model) {
        model.addAttribute("reports", reports.mine(me.id()));
        return "report/mine";
    }

    @GetMapping("/reports/{id}")
    @Transactional(readOnly = true)
    public String detail(@AuthenticationPrincipal AlouteUserPrincipal me, @PathVariable UUID id, Model model) {
        try {
            ReportService.MyReportItem report = reports.getMyReport(me.id(), id);
            model.addAttribute("report", report);
            UUID targetPostId = null;
            if (report.type() == ReportTargetType.POST) {
                targetPostId = report.targetId();
            } else if (report.type() == ReportTargetType.COMMENT) {
                var commentOpt = comments.findById(report.targetId());
                if (commentOpt.isPresent() && commentOpt.get().getPost() != null) {
                    targetPostId = commentOpt.get().getPost().getId();
                }
            }
            if (targetPostId != null) {
                posts.findLive(targetPostId)
                        .ifPresent(p -> {
                            var postViews = postAssembler.assemble(List.of(p), me.id());
                            if (!postViews.isEmpty()) {
                                model.addAttribute("reportedPost", postViews.get(0));
                                model.addAttribute("post", postViews.get(0));
                            }
                        });
            }
            return "report/detail";
        } catch (InvalidReportException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Báo cáo không tồn tại.");
        }
    }
}
