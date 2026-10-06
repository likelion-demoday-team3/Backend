package com.example.kiwi_backend.live.application;

public record LiveRoundBroadcastResult(
        Long sessionId,
        int roundNumber,
        int roomCount,
        int recipientCount
) {
}
