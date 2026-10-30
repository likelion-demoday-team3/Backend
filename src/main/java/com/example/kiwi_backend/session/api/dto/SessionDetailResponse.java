package com.example.kiwi_backend.session.api.dto;

import com.example.kiwi_backend.session.application.support.SeatCount;
import com.example.kiwi_backend.session.domain.entity.DatingSession;
import com.example.kiwi_backend.session.domain.entity.LiveStatus;
import com.example.kiwi_backend.session.domain.entity.ParticipantStatus;
import com.example.kiwi_backend.session.domain.entity.SessionParticipant;
import com.example.kiwi_backend.session.domain.entity.SessionStatus;

import java.time.Instant;

/**
 * 세션 상세. 내가 신청했는지(myStatus)와 내 별칭(myAlias)을 함께 내려준다.
 * 신청하지 않았으면 둘 다 null.
 * liveStatus·currentRoundNumber·phaseEndsAt은 새로고침·재접속 때 화면을 되살리는 데 쓴다
 * (예: VOTING이면 phaseEndsAt이 투표 마감 시각).
 */
public record SessionDetailResponse(
        Long sessionId,
        Instant startTime,
        Instant applyDeadline,
        int capacityPerGender,
        int maleRemaining,
        int femaleRemaining,
        int roundDurationSeconds,
        int votingDurationSeconds,
        int resultAndShareDurationSeconds,
        int entryFeeGem,
        int completionRefundGem,
        SessionStatus status,
        ParticipantStatus myStatus,
        String myAlias,
        LiveStatus liveStatus,
        int currentRoundNumber,
        Instant phaseEndsAt
) {
    public static SessionDetailResponse of(DatingSession session, SeatCount seats, SessionParticipant me) {
        return new SessionDetailResponse(
                session.getId(),
                session.getStartTime(),
                session.applyDeadline(),
                session.getCapacityPerGender(),
                seats.maleRemaining(),
                seats.femaleRemaining(),
                session.getRoundDurationSeconds(),
                session.getVotingDurationSeconds(),
                session.getResultAndShareDurationSeconds(),
                session.getEntryFeeGem(),
                session.getCompletionRefundGem(),
                session.getStatus(),
                me == null ? null : me.getStatus(),
                me == null ? null : me.getAlias(),
                session.getLiveStatus(),
                session.getCurrentRoundNumber(),
                session.getPhaseEndsAt()
        );
    }
}
