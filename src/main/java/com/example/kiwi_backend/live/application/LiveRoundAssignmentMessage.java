package com.example.kiwi_backend.live.application;

public record LiveRoundAssignmentMessage(
        Long sessionId,
        Long roundId,
        int roundNumber,
        Long roomId,
        int tableNo,
        Long memberId,
        Long partnerMemberId
) {
}
