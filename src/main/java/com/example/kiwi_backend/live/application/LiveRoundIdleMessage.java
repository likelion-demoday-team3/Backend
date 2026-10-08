package com.example.kiwi_backend.live.application;

public record LiveRoundIdleMessage(
        Long sessionId,
        Long roundId,
        int roundNumber,
        Long memberId,
        int breakSeconds
) {
}
