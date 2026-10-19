package com.example.kiwi_backend.payment.infra.toss;

import com.example.kiwi_backend.common.exception.PaymentGatewayException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 토스페이먼츠 결제 API (https://docs.tosspayments.com/reference).
 *
 * 흐름: 프론트가 SDK로 결제창을 연다 → 사용자가 인증 → successUrl로 paymentKey가 돌아옴 → confirm → 필요하면 cancel
 * 결제창은 프론트가 직접 열기 때문에 서버 쪽 "결제 준비" 호출은 없다.
 *
 * 실패하면 PaymentGatewayException. 토스 원문 응답은 로그로만 남긴다.
 */
@Slf4j
@Component
@EnableConfigurationProperties(TossPaymentsProperties.class)
public class TossPaymentsClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    // 승인 API는 카드사 응답을 기다리느라 늦을 수 있어 넉넉히 기다린다
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final TossPaymentsProperties properties;

    @Autowired
    public TossPaymentsClient(TossPaymentsProperties properties) {
        this(RestClient.builder().requestFactory(requestFactory()), properties);
    }

    /** 테스트에서 가짜 서버를 붙이기 위한 생성자 */
    TossPaymentsClient(RestClient.Builder builder, TossPaymentsProperties properties) {
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
    }

    /** 결제 승인. 결제창 인증 후 successUrl로 받은 paymentKey가 필요하다 */
    public TossPaymentConfirmation confirm(String paymentKey, String orderId, int amount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("paymentKey", paymentKey);
        body.put("orderId", orderId);
        body.put("amount", amount);

        PaymentResponse response = post("/v1/payments/confirm", body);
        int total = response.totalAmount() == null ? 0 : response.totalAmount();
        return new TossPaymentConfirmation(response.paymentKey(), response.orderId(), response.status(), total, response.method());
    }

    /** 결제 취소 (전액) */
    public void cancel(String paymentKey, int cancelAmount, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cancelReason", reason);
        body.put("cancelAmount", cancelAmount);

        post("/v1/payments/" + paymentKey + "/cancel", body);
    }

    private PaymentResponse post(String path, Map<String, Object> body) {
        if (properties.secretKey() == null || properties.secretKey().isBlank()) {
            throw new PaymentGatewayException("결제 설정이 되어 있지 않습니다 (TOSS_SECRET_KEY)");
        }

        PaymentResponse response;
        try {
            response = restClient.post()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, authorization())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, res) -> {
                        String detail = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        log.warn("토스페이먼츠 요청 실패 path={} status={} body={}", path, res.getStatusCode().value(), detail);
                        throw new PaymentGatewayException("토스페이먼츠 결제 처리에 실패했습니다");
                    })
                    .body(PaymentResponse.class);
        } catch (RestClientException e) {
            log.warn("토스페이먼츠 호출 실패 path={}", path, e);
            throw new PaymentGatewayException("토스페이먼츠에 연결할 수 없습니다", e);
        }

        if (response == null) {
            throw new PaymentGatewayException("토스페이먼츠 응답이 비어 있습니다");
        }
        return response;
    }

    /** 시크릿 키 뒤에 콜론을 붙여 Base64로 인코딩한 Basic 인증 */
    private String authorization() {
        String encoded = Base64.getEncoder()
                .encodeToString((properties.secretKey() + ":").getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    // ===== 토스 Payment 객체 (필요한 필드만) =====

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PaymentResponse(
            String paymentKey,
            String orderId,
            String status,
            Integer totalAmount,
            String method
    ) {
    }
}
