package com.example.kiwi_backend.live.application;

public record LiveRoundSchedulerResult(
        Long sessionId,
        int firstRoundNumber,
        int roundDurationSeconds
) {
}
