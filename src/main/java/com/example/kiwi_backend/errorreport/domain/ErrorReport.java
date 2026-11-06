package com.example.kiwi_backend.errorreport.domain;

import com.example.kiwi_backend.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "error_report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ErrorReport extends BaseTimeEntity {

    public static final int MAX_MESSAGE = 2000;
    public static final int MAX_URL = 500;
    public static final int MAX_USER_AGENT = 500;
    public static final int MAX_DETAIL = 8000;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private ErrorReportKind kind;

    /** 제보한 회원. 로그인한 상태에서만 받으므로 지금은 항상 있다 */
    @Column(updatable = false)
    private Long memberId;

    @Column(nullable = false, updatable = false, length = MAX_MESSAGE)
    private String message;

    @Column(updatable = false, length = MAX_URL)
    private String pageUrl;

    @Column(updatable = false, length = MAX_USER_AGENT)
    private String userAgent;

    /** 스택, 최근 자동 수집 에러 등 추가 정보 */
    @Column(updatable = false, columnDefinition = "TEXT")
    private String detail;

    @Column(updatable = false, length = 64)
    private String fingerprint;

    @Column(nullable = false)
    private int occurrenceCount;

    @Column(nullable = false)
    private Instant lastOccurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_report_status", nullable = false, length = 20)
    private ErrorReportStatus status;

    private Instant resolvedAt;
    private Long resolvedBy;

    public static ErrorReport create(ErrorReportKind kind, Long memberId, String message, String pageUrl,
                                     String userAgent, String detail, String fingerprint, Instant now) {
        if (kind == null || now == null) {
            throw new IllegalArgumentException("제보 종류와 시각은 필수입니다");
        }
        String trimmedMessage = clip(message, MAX_MESSAGE);
        if (trimmedMessage == null) {
            throw new IllegalArgumentException("어떤 문제가 있었는지 적어주세요");
        }
        ErrorReport report = new ErrorReport();
        report.kind = kind;
        report.memberId = memberId;
        report.message = trimmedMessage;
        report.pageUrl = clip(pageUrl, MAX_URL);
        report.userAgent = clip(userAgent, MAX_USER_AGENT);
        report.detail = clip(detail, MAX_DETAIL);
        report.fingerprint = fingerprint;
        report.occurrenceCount = 1;
        report.lastOccurredAt = now;
        report.status = ErrorReportStatus.OPEN;
        return report;
    }

    /** 같은 자동 에러가 다시 남 */
    public void occurredAgain(Instant now) {
        this.occurrenceCount++;
        this.lastOccurredAt = now;
    }

    /** 해결 처리. 이미 해결된 제보는 그대로 둔다 */
    public void resolve(Long adminId, Instant now) {
        if (status == ErrorReportStatus.RESOLVED) {
            return;
        }
        this.status = ErrorReportStatus.RESOLVED;
        this.resolvedBy = adminId;
        this.resolvedAt = now;
    }

    /** 앞뒤 공백을 지우고 최대 길이로 자른다. 비어 있으면 null */
    static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
