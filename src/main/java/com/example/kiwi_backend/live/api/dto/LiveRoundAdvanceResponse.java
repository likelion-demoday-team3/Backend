package com.example.kiwi_backend.live.api.dto;

import com.example.kiwi_backend.live.application.LiveRoundAdvanceResult;
import com.example.kiwi_backend.live.application.LiveRoundPhase;

public record LiveRoundAdvanceResponse(
        Long sessionId,
        int completedRoundNumber,
        boolean nextRoundExists,
        Integer nextRoundNumber,
        LiveRoundPhase phase,
        int breakSeconds
) {
    public static LiveRoundAdvanceResponse from(LiveRoundAdvanceResult result) {
        return new LiveRoundAdvanceResponse(
                result.sessionId(),
                result.completedRoundNumber(),
                result.nextRoundExists(),
                result.nextRoundNumber(),
                result.phase(),
                result.breakSeconds()
        );
    }
}
