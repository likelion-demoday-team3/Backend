package com.example.kiwi_backend.payment.infra.toss;

/** 결제 승인 결과. totalAmount로 실제 결제된 금액을 검증한다 */
public record TossPaymentConfirmation(
        String paymentKey,
        String orderId,
        String status,
        int totalAmount,
        String method
) {
}
