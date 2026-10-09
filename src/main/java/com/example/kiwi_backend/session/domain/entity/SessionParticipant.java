package com.example.kiwi_backend.session.domain.entity;

import com.example.kiwi_backend.contract.dto.Gender;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 세션별 참가자.
 * 테이블에 created_at이 없어 BaseTimeEntity를 상속하지 않는다. 신청 시각은 appliedAt으로 직접 기록한다.
 *
 * (session_id, member_id) 유니크라서, 취소 후 같은 세션에 다시 신청하면 새 행을 만들지 않고 이 행을 되살린다.
 */
@Entity
@Table(name = "session_participant")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionParticipant {

    /** 식별자 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 참가한 세션 */
    @Column(nullable = false, updatable = false)
    private Long sessionId;

    /** 참가한 회원 */
    @Column(nullable = false, updatable = false)
    private Long memberId;

    /** 성비 집계용 성별 스냅샷 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private Gender gender;

    /** 로테이션 위치 번호, 로비 마감 시 부여 (지금은 미사용) */
    private Integer seatNo;

    /** 세션 중 표시 이름, 신청 시 자동 부여. 재신청해도 유지 */
    @Column(nullable = false, updatable = false, length = 50)
    private String alias;

    /** 참가 상태. 필드명과 컬럼명이 달라 name 지정 */
    @Enumerated(EnumType.STRING)
    @Column(name = "participant_status", nullable = false, length = 20)
    private ParticipantStatus status;

    /** 투표 완료 시각 (vote 모듈에서 기록) */
    private Instant votedAt;

    /** 신청 시각. 재신청하면 새 시각으로 바뀌고, 참가비 멱등 키에 쓰인다 */
    @Column(nullable = false)
    private Instant appliedAt;

    /** 본인 취소 시각 */
    private Instant cancelledAt;

    /** 로비 입장 시각 (로비 처리는 A와 정한 뒤 구현) */
    private Instant enteredAt;

    /** 퇴장 시각 */
    private Instant leftAt;

    public static SessionParticipant apply(Long sessionId, Long memberId, Gender gender, String alias, Instant now) {
        if (sessionId == null || memberId == null || gender == null) {
            throw new IllegalArgumentException("세션, 회원, 성별은 필수입니다");
        }
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("별칭은 필수입니다");
        }
        SessionParticipant participant = new SessionParticipant();
        participant.sessionId = sessionId;
        participant.memberId = memberId;
        participant.gender = gender;
        participant.alias = alias;
        participant.status = ParticipantStatus.APPLIED;
        participant.appliedAt = now;
        return participant;
    }

    /** 취소했던 세션에 다시 신청 */
    public void reapply(Instant now) {
        if (status != ParticipantStatus.CANCELLED) {
            throw new IllegalStateException("취소한 신청만 다시 신청할 수 있습니다");
        }
        this.status = ParticipantStatus.APPLIED;
        this.appliedAt = now;
        this.cancelledAt = null;
    }

    /** 본인 취소 */
    public void cancel(Instant now) {
        if (status != ParticipantStatus.APPLIED) {
            throw new IllegalStateException("신청 상태에서만 취소할 수 있습니다");
        }
        this.status = ParticipantStatus.CANCELLED;
        this.cancelledAt = now;
    }

    /** 신청했거나 출석한 유효한 참가자인가 */
    public boolean isActive() {
        return status == ParticipantStatus.APPLIED || status == ParticipantStatus.ATTENDED;
    }
}
