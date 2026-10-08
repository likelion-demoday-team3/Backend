package com.example.kiwi_backend.session.api.dto;

/**
 * 신청 결과. 참가비가 0이면 balanceAfter는 null (보석 변동 없음).
 */
public record ApplyResponse(
        Long sessionId,
        String alias,
        int chargedGem,
        Integer balanceAfter
) {
}
