package com.example.kiwi_backend.payment.api.dto;

/**
 * 결제 준비 결과. 프론트는 이 값으로 토스 SDK 결제창을 연다.
 *   TossPayments(clientKey).payment({ customerKey: TossPayments.ANONYMOUS })
 *     .requestPayment({ method: "CARD", amount: { currency: "KRW", value: amount }, orderId, orderName, successUrl, failUrl })
 * amount는 결제창 호출에만 쓰고 상점 화면에는 보석 개수만 보여준다.
 */
public record PaymentReadyResponse(
        String orderId,
        String orderName,
        int amount,
        int gemAmount,
        String clientKey,
        String successUrl,
        String failUrl
) {
}
