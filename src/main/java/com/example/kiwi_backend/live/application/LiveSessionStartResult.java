package com.example.kiwi_backend.live.application;

public record LiveSessionStartResult(
        Long sessionId,
        int participantCount,
        int roundCount,
        int roomCount,
        int idleCount,
        int firstRoundNumber,
        int roundDurationSeconds
) {
}
