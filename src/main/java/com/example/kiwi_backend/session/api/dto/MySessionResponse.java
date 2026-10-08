package com.example.kiwi_backend.session.api.dto;

import com.example.kiwi_backend.session.domain.entity.ParticipantStatus;
import com.example.kiwi_backend.session.domain.entity.SessionStatus;

import java.time.Instant;

/** 내가 신청했던 세션 목록 한 줄 */
public record MySessionResponse(
        Long sessionId,
        Instant startTime,
        SessionStatus sessionStatus,
        ParticipantStatus myStatus,
        String alias,
        Instant appliedAt
) {
}
