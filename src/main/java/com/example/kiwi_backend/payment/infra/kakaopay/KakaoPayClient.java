package com.example.kiwi_backend.payment.infra.kakaopay;

import com.example.kiwi_backend.common.exception.PaymentGatewayException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 카카오페이 단건 결제 API (https://developers.kakaopay.com/docs/payment/online/single-payment).
 *
 * 흐름: ready(tid 발급, 결제창 URL) → 사용자가 결제창에서 승인 → approve(pg_token) → 필요하면 cancel
 *
 * 실패하면 PaymentGatewayException. 카카오페이 원문 응답은 로그로만 남긴다.
 */
@Slf4j
@Component
@EnableConfigurationProperties(KakaoPayProperties.class)
public class KakaoPayClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final RestClient restClient;
    private final KakaoPayProperties properties;

    @Autowired
    public KakaoPayClient(KakaoPayProperties properties) {
        this(RestClient.builder().requestFactory(requestFactory()), properties);
    }

    /** 테스트에서 가짜 서버를 붙이기 위한 생성자 */
    KakaoPayClient(RestClient.Builder builder, KakaoPayProperties properties) {
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
    }

    /** 결제 준비. 결제창 URL과 tid를 받는다 */
    public KakaoPayReady ready(String orderId, Long memberId, String itemName, int totalAmount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cid", properties.cid());
        body.put("partner_order_id", orderId);
        body.put("partner_user_id", String.valueOf(memberId));
        body.put("item_name", itemName);
        body.put("quantity", 1);
        body.put("total_amount", totalAmount);
        body.put("tax_free_amount", 0);
        body.put("approval_url", properties.approvalUrlFor(orderId));
        body.put("cancel_url", properties.cancelUrlFor(orderId));
        body.put("fail_url", properties.failUrlFor(orderId));

        ReadyResponse response = post("/online/v1/payment/ready", body, ReadyResponse.class);
        return new KakaoPayReady(response.tid(), response.pcUrl(), response.mobileUrl(), response.appUrl());
    }

    /** 결제 승인. 사용자가 결제창에서 승인한 뒤 받은 pg_token이 필요하다 */
    public KakaoPayApproval approve(String tid, String orderId, Long memberId, String pgToken) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cid", properties.cid());
        body.put("tid", tid);
        body.put("partner_order_id", orderId);
        body.put("partner_user_id", String.valueOf(memberId));
        body.put("pg_token", pgToken);

        ApproveResponse response = post("/online/v1/payment/approve", body, ApproveResponse.class);
        int total = response.amount() == null ? 0 : response.amount().total();
        return new KakaoPayApproval(response.aid(), response.tid(), total, response.paymentMethodType());
    }

    /** 결제 취소 (전액) */
    public void cancel(String tid, int cancelAmount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cid", properties.cid());
        body.put("tid", tid);
        body.put("cancel_amount", cancelAmount);
        body.put("cancel_tax_free_amount", 0);

        post("/online/v1/payment/cancel", body, CancelResponse.class);
    }

    private <T> T post(String path, Map<String, Object> body, Class<T> responseType) {
        if (properties.secretKey() == null || properties.secretKey().isBlank()) {
            throw new PaymentGatewayException("결제 설정이 되어 있지 않습니다 (KAKAOPAY_SECRET_KEY)");
        }

        T response;
        try {
            response = restClient.post()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "SECRET_KEY " + properties.secretKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, res) -> {
                        String detail = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        log.warn("카카오페이 요청 실패 path={} status={} body={}", path, res.getStatusCode().value(), detail);
                        throw new PaymentGatewayException("카카오페이 결제 처리에 실패했습니다");
                    })
                    .body(responseType);
        } catch (RestClientException e) {
            log.warn("카카오페이 호출 실패 path={}", path, e);
            throw new PaymentGatewayException("카카오페이에 연결할 수 없습니다", e);
        }

        if (response == null) {
            throw new PaymentGatewayException("카카오페이 응답이 비어 있습니다");
        }
        return response;
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    // ===== 카카오페이 응답 (필요한 필드만) =====

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ReadyResponse(
            String tid,
            @JsonProperty("next_redirect_pc_url") String pcUrl,
            @JsonProperty("next_redirect_mobile_url") String mobileUrl,
            @JsonProperty("next_redirect_app_url") String appUrl
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApproveResponse(
            String aid,
            String tid,
            @JsonProperty("payment_method_type") String paymentMethodType,
            Amount amount
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Amount(int total) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CancelResponse(String tid, String status) {
    }
}
