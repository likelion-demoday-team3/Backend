package com.example.kiwi_backend.payment.infra.nicepay;

import com.example.kiwi_backend.common.exception.PaymentGatewayException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static com.example.kiwi_backend.payment.infra.nicepay.NicePayClient.sha256Hex;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NicePayClientTest {

    private static final String BASE_URL = "https://sandbox-api.nicepay.co.kr";
    private static final String CLIENT_ID = "client-1";
    private static final String SECRET = "secret-1";
    /** 2026-10-13T00:00:00Z를 한국 시간으로 */
    private static final String EDI_DATE = "2026-10-13T09:00:00+09:00";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-13T00:00:00Z"), ZoneOffset.UTC);

    private final NicePayProperties properties = new NicePayProperties(
            BASE_URL, CLIENT_ID, SECRET,
            "http://localhost:8080/payments/nicepay/return",
            "http://localhost:3000/shop/payment/{orderId}");

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    @Test
    void checkoutReturnsClientIdAndReturnUrl() {
        NicePayClient client = new NicePayClient(builder, properties, CLOCK);

        assertThat(client.checkout()).isEqualTo(
                new NicePayCheckout(CLIENT_ID, "http://localhost:8080/payments/nicepay/return"));
        assertThat(properties.resultUrlFor("order-1")).isEqualTo("http://localhost:3000/shop/payment/order-1");
    }

    @Test
    void authSignatureMatchesOnlyWithSecretKey() {
        NicePayClient client = new NicePayClient(builder, properties, CLOCK);
        String signature = sha256Hex("auth-token" + CLIENT_ID + "3000" + SECRET);

        assertThat(client.verifyAuthSignature("auth-token", "3000", signature)).isTrue();
        assertThat(client.verifyAuthSignature("auth-token", "3000", signature.toUpperCase())).isTrue();
        assertThat(client.verifyAuthSignature("auth-token", "100", signature)).isFalse();
        assertThat(client.verifyAuthSignature("auth-token", "3000", sha256Hex("auth-token" + CLIENT_ID + "3000" + "other"))).isFalse();
        assertThat(client.verifyAuthSignature("auth-token", "3000", null)).isFalse();
    }

    @Test
    void approveSendsSignedRequestWithBasicAuth() {
        NicePayClient client = new NicePayClient(builder, properties, CLOCK);
        String basic = Base64.getEncoder().encodeToString((CLIENT_ID + ":" + SECRET).getBytes(StandardCharsets.UTF_8));

        server.expect(requestTo(BASE_URL + "/v1/payments/T123"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic " + basic))
                .andExpect(jsonPath("$.amount").value(3000))
                .andExpect(jsonPath("$.ediDate").value(EDI_DATE))
                .andExpect(jsonPath("$.signData").value(sha256Hex("T123" + 3000 + EDI_DATE + SECRET)))
                .andRespond(withSuccess("""
                        {"resultCode":"0000","resultMsg":"정상 처리되었습니다.","tid":"T123","orderId":"order-1",
                         "status":"paid","amount":3000,"payMethod":"card","ediDate":"2026-10-13T09:00:01.000+0900",
                         "signature":"%s"}
                        """.formatted(sha256Hex("T123" + 3000 + "2026-10-13T09:00:01.000+0900" + SECRET)),
                        MediaType.APPLICATION_JSON));

        NicePayApproval approval = client.approve("T123", 3000);

        assertThat(approval).isEqualTo(new NicePayApproval("T123", "order-1", 3000, "card"));
        server.verify();
    }

    @Test
    void approveFailsWhenResultCodeIsNotSuccess() {
        NicePayClient client = new NicePayClient(builder, properties, CLOCK);

        server.expect(requestTo(BASE_URL + "/v1/payments/T123"))
                .andRespond(withSuccess("""
                        {"resultCode":"A123","resultMsg":"거래 실패","tid":"T123","status":"failed","amount":3000}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.approve("T123", 3000))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessage("나이스페이 결제 처리에 실패했습니다");
    }

    @Test
    void approveFailsWhenResponseSignatureIsForged() {
        NicePayClient client = new NicePayClient(builder, properties, CLOCK);

        server.expect(requestTo(BASE_URL + "/v1/payments/T123"))
                .andRespond(withSuccess("""
                        {"resultCode":"0000","tid":"T123","orderId":"order-1","status":"paid","amount":3000,
                         "ediDate":"2026-10-13T09:00:01.000+0900","signature":"forged"}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.approve("T123", 3000)).isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    void cancelSendsReasonOrderIdAndSignature() {
        NicePayClient client = new NicePayClient(builder, properties, CLOCK);

        server.expect(requestTo(BASE_URL + "/v1/payments/T123/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.reason").value("사용자 환불 요청"))
                .andExpect(jsonPath("$.orderId").value("order-1"))
                .andExpect(jsonPath("$.cancelAmt").doesNotExist())
                .andExpect(jsonPath("$.signData").value(sha256Hex("T123" + EDI_DATE + SECRET)))
                .andRespond(withSuccess("""
                        {"resultCode":"0000","resultMsg":"취소 성공","tid":"T123","orderId":"order-1",
                         "status":"cancelled","amount":3000}
                        """, MediaType.APPLICATION_JSON));

        client.cancel("T123", "order-1", "사용자 환불 요청");

        server.verify();
    }

    @Test
    void errorResponseBecomesPaymentGatewayException() {
        NicePayClient client = new NicePayClient(builder, properties, CLOCK);

        server.expect(requestTo(BASE_URL + "/v1/payments/T123/cancel"))
                .andRespond(withBadRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"resultCode\":\"U104\",\"resultMsg\":\"인증 실패\"}"));

        assertThatThrownBy(() -> client.cancel("T123", "order-1", "환불"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessage("나이스페이 결제 처리에 실패했습니다");
    }

    @Test
    void missingKeysFailWithoutCallingNicePay() {
        NicePayProperties noKey = new NicePayProperties(BASE_URL, "", "", "a", "b/{orderId}");
        NicePayClient client = new NicePayClient(builder, noKey, CLOCK);

        assertThatThrownBy(client::checkout).isInstanceOf(PaymentGatewayException.class);
        assertThatThrownBy(() -> client.cancel("T123", "order-1", "환불")).isInstanceOf(PaymentGatewayException.class);
        server.verify();   // 요청이 하나도 나가지 않아야 함
    }
}
