package com.example.kiwi_backend.payment.infra.nicepay;

/** 결제 승인 결과. amount로 실제 결제된 금액을 검증한다 */
public record NicePayApproval(
        String tid,
        String orderId,
        int amount,
        String payMethod
) {
}
