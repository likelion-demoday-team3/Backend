package com.example.kiwi_backend.payment.infra.nicepay;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.nicepay 설정.
 *
 * clientId, secretKey: start.nicepay.co.kr > 개발정보 > API KEY. 샌드박스와 운영 키가 다르다
 *   (샌드박스 키는 baseUrl을 sandbox-api.nicepay.co.kr로, 운영 키는 api.nicepay.co.kr로)
 * returnUrl: 결제창 인증이 끝나면 나이스페이가 브라우저로 form POST 하는 백엔드 주소 (/payments/nicepay/return)
 * resultUrl: 승인 처리 후 사용자를 보낼 프론트 주소. {orderId}가 주문 번호로 바뀌고 뒤에 /success 또는 /fail이 붙는다
 */
@ConfigurationProperties("app.nicepay")
public record NicePayProperties(
        String baseUrl,
        String clientId,
        String secretKey,
        String returnUrl,
        String resultUrl
) {

    private static final String ORDER_ID_PLACEHOLDER = "{orderId}";

    public String resultUrlFor(String orderId) {
        return resultUrl.replace(ORDER_ID_PLACEHOLDER, orderId);
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank() && secretKey != null && !secretKey.isBlank();
    }
}
