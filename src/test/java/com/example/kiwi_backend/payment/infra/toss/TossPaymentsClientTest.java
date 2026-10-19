package com.example.kiwi_backend.payment.infra.toss;

import com.example.kiwi_backend.common.exception.PaymentGatewayException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TossPaymentsClientTest {

    private static final String BASE_URL = "https://api.tosspayments.com";
    // "test_sk_key:" 를 Base64로
    private static final String BASIC_AUTH = "Basic dGVzdF9za19rZXk6";

    private final TossPaymentsProperties properties = new TossPaymentsProperties(
            BASE_URL, "test_sk_key", "test_ck_key",
            "http://localhost:3000/shop/payment/{orderId}/success",
            "http://localhost:3000/shop/payment/{orderId}/fail");

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    @Test
    void confirmSendsPaymentKeyAndReadsTotalAmount() {
        TossPaymentsClient client = new TossPaymentsClient(builder, properties);

        server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", BASIC_AUTH))
                .andExpect(jsonPath("$.paymentKey").value("pk-1"))
                .andExpect(jsonPath("$.orderId").value("order-1"))
                .andExpect(jsonPath("$.amount").value(3000))
                .andRespond(withSuccess("""
                        {"mId":"tosspayments","paymentKey":"pk-1","orderId":"order-1","orderName":"보석 30개",
                         "status":"DONE","totalAmount":3000,"balanceAmount":3000,"method":"카드"}
                        """, MediaType.APPLICATION_JSON));

        TossPaymentConfirmation confirmation = client.confirm("pk-1", "order-1", 3000);

        assertThat(confirmation).isEqualTo(new TossPaymentConfirmation("pk-1", "order-1", "DONE", 3000, "카드"));
        server.verify();
    }

    @Test
    void cancelPostsToPaymentKeyPath() {
        TossPaymentsClient client = new TossPaymentsClient(builder, properties);

        server.expect(requestTo(BASE_URL + "/v1/payments/pk-1/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", BASIC_AUTH))
                .andExpect(jsonPath("$.cancelReason").value("사용자 환불 요청"))
                .andExpect(jsonPath("$.cancelAmount").value(3000))
                .andRespond(withSuccess("""
                        {"paymentKey":"pk-1","orderId":"order-1","status":"CANCELED","totalAmount":3000}
                        """, MediaType.APPLICATION_JSON));

        client.cancel("pk-1", 3000, "사용자 환불 요청");

        server.verify();
    }

    @Test
    void errorResponseBecomesPaymentGatewayException() {
        TossPaymentsClient client = new TossPaymentsClient(builder, properties);

        server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
                .andRespond(withBadRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"ALREADY_PROCESSED_PAYMENT\",\"message\":\"이미 처리된 결제 입니다.\"}"));

        assertThatThrownBy(() -> client.confirm("pk-1", "order-1", 3000))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessage("토스페이먼츠 결제 처리에 실패했습니다");
    }

    @Test
    void missingSecretKeyFailsWithoutCallingToss() {
        TossPaymentsProperties noKey = new TossPaymentsProperties(BASE_URL, "", "test_ck_key", "a", "b");
        TossPaymentsClient client = new TossPaymentsClient(builder, noKey);

        assertThatThrownBy(() -> client.cancel("pk-1", 3000, "환불")).isInstanceOf(PaymentGatewayException.class);
        server.verify();   // 요청이 하나도 나가지 않아야 함
    }
}
