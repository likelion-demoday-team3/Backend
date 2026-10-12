package com.example.kiwi_backend.session.domain.entity;

import com.example.kiwi_backend.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/**
 * 소개팅 세션 하나의 설정과 진행 상태.
 * 상태는 허용된 전환만 가능하고, 모집이 시작되면 가격과 설정을 바꿀 수 없다.
 */
@Entity
@Table(name = "dating_session")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DatingSession extends BaseTimeEntity {

    // ===== 시간 규칙 (한곳에 모아둠. 바꿀 땐 여기만) =====

    /** 신청 마감 = 1차 진행 판단 = 시작 30분 전 */
    public static final Duration APPLY_CLOSE_BEFORE = Duration.ofMinutes(30);

    /** 로비 오픈 = 시작 5분 전 */
    public static final Duration LOBBY_OPEN_BEFORE = Duration.ofMinutes(5);

    /** 로비 마감 = 출석 확정 = 시작 1분 후 */
    public static final Duration LOBBY_CLOSE_AFTER = Duration.ofMinutes(1);

    /** 1라운드 시작 = 시작 1분 30초 후 (로비 마감 후 30초 준비 시간) */
    public static final Duration FIRST_ROUND_AFTER = Duration.ofSeconds(90);

    static final int MIN_CAPACITY = 3;
    static final int MAX_CAPACITY = 6;

    /** 식별자 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 세션 시작 시각. 신청 마감과 환불 기준이 여기서 계산된다 */
    @Column(nullable = false)
    private Instant startTime;

    /** 성별별 정원 3~6 (방 크기 3:3~6:6) */
    @Column(nullable = false)
    private int capacityPerGender;

    /** 라운드당 대화 시간 */
    @Column(nullable = false)
    private int roundDurationSeconds;

    /** 투표 시간 */
    @Column(nullable = false)
    private int votingDurationSeconds;

    /** 결과 확인·연락처 공유 시간 */
    @Column(nullable = false)
    private int resultAndShareDurationSeconds;

    /** 신청 시 차감할 참가비 */
    @Column(nullable = false)
    private int entryFeeGem;

    /** 투표 완료 시 반환할 보석 */
    @Column(nullable = false)
    private int completionRefundGem;

    /** 모집 단계. 필드명과 컬럼명이 달라 name 지정 */
    @Enumerated(EnumType.STRING)
    @Column(name = "session_status", nullable = false, length = 20)
    private SessionStatus status;

    /** 진행 단계. 변경 주체는 A와 정하는 중 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LiveStatus liveStatus;

    /** 현재 라운드, 0이면 시작 전 */
    @Column(nullable = false)
    private int currentRoundNumber;

    /** 현재 단계 마감 시각 */
    private Instant phaseEndsAt;

    // ===== 생성·수정 =====

    public static DatingSession create(Instant startTime,
                                       int capacityPerGender,
                                       int roundDurationSeconds,
                                       int votingDurationSeconds,
                                       int resultAndShareDurationSeconds,
                                       int entryFeeGem,
                                       int completionRefundGem,
                                       Instant now) {
        DatingSession session = new DatingSession();
        session.applySettings(startTime, capacityPerGender, roundDurationSeconds, votingDurationSeconds,
                resultAndShareDurationSeconds, entryFeeGem, completionRefundGem, now);
        session.status = SessionStatus.SCHEDULED;
        session.liveStatus = LiveStatus.WAITING;
        session.currentRoundNumber = 0;
        return session;
    }

    /** 모집 시작 전(SCHEDULED)에만 수정 가능. 신청자가 생긴 뒤 가격이 바뀌면 환불 기준이 틀어진다 */
    public void updateSettings(Instant startTime,
                               int capacityPerGender,
                               int roundDurationSeconds,
                               int votingDurationSeconds,
                               int resultAndShareDurationSeconds,
                               int entryFeeGem,
                               int completionRefundGem,
                               Instant now) {
        if (status != SessionStatus.SCHEDULED) {
            throw new IllegalStateException("모집이 시작된 세션은 수정할 수 없습니다");
        }
        applySettings(startTime, capacityPerGender, roundDurationSeconds, votingDurationSeconds,
                resultAndShareDurationSeconds, entryFeeGem, completionRefundGem, now);
    }

    // ===== 상태 전환 =====

    public void openRecruiting(Instant now) {
        if (!now.isBefore(applyDeadline())) {
            throw new IllegalStateException("신청 마감 시각이 지난 세션은 모집을 시작할 수 없습니다");
        }
        transitionTo(SessionStatus.RECRUITING);
    }

    public void markReady() {
        transitionTo(SessionStatus.READY);
    }

    /** 로비 마감 후 출석자로 진행 확정. 1라운드 시각을 기록해서 A가 그 시각에 시작하게 한다 */
    public void startProgress() {
        transitionTo(SessionStatus.IN_PROGRESS);
        this.phaseEndsAt = firstRoundAt();
    }

    /**
     * A가 1라운드를 시작했음을 기록. 이후 "1라운드 시작할 세션" 조회에 다시 잡히지 않는다.
     * 이미 기록됐으면 아무것도 하지 않는다 (두 번 불려도 안전).
     */
    public void markRoundsStarted() {
        if (status != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("진행 중인 세션만 라운드를 시작할 수 있습니다");
        }
        if (liveStatus == LiveStatus.IN_ROUND) {
            return;
        }
        this.liveStatus = LiveStatus.IN_ROUND;
        this.phaseEndsAt = null;
    }

    /**
     * 마지막 라운드가 끝나서 투표 시작. 투표 마감 시각을 기록하고 돌려준다.
     * 이미 투표 중이면 처음 정한 마감 시각을 그대로 돌려준다 (A의 타이머가 두 번 불러도 안전).
     */
    public Instant startVoting(Instant startedAt) {
        if (startedAt == null) {
            throw new IllegalArgumentException("투표 시작 시각은 필수입니다");
        }
        if (status != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("진행 중인 세션만 투표를 시작할 수 있습니다");
        }
        if (liveStatus == LiveStatus.VOTING) {
            return phaseEndsAt;
        }
        if (liveStatus != LiveStatus.IN_ROUND) {
            throw new IllegalStateException("라운드가 끝난 뒤에만 투표를 시작할 수 있습니다");
        }
        this.liveStatus = LiveStatus.VOTING;
        this.phaseEndsAt = startedAt.plusSeconds(votingDurationSeconds);
        return phaseEndsAt;
    }

    public void cancel() {
        transitionTo(SessionStatus.CANCELLED);
    }

    /** 운영자 취소 가능 여부: 로비 마감 전(모집 전·모집 중·로비 대기)까지만 */
    public boolean isAdminCancellable() {
        return status == SessionStatus.SCHEDULED
                || status == SessionStatus.RECRUITING
                || status == SessionStatus.READY;
    }

    public void vote() { transitionTo(SessionStatus.VOTING); }

    public void complete() {
        transitionTo(SessionStatus.COMPLETED);
    }

    private void transitionTo(SessionStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("세션 상태를 " + status + "에서 " + next + "(으)로 바꿀 수 없습니다");
        }
        this.status = next;
    }

    // ===== 조회용 규칙 =====

    /** 신청·취소 마감 시각 */
    public Instant applyDeadline() {
        return startTime.minus(APPLY_CLOSE_BEFORE);
    }

    /** 지금 신청·취소할 수 있는가 */
    public boolean isApplicationOpen(Instant now) {
        return status == SessionStatus.RECRUITING && now.isBefore(applyDeadline());
    }

    /** 30분 전 진행 판단을 할 차례인가 */
    public boolean isDueForDecision(Instant now) {
        return status == SessionStatus.RECRUITING && !now.isBefore(applyDeadline());
    }

    public Instant lobbyOpenAt() {
        return startTime.minus(LOBBY_OPEN_BEFORE);
    }

    public Instant lobbyCloseAt() {
        return startTime.plus(LOBBY_CLOSE_AFTER);
    }

    public Instant firstRoundAt() {
        return startTime.plus(FIRST_ROUND_AFTER);
    }

    /** 지금 로비에 들어올 수 있는가 (READY이고 오픈 ~ 마감 사이) */
    public boolean isLobbyOpen(Instant now) {
        return status == SessionStatus.READY
                && !now.isBefore(lobbyOpenAt())
                && now.isBefore(lobbyCloseAt());
    }

    /** 로비 마감(출석 확정)을 할 차례인가 */
    public boolean isDueForLobbyClose(Instant now) {
        return status == SessionStatus.READY && !now.isBefore(lobbyCloseAt());
    }

    // ===== 내부 =====

    private void applySettings(Instant startTime,
                               int capacityPerGender,
                               int roundDurationSeconds,
                               int votingDurationSeconds,
                               int resultAndShareDurationSeconds,
                               int entryFeeGem,
                               int completionRefundGem,
                               Instant now) {
        if (startTime == null || !startTime.isAfter(now)) {
            throw new IllegalArgumentException("시작 시각은 현재 이후여야 합니다");
        }
        if (capacityPerGender < MIN_CAPACITY || capacityPerGender > MAX_CAPACITY) {
            throw new IllegalArgumentException("성별 정원은 " + MIN_CAPACITY + "~" + MAX_CAPACITY + "명이어야 합니다");
        }
        if (roundDurationSeconds <= 0 || votingDurationSeconds <= 0 || resultAndShareDurationSeconds <= 0) {
            throw new IllegalArgumentException("진행 시간은 1초 이상이어야 합니다");
        }
        if (entryFeeGem < 0 || completionRefundGem < 0) {
            throw new IllegalArgumentException("보석 금액은 0 이상이어야 합니다");
        }
        if (completionRefundGem > entryFeeGem) {
            throw new IllegalArgumentException("완주 반환은 참가비보다 클 수 없습니다");
        }
        this.startTime = startTime;
        this.capacityPerGender = capacityPerGender;
        this.roundDurationSeconds = roundDurationSeconds;
        this.votingDurationSeconds = votingDurationSeconds;
        this.resultAndShareDurationSeconds = resultAndShareDurationSeconds;
        this.entryFeeGem = entryFeeGem;
        this.completionRefundGem = completionRefundGem;
    }
}
