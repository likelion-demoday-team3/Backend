package com.example.kiwi_backend.live.api.dto;

import com.example.kiwi_backend.live.application.LiveRoundAdvanceResult;

public record LiveRoundAdvanceResponse(
        Long sessionId,
        int completedRoundNumber,
        boolean nextRoundStarted,
        Integer nextRoundNumber
) {
    public static LiveRoundAdvanceResponse from(LiveRoundAdvanceResult result) {
        return new LiveRoundAdvanceResponse(
                result.sessionId(),
                result.completedRoundNumber(),
                result.nextRoundStarted(),
                result.nextRoundNumber()
        );
    }
}
