package com.example.kiwi_backend.session.api.dto;

/** 본인 취소 결과. 시각에 따라 100% / 50% / 0% 환불 */
public record CancelResponse(
        Long sessionId,
        int refundedGem
) {
}
