package com.example.kiwi_backend.payment.infra.nicepay;

/** 프론트가 결제창(AUTHNICE.requestPay)을 열 때 필요한 가맹점 정보 */
public record NicePayCheckout(
        String clientId,
        String returnUrl
) {
}
