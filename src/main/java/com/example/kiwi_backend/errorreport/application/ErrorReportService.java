package com.example.kiwi_backend.errorreport.application;

import com.example.kiwi_backend.common.notification.NotificationEvent;
import com.example.kiwi_backend.common.notification.NotificationType;
import com.example.kiwi_backend.contract.query.MemberQueryService;
import com.example.kiwi_backend.errorreport.domain.ErrorReport;
import com.example.kiwi_backend.errorreport.domain.ErrorReportKind;
import com.example.kiwi_backend.errorreport.domain.ErrorReportRepository;
import com.example.kiwi_backend.errorreport.domain.ErrorReportStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * 오류 제보 접수.
 *
 * USER(직접 제보)는 매번 새로 만들고 운영자 전원에게 알린다.
 * AUTO(자동 수집)는 같은 에러(fingerprint)의 확인 전 제보가 있으면 횟수만 올리고, 처음 생긴 에러만 알린다.
 * 한 회원이 1시간에 MAX_PER_HOUR개 넘게 새로 만들지 못하게 막는다 (자동 수집이 폭주해도 DB·알림함이 넘치지 않게).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErrorReportService {

    /** 운영자 알림을 눌렀을 때 이동할 프론트 경로 */
    public static final String ADMIN_ERROR_REPORTS_LINK = "/admin/error-reports";
    static final int MAX_PER_HOUR = 20;
    /** 비회원 제보는 누구나 보낼 수 있으니 전체 합계로 막는다 (운영자 알림함이 넘치지 않게) */
    static final int GUEST_MAX_PER_HOUR = 30;

    private final ErrorReportRepository errorReports;
    private final MemberQueryService members;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public Receipt submit(Long memberId, Submission submission) {
        if (submission == null || submission.kind() == null) {
            throw new IllegalArgumentException("제보 종류가 필요합니다");
        }
        Instant now = clock.instant();

        String fingerprint = null;
        if (submission.kind() == ErrorReportKind.AUTO) {
            fingerprint = fingerprint(submission);
            var existing = errorReports.findFirstByKindAndFingerprintAndStatus(
                    ErrorReportKind.AUTO, fingerprint, ErrorReportStatus.OPEN);
            if (existing.isPresent()) {
                existing.get().occurredAgain(now);
                return new Receipt(existing.get().getId(), true);
            }
        }

        if (errorReports.countByMemberIdAndCreatedAtAfter(memberId, now.minus(Duration.ofHours(1))) >= MAX_PER_HOUR) {
            if (submission.kind() == ErrorReportKind.AUTO) {
                // 자동 수집은 조용히 버린다 (사용자 화면에는 아무 영향 없음)
                return new Receipt(null, false);
            }
            throw new IllegalStateException("제보가 너무 많아요. 잠시 후 다시 보내주세요");
        }

        return save(memberId, submission.kind(), submission.message(), submission.pageUrl(),
                submission.userAgent(), submission.detail(), fingerprint, now);
    }

    /**
     * 로그인하지 않은 사람의 직접 제보 (예: 학교 메일로 가입이 안 될 때).
     * 답변 받을 이메일을 제보 내용에 함께 남긴다. 비회원 제보 전체를 1시간에 GUEST_MAX_PER_HOUR개로 막는다.
     */
    @Transactional
    public Receipt submitAsGuest(GuestSubmission submission) {
        if (submission == null || submission.contactEmail() == null || submission.contactEmail().isBlank()) {
            throw new IllegalArgumentException("답변 받을 이메일이 필요합니다");
        }
        if (submission.message() == null || submission.message().isBlank()) {
            throw new IllegalArgumentException("어떤 문제가 있었는지 적어주세요");
        }
        Instant now = clock.instant();
        if (errorReports.countByMemberIdIsNullAndCreatedAtAfter(now.minus(Duration.ofHours(1))) >= GUEST_MAX_PER_HOUR) {
            throw new IllegalStateException("지금은 제보가 많아요. 잠시 후 다시 보내주세요");
        }
        String contact = submission.contactEmail().strip();
        String detail = "답변 받을 이메일: " + contact
                + (submission.detail() == null || submission.detail().isBlank() ? "" : "\n" + submission.detail().strip());
        return save(null, ErrorReportKind.USER, "[비회원] " + submission.message().strip(), submission.pageUrl(),
                submission.userAgent(), detail, null, now);
    }

    private Receipt save(Long memberId, ErrorReportKind kind, String message, String pageUrl, String userAgent,
                         String detail, String fingerprint, Instant now) {
        ErrorReport report = errorReports.saveAndFlush(ErrorReport.create(kind, memberId,
                message, pageUrl, userAgent, detail, fingerprint, now));
        notifyAdmins(report);
        log.info("오류 제보 접수 id={} kind={} member={} page={}",
                report.getId(), report.getKind(), memberId, report.getPageUrl());
        return new Receipt(report.getId(), false);
    }

    private void notifyAdmins(ErrorReport report) {
        boolean byUser = report.getKind() == ErrorReportKind.USER;
        String title = byUser ? "새 오류 제보가 들어왔어요" : "새 오류가 자동으로 수집됐어요";
        String body = preview(report.getMessage());
        for (Long adminId : members.findActiveAdminIds()) {
            events.publishEvent(new NotificationEvent(adminId, NotificationType.ERROR_REPORTED, title, body,
                    null, ADMIN_ERROR_REPORTS_LINK, "ERROR_REPORTED:e" + report.getId() + ":a" + adminId));
        }
    }

    private static String preview(String message) {
        String oneLine = message.replaceAll("\\s+", " ");
        return oneLine.length() <= 80 ? oneLine : oneLine.substring(0, 80) + "…";
    }

    /** 같은 에러를 묶는 키: 메시지 + 스택 첫 줄 + 화면 경로(쿼리 제외) */
    static String fingerprint(Submission submission) {
        String message = head(submission.message(), 300);
        String firstDetailLine = submission.detail() == null ? "" : head(submission.detail().strip().split("\\R", 2)[0], 300);
        String key = message + "|" + firstDetailLine + "|" + pathOf(submission.pageUrl());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String head(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.strip();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static String pathOf(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            String path = URI.create(url.strip()).getPath();
            return path == null ? "" : path;
        } catch (IllegalArgumentException e) {
            return head(url, 200);
        }
    }

    public record Submission(ErrorReportKind kind, String message, String pageUrl, String userAgent, String detail) { }

    public record GuestSubmission(String contactEmail, String message, String pageUrl, String userAgent, String detail) { }

    /**
     * id: 저장(또는 묶인) 제보 id. 자동 수집이 너무 많아 버려졌으면 null.
     * merged: 같은 자동 에러의 기존 제보에 횟수만 더했으면 true
     */
    public record Receipt(Long id, boolean merged) { }
}
