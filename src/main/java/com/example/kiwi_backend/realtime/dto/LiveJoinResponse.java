package com.example.kiwi_backend.realtime.dto;

public record LiveJoinResponse(
        Long sessionId,
        Long memberId,
        LiveJoinStatus status,
        Long roundId,
        Integer roundNumber,
        Long roomId,
        Integer tableNo,
        Long partnerMemberId
) {
}
