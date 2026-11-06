package com.example.kiwi_backend.errorreport.application;

import com.example.kiwi_backend.common.exception.NotFoundException;
import com.example.kiwi_backend.contract.dto.MemberAdminSummary;
import com.example.kiwi_backend.contract.query.MemberQueryService;
import com.example.kiwi_backend.errorreport.domain.ErrorReport;
import com.example.kiwi_backend.errorreport.domain.ErrorReportKind;
import com.example.kiwi_backend.errorreport.domain.ErrorReportRepository;
import com.example.kiwi_backend.errorreport.domain.ErrorReportStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 운영자의 오류 제보 확인·해결 처리 */
@Service
@RequiredArgsConstructor
public class AdminErrorReportService {

    private final ErrorReportRepository errorReports;
    private final MemberQueryService members;
    private final Clock clock;

    /** 최근 발생순 최대 200개. status·kind가 없으면 전체 */
    @Transactional(readOnly = true)
    public List<ErrorReportView> list(ErrorReportStatus status, ErrorReportKind kind) {
        List<ErrorReport> found = status == null
                ? errorReports.findTop200ByOrderByLastOccurredAtDesc()
                : errorReports.findTop200ByStatusOrderByLastOccurredAtDesc(status);
        if (kind != null) {
            found = found.stream().filter(report -> report.getKind() == kind).toList();
        }
        if (found.isEmpty()) {
            return List.of();
        }
        Map<Long, MemberAdminSummary> summaries = members.getAdminSummaries(found.stream()
                .map(ErrorReport::getMemberId).filter(Objects::nonNull).collect(Collectors.toSet()));
        return found.stream().map(report -> ErrorReportView.of(report,
                report.getMemberId() == null ? null : summaries.get(report.getMemberId()))).toList();
    }

    @Transactional
    public void resolve(Long errorReportId, Long adminId) {
        ErrorReport report = errorReports.findById(errorReportId)
                .orElseThrow(() -> new NotFoundException("오류 제보를 찾을 수 없습니다"));
        report.resolve(adminId, clock.instant());
    }

    public record Reporter(Long memberId, String name, String email) { }

    public record ErrorReportView(
            Long id,
            ErrorReportKind kind,
            ErrorReportStatus status,
            String message,
            String pageUrl,
            String userAgent,
            String detail,
            int occurrenceCount,
            Reporter reporter,
            Instant createdAt,
            Instant lastOccurredAt,
            Instant resolvedAt
    ) {
        static ErrorReportView of(ErrorReport report, MemberAdminSummary summary) {
            Reporter reporter = report.getMemberId() == null ? null : new Reporter(report.getMemberId(),
                    summary == null ? null : summary.name(), summary == null ? null : summary.email());
            return new ErrorReportView(report.getId(), report.getKind(), report.getStatus(), report.getMessage(),
                    report.getPageUrl(), report.getUserAgent(), report.getDetail(), report.getOccurrenceCount(),
                    reporter, report.getCreatedAt(), report.getLastOccurredAt(), report.getResolvedAt());
        }
    }
}
