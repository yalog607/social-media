package com.aloute.report;

import com.aloute.security.AlouteUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;
import java.util.UUID;

/** Gửi báo cáo qua AJAX từ hộp thoại "Báo cáo" dùng chung; lỗi nghiệp vụ trả 400 kèm thông báo để hiện ngay trong hộp thoại. */
@Controller
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @PostMapping("/api/reports")
    @ResponseBody
    public Map<String, Object> submit(@AuthenticationPrincipal AlouteUserPrincipal me,
                                      @RequestParam ReportTargetType targetType, @RequestParam UUID targetId,
                                      @RequestParam ReportReason reason,
                                      @RequestParam(required = false) String detail) {
        Report report = reports.submit(me.id(), targetType, targetId, reason, detail);
        return Map.of("id", report.getId().toString());
    }

    @ExceptionHandler(InvalidReportException.class)
    public ResponseEntity<Map<String, String>> invalid(InvalidReportException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }
}
