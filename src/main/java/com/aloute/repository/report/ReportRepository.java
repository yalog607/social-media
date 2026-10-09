package com.aloute.repository.report;

import com.aloute.model.report.Report;
import com.aloute.model.report.ReportStatus;
import com.aloute.model.report.ReportTargetType;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    boolean existsByReporterIdAndTargetTypeAndTargetIdAndStatus(UUID reporterId, ReportTargetType targetType,
                                                                UUID targetId, ReportStatus status);

    java.util.List<Report> findByReporterIdOrderByCreatedAtDesc(UUID reporterId);
    java.util.Optional<Report> findByIdAndReporterId(UUID id, UUID reporterId);
}
