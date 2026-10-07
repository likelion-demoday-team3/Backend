package com.example.kiwi_backend.live.application;

public record LiveRoundAdvanceResult(
        Long sessionId,
        int completedRoundNumber,
        boolean nextRoundExists,
        Integer nextRoundNumber,
        LiveRoundPhase phase,
        int breakSeconds
) {
}
