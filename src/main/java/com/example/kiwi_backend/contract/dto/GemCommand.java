package com.example.kiwi_backend.contract.dto;

import java.time.Instant;

/**
 * 보석 변동 요청. 금액은 항상 양수로 넘기고, 증가/감소는 type이 정한다.
 *
 * idempotencyKey: 같은 의도의 요청은 항상 같은 키를 만들어야 한다.
 * 같은 키로 다시 요청하면 wallet은 아무것도 바꾸지 않고 처음 결과를 돌려준다.
 * 키를 직접 만들기보다 아래 정적 메서드를 쓰면 규칙이 한곳에 모인다.
 *
 * 환불액이 0이면(12시간 이내 취소 등) 요청 자체를 보내지 않는다. 0원 요청은 거부한다.
 */
public record GemCommand(
        Long memberId,
        GemTransactionType type,
        int amount,
        Long sessionId,
        Long paymentId,
        String memo,
        String idempotencyKey
) {

    private static final int MAX_KEY_LENGTH = 100;

    public GemCommand {
        if (memberId == null) {
            throw new IllegalArgumentException("회원 id는 필수입니다");
        }
        if (type == null) {
            throw new IllegalArgumentException("보석 변동 타입은 필수입니다");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("보석 변동량은 1 이상이어야 합니다");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("멱등 키는 1~" + MAX_KEY_LENGTH + "자여야 합니다");
        }
        if (sessionId != null && paymentId != null) {
            throw new IllegalArgumentException("세션과 결제를 동시에 참조할 수 없습니다");
        }
        switch (type.reference()) {
            case SESSION -> {
                if (sessionId == null) throw new IllegalArgumentException(type + "는 세션 id가 필요합니다");
            }
            case PAYMENT -> {
                if (paymentId == null) throw new IllegalArgumentException(type + "는 결제 id가 필요합니다");
            }
            case ADMIN -> {
                if (memo == null || memo.isBlank()) throw new IllegalArgumentException(type + "는 사유(memo)가 필요합니다");
            }
        }
    }

    // ===== 만들기 편하게 + 멱등 키 규칙을 한곳에 =====

    /**
     * 세션 신청 참가비.
     * 취소 후 재신청하면 다시 내야 하므로, 키에 신청 시각을 넣어 신청마다 다른 키가 되게 한다.
     * (같은 키를 쓰면 재신청 때 "이미 처리됨"으로 공짜 신청이 된다)
     */
    public static GemCommand entryFee(Long memberId, Long sessionId, int amount, Instant appliedAt) {
        return new GemCommand(memberId, GemTransactionType.ENTRY_FEE, amount, sessionId, null, null,
                "ENTRY_FEE:s" + sessionId + ":m" + memberId + ":" + appliedAt.toEpochMilli());
    }

    /** 참가비 환불. 같은 신청(appliedAt)에 대한 환불은 한 번만 */
    public static GemCommand entryFeeRefund(Long memberId, Long sessionId, int amount, Instant appliedAt) {
        return new GemCommand(memberId, GemTransactionType.ENTRY_FEE_REFUND, amount, sessionId, null, null,
                "ENTRY_FEE_REFUND:s" + sessionId + ":m" + memberId + ":" + appliedAt.toEpochMilli());
    }

    /** 완주 반환. 세션당 한 번 */
    public static GemCommand completionRefund(Long memberId, Long sessionId, int amount) {
        return new GemCommand(memberId, GemTransactionType.COMPLETION_REFUND, amount, sessionId, null, null,
                "COMPLETION_REFUND:s" + sessionId + ":m" + memberId);
    }

    /** 보석 구매. 결제당 한 번 */
    public static GemCommand gemPurchase(Long memberId, Long paymentId, int amount) {
        return new GemCommand(memberId, GemTransactionType.GEM_PURCHASE, amount, null, paymentId, null,
                "GEM_PURCHASE:p" + paymentId);
    }

    /** 구매 환불 (지급했던 보석 회수). 결제당 한 번 */
    public static GemCommand purchaseRefund(Long memberId, Long paymentId, int amount) {
        return new GemCommand(memberId, GemTransactionType.PURCHASE_REFUND, amount, null, paymentId, null,
                "PURCHASE_REFUND:p" + paymentId);
    }

    /** 운영자 지급. requestId는 운영자 요청마다 새로 만든 값 (예: UUID) */
    public static GemCommand adminGrant(Long memberId, int amount, String memo, String requestId) {
        return new GemCommand(memberId, GemTransactionType.ADMIN_GRANT, amount, null, null, memo,
                "ADMIN_GRANT:" + requestId);
    }

    /** 운영자 회수 */
    public static GemCommand adminDeduct(Long memberId, int amount, String memo, String requestId) {
        return new GemCommand(memberId, GemTransactionType.ADMIN_DEDUCT, amount, null, null, memo,
                "ADMIN_DEDUCT:" + requestId);
    }
}
