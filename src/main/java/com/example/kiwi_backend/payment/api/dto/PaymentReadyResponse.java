package com.example.kiwi_backend.payment.api.dto;

/**
 * 결제 준비 결과. 프론트는 기기에 맞는 URL로 이동시킨다.
 * PC 웹: pcRedirectUrl, 모바일 웹: mobileRedirectUrl, 앱: appRedirectUrl
 * 현금 금액은 내보내지 않는다 (상점 화면에는 보석 개수만 보여준다)
 */
public record PaymentReadyResponse(
        String orderId,
        int gemAmount,
        String pcRedirectUrl,
        String mobileRedirectUrl,
        String appRedirectUrl
) {
}
