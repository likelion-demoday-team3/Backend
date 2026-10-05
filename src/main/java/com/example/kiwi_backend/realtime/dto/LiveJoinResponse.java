package com.example.kiwi_backend.realtime.dto;

public record LiveJoinResponse(
        Long sessionId,
        Long memberId,
        String status
) {
}
