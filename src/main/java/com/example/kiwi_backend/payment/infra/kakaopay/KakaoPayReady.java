package com.example.kiwi_backend.payment.infra.kakaopay;

/**
 * 결제 준비 결과.
 * 프론트는 기기에 맞는 URL로 사용자를 보낸다 (PC 웹: pcUrl, 모바일 웹: mobileUrl, 앱: appUrl).
 */
public record KakaoPayReady(
        String tid,
        String pcUrl,
        String mobileUrl,
        String appUrl
) {
}
