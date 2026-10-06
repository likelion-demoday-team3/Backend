package com.example.kiwi_backend.live.domain.entity;

import com.example.kiwi_backend.common.entity.BaseTimeEntity;
import com.example.kiwi_backend.live.domain.enumerated.LiveRoundStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
        name = "live_round",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_live_round_session_round", columnNames = {"session_id", "round_number"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveRound extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private Long sessionId;

    @Column(name = "round_number", nullable = false, updatable = false)
    private int roundNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "round_status", nullable = false, length = 20)
    private LiveRoundStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    public static LiveRound create(Long sessionId, int roundNumber) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }
        if (roundNumber < 1) {
            throw new IllegalArgumentException("roundNumber must be positive");
        }

        LiveRound round = new LiveRound();
        round.sessionId = sessionId;
        round.roundNumber = roundNumber;
        round.status = LiveRoundStatus.PENDING;
        return round;
    }

    public void start(Instant startedAt) {
        if (status != LiveRoundStatus.PENDING) {
            throw new IllegalStateException("pending round만 시작할 수 있습니다");
        }
        this.status = LiveRoundStatus.IN_PROGRESS;
        this.startedAt = requireInstant(startedAt, "startedAt");
    }

    public void complete(Instant endedAt) {
        if (status != LiveRoundStatus.IN_PROGRESS) {
            throw new IllegalStateException("진행 중인 round만 종료할 수 있습니다");
        }
        Instant end = requireInstant(endedAt, "endedAt");
        if (startedAt != null && end.isBefore(startedAt)) {
            throw new IllegalArgumentException("endedAt must not be before startedAt");
        }
        this.status = LiveRoundStatus.COMPLETED;
        this.endedAt = end;
    }

    private Instant requireInstant(Instant value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value;
    }
}
