package com.example.kiwi_backend.live.application;

public record LiveRoundAdvanceResult(
        Long sessionId,
        int completedRoundNumber,
        boolean nextRoundStarted,
        Integer nextRoundNumber
) {
}
