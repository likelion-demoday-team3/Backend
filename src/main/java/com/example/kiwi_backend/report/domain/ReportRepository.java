package com.example.kiwi_backend.report.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    Optional<Report> findBySessionIdAndReporterIdAndReportedMemberId(Long sessionId, Long reporterId,
                                                                    Long reportedMemberId);

    List<Report> findAllByOrderByCreatedAtDesc();

    List<Report> findAllByStatusOrderByCreatedAtDesc(ReportStatus status);

    List<Report> findAllByReportedMemberIdAndStatus(Long reportedMemberId, ReportStatus status);

    /** 회원별 누적 신고 수 (운영자 화면 표시용) */
    @Query("""
            select r.reportedMemberId, count(r)
            from Report r
            where r.reportedMemberId in :memberIds
            group by r.reportedMemberId
            """)
    List<Object[]> countByReportedMemberIds(@Param("memberIds") Collection<Long> memberIds);
}
