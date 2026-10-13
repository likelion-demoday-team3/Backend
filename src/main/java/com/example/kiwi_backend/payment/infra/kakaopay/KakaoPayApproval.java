package com.example.kiwi_backend.payment.infra.kakaopay;

/** 결제 승인 결과. totalAmount로 실제 결제된 금액을 검증한다 */
public record KakaoPayApproval(
        String aid,
        String tid,
        int totalAmount,
        String paymentMethodType
) {
}
