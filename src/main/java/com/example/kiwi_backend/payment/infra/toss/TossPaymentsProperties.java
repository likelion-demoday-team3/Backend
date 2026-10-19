package com.example.kiwi_backend.payment.infra.toss;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.toss 설정.
 *
 * secretKey: 토스페이먼츠 개발자센터 > API 키 > 시크릿 키. 서버에만 둔다. 테스트 키(test_sk_...)는 실제 돈이 빠지지 않는다
 * clientKey: 같은 화면의 클라이언트 키. 프론트 결제창 SDK에 쓰이며 공개돼도 된다. 시크릿 키와 같은 쌍(테스트/라이브)이어야 한다
 * successUrl, failUrl: 사용자가 결제창에서 돌아올 프론트 주소. {orderId}가 주문 번호로 바뀐다.
 *   성공 시 토스가 뒤에 ?paymentKey=...&orderId=...&amount=... 를 붙여서 보낸다.
 */
@ConfigurationProperties("app.toss")
public record TossPaymentsProperties(
        String baseUrl,
        String secretKey,
        String clientKey,
        String successUrl,
        String failUrl
) {

    private static final String ORDER_ID_PLACEHOLDER = "{orderId}";

    public String successUrlFor(String orderId) {
        return successUrl.replace(ORDER_ID_PLACEHOLDER, orderId);
    }

    public String failUrlFor(String orderId) {
        return failUrl.replace(ORDER_ID_PLACEHOLDER, orderId);
    }
}
