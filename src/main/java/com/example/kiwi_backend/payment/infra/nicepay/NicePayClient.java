package com.example.kiwi_backend.payment.infra.nicepay;

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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 나이스페이먼츠 결제창 Server 승인 API (https://github.com/nicepayments/nicepay-manual).
 *
 * 흐름: 프론트가 AUTHNICE.requestPay로 결제창을 연다 → 인증이 끝나면 나이스페이가 returnUrl로 form POST
 *      → verifyAuthSignature로 위변조 확인 → approve(tid) → 필요하면 cancel
 *
 * 나이스페이는 실패도 HTTP 200에 resultCode로 알려주므로 resultCode가 "0000"이 아니면 실패로 본다.
 * 실패하면 PaymentGatewayException. 나이스페이 원문 응답은 로그로만 남긴다.
 */
@Slf4j
@Component
@EnableConfigurationProperties(NicePayProperties.class)
public class NicePayClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    private static final String SUCCESS = "0000";
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private final RestClient restClient;
    private final NicePayProperties properties;
    private final Clock clock;

    @Autowired
    public NicePayClient(NicePayProperties properties, Clock clock) {
        this(RestClient.builder().requestFactory(requestFactory()), properties, clock);
    }

    /** 테스트에서 가짜 서버를 붙이기 위한 생성자 */
    NicePayClient(RestClient.Builder builder, NicePayProperties properties, Clock clock) {
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
        this.clock = clock;
    }

    /** 결제창을 열 때 프론트에 넘길 가맹점 정보 */
    public NicePayCheckout checkout() {
        requireConfigured();
        return new NicePayCheckout(properties.clientId(), properties.returnUrl());
    }

    /**
     * returnUrl로 받은 인증 결과가 나이스페이에서 온 것인지 확인한다.
     * signature = hex(sha256(authToken + clientId + amount + secretKey))
     */
    public boolean verifyAuthSignature(String authToken, String amount, String signature) {
        requireConfigured();
        if (authToken == null || amount == null || signature == null) {
            return false;
        }
        String expected = sha256Hex(authToken + properties.clientId() + amount + properties.secretKey());
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    /** 결제 승인. signData = hex(sha256(tid + amount + ediDate + secretKey)) */
    public NicePayApproval approve(String tid, int amount) {
        String ediDate = ediDate();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amount);
        body.put("ediDate", ediDate);
        body.put("signData", sha256Hex(tid + amount + ediDate + properties.secretKey()));

        PaymentResponse response = post("/v1/payments/" + tid, body);
        if (!"paid".equals(response.status())) {
            log.warn("나이스페이 승인 응답 상태가 paid가 아님 tid={} status={}", tid, response.status());
            throw new PaymentGatewayException("나이스페이 결제 처리에 실패했습니다");
        }
        verifyResponseSignature(response);
        return new NicePayApproval(response.tid(), response.orderId(), response.amount(), response.payMethod());
    }

    /** 결제 취소 (전액). signData = hex(sha256(tid + ediDate + secretKey)) */
    public void cancel(String tid, String orderId, String reason) {
        String ediDate = ediDate();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", reason);
        body.put("orderId", orderId);
        body.put("ediDate", ediDate);
        body.put("signData", sha256Hex(tid + ediDate + properties.secretKey()));

        post("/v1/payments/" + tid + "/cancel", body);
    }

    private PaymentResponse post(String path, Map<String, Object> body) {
        requireConfigured();

        PaymentResponse response;
        try {
            response = restClient.post()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basicCredentials())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, res) -> {
                        String detail = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        log.warn("나이스페이 요청 실패 path={} status={} body={}", path, res.getStatusCode().value(), detail);
                        throw new PaymentGatewayException("나이스페이 결제 처리에 실패했습니다");
                    })
                    .body(PaymentResponse.class);
        } catch (RestClientException e) {
            log.warn("나이스페이 호출 실패 path={}", path, e);
            throw new PaymentGatewayException("나이스페이에 연결할 수 없습니다", e);
        }

        if (response == null) {
            throw new PaymentGatewayException("나이스페이 응답이 비어 있습니다");
        }
        if (!SUCCESS.equals(response.resultCode())) {
            log.warn("나이스페이 처리 실패 path={} resultCode={} resultMsg={}", path, response.resultCode(), response.resultMsg());
            throw new PaymentGatewayException("나이스페이 결제 처리에 실패했습니다");
        }
        return response;
    }

    /** 응답 위변조 확인. signature = hex(sha256(tid + amount + ediDate + secretKey)). 값이 없으면 건너뛴다 */
    private void verifyResponseSignature(PaymentResponse response) {
        if (response.signature() == null || response.ediDate() == null) {
            return;
        }
        String expected = sha256Hex(response.tid() + response.amount() + response.ediDate() + properties.secretKey());
        if (!expected.equalsIgnoreCase(response.signature())) {
            log.warn("나이스페이 응답 서명 불일치 tid={}", response.tid());
            throw new PaymentGatewayException("나이스페이 응답을 확인할 수 없습니다");
        }
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new PaymentGatewayException("결제 설정이 되어 있지 않습니다 (NICEPAY_CLIENT_ID, NICEPAY_SECRET_KEY)");
        }
    }

    private String basicCredentials() {
        String raw = properties.clientId() + ":" + properties.secretKey();
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String ediDate() {
        // toString()은 초가 0이면 초를 빼므로 형식을 고정한다. 예: 2026-10-13T09:00:00+09:00
        return OffsetDateTime.now(clock).withOffsetSameInstant(KST).truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    // ===== 나이스페이 응답 (승인·취소 공통, 필요한 필드만) =====

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PaymentResponse(
            String resultCode,
            String resultMsg,
            String tid,
            String orderId,
            String status,
            int amount,
            String payMethod,
            String ediDate,
            String signature
    ) {
    }
}
