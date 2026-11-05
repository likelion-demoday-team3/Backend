package com.example.kiwi_backend.report.application;

import com.example.kiwi_backend.common.notification.NotificationEvent;
import com.example.kiwi_backend.common.notification.NotificationType;
import com.example.kiwi_backend.contract.dto.LiveParticipant;
import com.example.kiwi_backend.contract.query.LiveEncounterQuery;
import com.example.kiwi_backend.contract.query.MemberQueryService;
import com.example.kiwi_backend.contract.query.SessionQueryService;
import com.example.kiwi_backend.report.domain.Report;
import com.example.kiwi_backend.report.domain.ReportReason;
import com.example.kiwi_backend.report.domain.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** 세션 중 신고 접수. 접수되면 운영자 전원에게 알림을 남긴다 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    /** 운영자 알림을 눌렀을 때 이동할 프론트 경로 */
    public static final String ADMIN_REPORTS_LINK = "/admin/reports";

    private final ReportRepository reports;
    private final LiveEncounterQuery encounters;
    private final MemberQueryService members;
    private final SessionQueryService sessions;
    private final ApplicationEventPublisher events;

    @Transactional
    public Receipt submit(Long sessionId, Long reporterId, Long reportedMemberId, ReportReason reason) {
        if (reason == null) {
            throw new IllegalArgumentException("신고 사유를 선택해주세요");
        }
        if (Objects.equals(reporterId, reportedMemberId)) {
            throw new IllegalArgumentException("자기 자신은 신고할 수 없어요");
        }
        // 같은 방에서 대화한 상대만 신고할 수 있다 (지금 대화 중인 라운드 포함)
        if (!encounters.haveMet(sessionId, reporterId, reportedMemberId)) {
            throw new IllegalArgumentException("대화한 상대만 신고할 수 있어요");
        }
        var existing = reports.findBySessionIdAndReporterIdAndReportedMemberId(sessionId, reporterId, reportedMemberId);
        if (existing.isPresent()) {
            return new Receipt(existing.get().getId(), true);
        }

        Report report = reports.saveAndFlush(Report.create(sessionId, reporterId, reportedMemberId, reason));
        notifyAdmins(report);
        log.info("신고 접수 reportId={} sessionId={} reporter={} reported={} reason={}",
                report.getId(), sessionId, reporterId, reportedMemberId, reason);
        return new Receipt(report.getId(), false);
    }

    private void notifyAdmins(Report report) {
        String reportedAlias = aliasOf(report.getSessionId(), report.getReportedMemberId());
        String body = "세션 #" + report.getSessionId() + "에서 " + reportedAlias + " 님이 신고됐어요. 사유: "
                + report.getReason().label();
        for (Long adminId : members.findActiveAdminIds()) {
            events.publishEvent(new NotificationEvent(adminId, NotificationType.REPORT_SUBMITTED,
                    "새 신고가 접수됐어요", body, report.getSessionId(), ADMIN_REPORTS_LINK,
                    "REPORT_SUBMITTED:r" + report.getId() + ":a" + adminId));
        }
    }

    private String aliasOf(Long sessionId, Long memberId) {
        List<LiveParticipant> participants = sessions.getLiveParticipants(sessionId);
        if (participants == null) {
            return "참가자";
        }
        return participants.stream()
                .filter(participant -> memberId.equals(participant.memberId()))
                .map(LiveParticipant::alias)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("참가자");
    }

    /** alreadyReported: 같은 세션에서 같은 상대를 이미 신고했으면 새로 만들지 않고 기존 신고를 돌려준다 */
    public record Receipt(Long reportId, boolean alreadyReported) { }
}
