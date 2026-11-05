package com.example.kiwi_backend.report.domain;

import com.example.kiwi_backend.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "report", uniqueConstraints = @UniqueConstraint(name = "uk_report_pair",
        columnNames = {"session_id", "reporter_id", "reported_member_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, updatable = false) private Long sessionId;
    @Column(nullable = false, updatable = false) private Long reporterId;
    @Column(nullable = false, updatable = false) private Long reportedMemberId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40) private ReportReason reason;
    @Enumerated(EnumType.STRING)
    @Column(name = "report_status", nullable = false, length = 20) private ReportStatus status;
    private Instant resolvedAt;
    private Long resolvedBy;

    public static Report create(Long sessionId, Long reporterId, Long reportedMemberId, ReportReason reason) {
        if (sessionId == null || reporterId == null || reportedMemberId == null || reason == null) {
            throw new IllegalArgumentException("세션, 신고자, 신고 대상, 사유는 필수입니다");
        }
        if (reporterId.equals(reportedMemberId)) {
            throw new IllegalArgumentException("자기 자신은 신고할 수 없어요");
        }
        Report report = new Report();
        report.sessionId = sessionId;
        report.reporterId = reporterId;
        report.reportedMemberId = reportedMemberId;
        report.reason = reason;
        report.status = ReportStatus.PENDING;
        return report;
    }

    /** 이용 정지로 처리. 이미 처리된 신고는 그대로 둔다 */
    public void markActioned(Long adminId, Instant now) {
        resolve(ReportStatus.ACTIONED, adminId, now);
    }

    /** 기각. 확인 전 신고만 기각할 수 있다 */
    public void dismiss(Long adminId, Instant now) {
        if (status != ReportStatus.PENDING) {
            throw new IllegalStateException("이미 처리된 신고입니다");
        }
        resolve(ReportStatus.DISMISSED, adminId, now);
    }

    private void resolve(ReportStatus next, Long adminId, Instant now) {
        if (status != ReportStatus.PENDING) {
            return;
        }
        this.status = next;
        this.resolvedBy = adminId;
        this.resolvedAt = now;
    }
}
