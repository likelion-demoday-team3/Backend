package com.example.kiwi_backend.payment.infra.kakaopay;

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

class KakaoPayClientTest {

    private static final String BASE_URL = "https://open-api.kakaopay.com";

    private final KakaoPayProperties properties = new KakaoPayProperties(
            BASE_URL, "test-key", "TC0ONETIME",
            "http://localhost:3000/shop/payment/{orderId}/success",
            "http://localhost:3000/shop/payment/{orderId}/cancel",
            "http://localhost:3000/shop/payment/{orderId}/fail");

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    @Test
    void readySendsOrderAndReturnsRedirectUrls() {
        KakaoPayClient client = new KakaoPayClient(builder, properties);

        server.expect(requestTo(BASE_URL + "/online/v1/payment/ready"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "SECRET_KEY test-key"))
                .andExpect(jsonPath("$.cid").value("TC0ONETIME"))
                .andExpect(jsonPath("$.partner_order_id").value("order-1"))
                .andExpect(jsonPath("$.partner_user_id").value("7"))
                .andExpect(jsonPath("$.total_amount").value(3000))
                .andExpect(jsonPath("$.approval_url").value("http://localhost:3000/shop/payment/order-1/success"))
                .andExpect(jsonPath("$.fail_url").value("http://localhost:3000/shop/payment/order-1/fail"))
                .andRespond(withSuccess("""
                        {"tid":"T123","next_redirect_pc_url":"https://pc","next_redirect_mobile_url":"https://m",
                         "next_redirect_app_url":"https://app","android_app_scheme":"x","created_at":"2026-10-13T10:00:00"}
                        """, MediaType.APPLICATION_JSON));

        KakaoPayReady ready = client.ready("order-1", 7L, "보석 30개", 3000);

        assertThat(ready).isEqualTo(new KakaoPayReady("T123", "https://pc", "https://m", "https://app"));
        server.verify();
    }

    @Test
    void approveReadsTotalAmount() {
        KakaoPayClient client = new KakaoPayClient(builder, properties);

        server.expect(requestTo(BASE_URL + "/online/v1/payment/approve"))
                .andExpect(jsonPath("$.tid").value("T123"))
                .andExpect(jsonPath("$.pg_token").value("pg-1"))
                .andRespond(withSuccess("""
                        {"aid":"A1","tid":"T123","payment_method_type":"MONEY",
                         "amount":{"total":3000,"tax_free":0,"vat":273,"point":0,"discount":0}}
                        """, MediaType.APPLICATION_JSON));

        KakaoPayApproval approval = client.approve("T123", "order-1", 7L, "pg-1");

        assertThat(approval.totalAmount()).isEqualTo(3000);
        assertThat(approval.paymentMethodType()).isEqualTo("MONEY");
        server.verify();
    }

    @Test
    void errorResponseBecomesPaymentGatewayException() {
        KakaoPayClient client = new KakaoPayClient(builder, properties);

        server.expect(requestTo(BASE_URL + "/online/v1/payment/cancel"))
                .andRespond(withBadRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error_code\":-721,\"error_message\":\"TID does not match\"}"));

        assertThatThrownBy(() -> client.cancel("T123", 3000))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessage("카카오페이 결제 처리에 실패했습니다");
    }

    @Test
    void missingSecretKeyFailsWithoutCallingKakao() {
        KakaoPayProperties noKey = new KakaoPayProperties(BASE_URL, "", "TC0ONETIME", "a", "b", "c");
        KakaoPayClient client = new KakaoPayClient(builder, noKey);

        assertThatThrownBy(() -> client.cancel("T123", 3000)).isInstanceOf(PaymentGatewayException.class);
        server.verify();   // 요청이 하나도 나가지 않아야 함
    }
}
