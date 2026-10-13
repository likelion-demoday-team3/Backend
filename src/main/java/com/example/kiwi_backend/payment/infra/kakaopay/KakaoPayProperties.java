package com.example.kiwi_backend.payment.infra.kakaopay;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.kakaopay 설정.
 *
 * secretKey: developers.kakaopay.com > 내 애플리케이션 > Secret key(dev). 테스트 결제는 dev 키 + cid TC0ONETIME
 * approvalUrl 등: 사용자가 결제창에서 돌아올 프론트 주소. {orderId}가 주문 번호로 바뀐다.
 *   승인 시 카카오페이가 뒤에 ?pg_token=... 을 붙여서 보낸다.
 */
@ConfigurationProperties("app.kakaopay")
public record KakaoPayProperties(
        String baseUrl,
        String secretKey,
        String cid,
        String approvalUrl,
        String cancelUrl,
        String failUrl
) {

    private static final String ORDER_ID_PLACEHOLDER = "{orderId}";

    public String approvalUrlFor(String orderId) {
        return approvalUrl.replace(ORDER_ID_PLACEHOLDER, orderId);
    }

    public String cancelUrlFor(String orderId) {
        return cancelUrl.replace(ORDER_ID_PLACEHOLDER, orderId);
    }

    public String failUrlFor(String orderId) {
        return failUrl.replace(ORDER_ID_PLACEHOLDER, orderId);
    }
}
