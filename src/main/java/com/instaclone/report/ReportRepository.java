package com.instaclone.report;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, ReportTargetType targetType, Long targetId);

    @Query(
            "select r.targetId from Report r where r.reporter.id = :reporterId and r.targetType = "
                    + "com.instaclone.report.ReportTargetType.POST")
    List<Long> findReportedPostIds(@Param("reporterId") Long reporterId);
}
