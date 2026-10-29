package com.example.kiwi_backend.payment.api.dto;

/**
 * 결제 준비 결과. 프론트는 이 값으로 나이스페이 결제창(AUTHNICE.requestPay)을 연다.
 * 결제창에 금액을 넘겨야 하므로 amount를 내보낸다 (상점 화면에는 보석 개수만 보여준다)
 */
public record PaymentReadyResponse(
        String orderId,
        String goodsName,
        int amount,
        int gemAmount,
        String clientId,
        String returnUrl
) {
}
