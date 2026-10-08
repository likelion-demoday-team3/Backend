package com.example.kiwi_backend.session.api.dto;

import com.example.kiwi_backend.session.application.support.SeatCount;
import com.example.kiwi_backend.session.domain.entity.DatingSession;
import com.example.kiwi_backend.session.domain.entity.SessionStatus;

import java.time.Instant;

/** 세션 목록 한 줄. 시각은 UTC로 내려가고, 화면 표시는 프론트가 한국 시간으로 바꾼다 */
public record SessionSummaryResponse(
        Long sessionId,
        Instant startTime,
        Instant applyDeadline,
        int capacityPerGender,
        int maleRemaining,
        int femaleRemaining,
        int entryFeeGem,
        int completionRefundGem,
        SessionStatus status
) {
    public static SessionSummaryResponse of(DatingSession session, SeatCount seats) {
        return new SessionSummaryResponse(
                session.getId(),
                session.getStartTime(),
                session.applyDeadline(),
                session.getCapacityPerGender(),
                seats.maleRemaining(),
                seats.femaleRemaining(),
                session.getEntryFeeGem(),
                session.getCompletionRefundGem(),
                session.getStatus()
        );
    }
}
