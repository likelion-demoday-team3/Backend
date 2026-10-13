package com.example.kiwi_backend.payment.domain.enumerated;

/**
 * 결제 상태.
 *
 * READY ──승인──▶ PAID ──환불──▶ REFUNDED
 *   └──취소·실패──▶ FAILED
 */
public enum PaymentStatus {
    READY,      // 결제창을 열었고 사용자 승인 대기
    PAID,       // 결제 완료, 보석 지급됨
    FAILED,     // 사용자가 결제창에서 취소했거나 승인 실패
    REFUNDED    // 결제 취소, 보석 회수됨
}
