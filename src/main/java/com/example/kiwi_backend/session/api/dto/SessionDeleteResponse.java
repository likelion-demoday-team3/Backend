package com.example.kiwi_backend.session.api.dto;

/**
 * 운영자 삭제 결과.
 * DELETED: 신청 기록이 없어 세션을 지움. CANCELLED: 신청 기록이 있어 전원 100% 환불 후 취소 (목록에서는 사라진다)
 */
public record SessionDeleteResponse(
        Long sessionId,
        Result result,
        int refundedParticipants
) {

    public enum Result {
        DELETED,
        CANCELLED
    }

    public static SessionDeleteResponse deleted(Long sessionId) {
        return new SessionDeleteResponse(sessionId, Result.DELETED, 0);
    }

    public static SessionDeleteResponse cancelled(Long sessionId, int refundedParticipants) {
        return new SessionDeleteResponse(sessionId, Result.CANCELLED, refundedParticipants);
    }
}
