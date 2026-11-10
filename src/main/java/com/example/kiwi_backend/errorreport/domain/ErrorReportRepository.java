package com.example.kiwi_backend.errorreport.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ErrorReportRepository extends JpaRepository<ErrorReport, Long> {

    /** 같은 자동 에러의 확인 전 제보 */
    Optional<ErrorReport> findFirstByKindAndFingerprintAndStatus(ErrorReportKind kind, String fingerprint,
                                                                 ErrorReportStatus status);

    /** 회원이 since 이후 새로 만든 제보 수 */
    long countByMemberIdAndCreatedAtAfter(Long memberId, Instant since);

    /** 비회원(로그인하지 않은 사람) 제보 수 */
    long countByMemberIdIsNullAndCreatedAtAfter(Instant since);

    List<ErrorReport> findTop200ByOrderByLastOccurredAtDesc();

    List<ErrorReport> findTop200ByStatusOrderByLastOccurredAtDesc(ErrorReportStatus status);
}
