package com.example.kiwi_backend.contract.dto;

/** 보석 내역에 붙여 보여줄 결제 요약. amount는 원 단위 결제 금액 */
public record PaymentSummary(
        Long paymentId,
        String orderId,
        int amount
) {
}
