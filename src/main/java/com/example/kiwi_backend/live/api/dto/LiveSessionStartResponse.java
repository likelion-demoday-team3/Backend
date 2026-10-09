package com.example.kiwi_backend.live.api.dto;

import com.example.kiwi_backend.live.application.LiveSessionStartResult;

public record LiveSessionStartResponse(
        Long sessionId,
        int participantCount,
        int roundCount,
        int roomCount,
        int idleCount,
        int firstRoundNumber,
        int roundDurationSeconds
) {

    public static LiveSessionStartResponse from(LiveSessionStartResult result) {
        return new LiveSessionStartResponse(
                result.sessionId(),
                result.participantCount(),
                result.roundCount(),
                result.roomCount(),
                result.idleCount(),
                result.firstRoundNumber(),
                result.roundDurationSeconds()
        );
    }
}
