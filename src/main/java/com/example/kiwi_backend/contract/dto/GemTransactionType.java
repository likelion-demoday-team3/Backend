package com.example.kiwi_backend.contract.dto;

/**
 * 보석 변동 사유. session, 결제 등 다른 모듈이 wallet에 요청할 때 쓰므로 contract에 둔다.
 *
 * 방향(증가/감소)을 타입이 알고 있어서, 요청하는 쪽은 항상 양수 금액만 넘긴다.
 * 어떤 기록을 반드시 함께 넘겨야 하는지(reference)도 타입이 정한다.
 */
public enum GemTransactionType {

    HEART_SEND(false, Reference.SESSION),
    CONTACT_SEND(false, Reference.SESSION),
    CONTACT_REQUEST(false, Reference.SESSION),
    RESULT_PURCHASE(false, Reference.SESSION),

    ENTRY_FEE(false, Reference.SESSION),           // 세션 신청 참가비
    ENTRY_FEE_REFUND(true, Reference.SESSION),     // 본인 취소, 세션 취소 환불
    COMPLETION_REFUND(true, Reference.SESSION),    // 투표 완료 시 참가비 일부 반환
    GEM_PURCHASE(true, Reference.PAYMENT),         // 보석 구매
    PURCHASE_REFUND(false, Reference.PAYMENT),     // 구매 환불
    ADMIN_GRANT(true, Reference.ADMIN),            // 운영자 지급
    ADMIN_DEDUCT(false, Reference.ADMIN);          // 운영자 회수

    public enum Reference {
        SESSION,   // sessionId 필수
        PAYMENT,   // paymentId 필수
        ADMIN      // memo 필수
    }

    private final boolean credit;
    private final Reference reference;

    GemTransactionType(boolean credit, Reference reference) {
        this.credit = credit;
        this.reference = reference;
    }

    /** true면 증가, false면 감소 */
    public boolean isCredit() {
        return credit;
    }

    public Reference reference() {
        return reference;
    }
}
