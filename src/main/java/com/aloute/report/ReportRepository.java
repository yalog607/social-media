package com.aloute.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    boolean existsByReporterIdAndTargetTypeAndTargetIdAndStatus(UUID reporterId, ReportTargetType targetType,
                                                                UUID targetId, ReportStatus status);
}
