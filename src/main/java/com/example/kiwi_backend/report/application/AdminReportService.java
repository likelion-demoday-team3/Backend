package com.example.kiwi_backend.report.application;

import com.example.kiwi_backend.common.exception.NotFoundException;
import com.example.kiwi_backend.common.notification.NotificationEvent;
import com.example.kiwi_backend.common.notification.NotificationType;
import com.example.kiwi_backend.contract.command.MemberAccountCommand;
import com.example.kiwi_backend.contract.command.SessionSuspensionCommand;
import com.example.kiwi_backend.contract.dto.LiveParticipant;
import com.example.kiwi_backend.contract.dto.MemberAdminSummary;
import com.example.kiwi_backend.contract.query.MemberQueryService;
import com.example.kiwi_backend.contract.query.SessionQueryService;
import com.example.kiwi_backend.report.domain.Report;
import com.example.kiwi_backend.report.domain.ReportReason;
import com.example.kiwi_backend.report.domain.ReportRepository;
import com.example.kiwi_backend.report.domain.ReportStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** 운영자의 신고 확인·이용 정지·기각 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminReportService {

    private final ReportRepository reports;
    private final MemberQueryService members;
    private final SessionQueryService sessions;
    private final MemberAccountCommand memberAccounts;
    private final SessionSuspensionCommand sessionSuspension;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** 신고 목록 (최신순). status가 없으면 전체 */
    @Transactional(readOnly = true)
    public List<ReportView> list(ReportStatus status) {
        List<Report> found = status == null
                ? reports.findAllByOrderByCreatedAtDesc()
                : reports.findAllByStatusOrderByCreatedAtDesc(status);
        if (found.isEmpty()) {
            return List.of();
        }

        Set<Long> memberIds = new HashSet<>();
        found.forEach(report -> {
            memberIds.add(report.getReporterId());
            memberIds.add(report.getReportedMemberId());
        });
        Map<Long, MemberAdminSummary> summaries = members.getAdminSummaries(memberIds);
        Map<Long, Long> reportCounts = new HashMap<>();
        for (Object[] row : reports.countByReportedMemberIds(
                found.stream().map(Report::getReportedMemberId).collect(Collectors.toSet()))) {
            reportCounts.put((Long) row[0], (Long) row[1]);
        }
        Map<Long, Map<Long, String>> aliasesBySession = new HashMap<>();
        Map<Long, Instant> startTimes = new HashMap<>();

        return found.stream().map(report -> {
            Map<Long, String> aliases = aliasesBySession.computeIfAbsent(report.getSessionId(), this::aliases);
            Instant startTime = startTimes.computeIfAbsent(report.getSessionId(), this::startTimeOrNull);
            return new ReportView(
                    report.getId(),
                    report.getSessionId(),
                    startTime,
                    person(report.getReporterId(), aliases, summaries),
                    person(report.getReportedMemberId(), aliases, summaries),
                    reportCounts.getOrDefault(report.getReportedMemberId(), 0L),
                    report.getReason(),
                    report.getReason().label(),
                    report.getStatus(),
                    report.getCreatedAt(),
                    report.getResolvedAt()
            );
        }).toList();
    }

    /**
     * 이용 정지. 새 세션 신청을 막고, 아직 시작하지 않은 신청은 취소한다
     * (참가비는 본인 취소와 같은 환불 규정: 24시간 전 100%, 12시간 전 50%, 그 이후 0%).
     * 이 회원에 대한 확인 전 신고는 모두 처리 완료(ACTIONED)로 바꾼다.
     */
    @Transactional
    public SuspendResult suspend(Long memberId, Long adminId) {
        if (Objects.equals(memberId, adminId)) {
            throw new IllegalStateException("자기 자신은 정지할 수 없습니다");
        }
        memberAccounts.suspend(memberId);
        SessionSuspensionCommand.Result cancellation = sessionSuspension.cancelUpcomingApplications(memberId);
        Instant now = clock.instant();
        List<Report> pending = reports.findAllByReportedMemberIdAndStatus(memberId, ReportStatus.PENDING);
        pending.forEach(report -> report.markActioned(adminId, now));

        String body = suspensionMessage(cancellation);
        events.publishEvent(new NotificationEvent(memberId, NotificationType.ACCOUNT_SUSPENDED,
                "이용이 제한됐어요", body, null, null,
                "ACCOUNT_SUSPENDED:m" + memberId + ":" + now.toEpochMilli()));
        log.info("이용 정지 memberId={} adminId={} cancelledApplications={} resolvedReports={}",
                memberId, adminId, cancellation.cancelled(), pending.size());
        return new SuspendResult(memberId, cancellation.cancelled(), cancellation.refundedGem(), pending.size());
    }

    private static String suspensionMessage(SessionSuspensionCommand.Result cancellation) {
        String message = "신고가 확인되어 새 세션 신청이 제한됐어요.";
        if (cancellation.cancelled() == 0) {
            return message;
        }
        message += " 신청해 둔 세션 " + cancellation.cancelled() + "개는 취소됐어요.";
        return cancellation.refundedGem() > 0
                ? message + " 환불 규정에 따라 보석 " + cancellation.refundedGem() + "개를 돌려드렸어요."
                : message + " 시작 12시간 전이 지나 환불되는 참가비는 없어요.";
    }

    /** 이용 정지 해제 */
    @Transactional
    public void reactivate(Long memberId, Long adminId) {
        memberAccounts.reactivate(memberId);
        Instant now = clock.instant();
        events.publishEvent(new NotificationEvent(memberId, NotificationType.ACCOUNT_REACTIVATED,
                "이용 제한이 풀렸어요", "이제 다시 세션을 신청할 수 있어요.", null, null,
                "ACCOUNT_REACTIVATED:m" + memberId + ":" + now.toEpochMilli()));
        log.info("이용 정지 해제 memberId={} adminId={}", memberId, adminId);
    }

    /** 신고 기각 (조치하지 않음) */
    @Transactional
    public void dismiss(Long reportId, Long adminId) {
        Report report = reports.findById(reportId)
                .orElseThrow(() -> new NotFoundException("신고를 찾을 수 없습니다"));
        report.dismiss(adminId, clock.instant());
    }

    private Map<Long, String> aliases(Long sessionId) {
        List<LiveParticipant> participants = sessions.getLiveParticipants(sessionId);
        if (participants == null) {
            return Map.of();
        }
        Map<Long, String> aliases = new HashMap<>();
        participants.forEach(participant -> {
            if (participant.memberId() != null && participant.alias() != null) {
                aliases.putIfAbsent(participant.memberId(), participant.alias());
            }
        });
        return aliases;
    }

    private Instant startTimeOrNull(Long sessionId) {
        try {
            return sessions.getStartTime(sessionId);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Person person(Long memberId, Map<Long, String> aliases, Map<Long, MemberAdminSummary> summaries) {
        MemberAdminSummary summary = summaries.get(memberId);
        return new Person(memberId, aliases.get(memberId),
                summary == null ? null : summary.name(),
                summary == null ? null : summary.email(),
                summary == null ? null : summary.status());
    }

    /** alias는 그 세션에서의 별칭, status는 ACTIVE·SUSPENDED·WITHDRAWN */
    public record Person(Long memberId, String alias, String name, String email, String status) { }

    public record ReportView(Long reportId, Long sessionId, Instant sessionStartTime, Person reporter,
                             Person reported, long reportedTotalCount, ReportReason reason, String reasonLabel,
                             ReportStatus status, Instant createdAt, Instant resolvedAt) { }

    public record SuspendResult(Long memberId, int cancelledApplications, int refundedGem, int resolvedReports) { }
}
