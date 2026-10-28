package com.example.kiwi_backend.payment.api;

import com.example.kiwi_backend.payment.api.dto.NicePayReturnRequest;
import com.example.kiwi_backend.payment.api.dto.PaymentApproveResponse;
import com.example.kiwi_backend.payment.application.PaymentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 나이스페이가 브라우저로 보내는 returnUrl: 로그인 없이 받아 form을 읽고 프론트 결과 페이지로 보낸다 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NicePayReturnEndpointTest {

    private static final String ORDER_ID = "0f8fad5b-d9cb-469f-a165-70867728950e";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PaymentService paymentService;

    @Test
    void approvedPaymentRedirectsToFrontendSuccessWithoutLogin() throws Exception {
        when(paymentService.approve(any())).thenReturn(new PaymentApproveResponse(ORDER_ID, 33, 133));

        mockMvc.perform(post("/payments/nicepay/return")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("authResultCode", "0000")
                        .param("authResultMsg", "인증 성공")
                        .param("tid", "T123")
                        .param("clientId", "client-1")
                        .param("orderId", ORDER_ID)
                        .param("amount", "3000")
                        .param("authToken", "auth-token")
                        .param("signature", "sig"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location",
                        "http://localhost:3000/shop/payment/" + ORDER_ID + "/success?grantedGem=33&gemBalance=133"));

        ArgumentCaptor<NicePayReturnRequest> request = ArgumentCaptor.forClass(NicePayReturnRequest.class);
        verify(paymentService).approve(request.capture());
        assertThat(request.getValue()).isEqualTo(new NicePayReturnRequest(
                "0000", "인증 성공", "T123", "client-1", ORDER_ID, "3000", "auth-token", "sig"));
    }

    @Test
    void failedAuthenticationMarksFailedAndRedirectsToFrontendFailure() throws Exception {
        mockMvc.perform(post("/payments/nicepay/return")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("authResultCode", "9999")
                        .param("authResultMsg", "사용자 취소")
                        .param("orderId", ORDER_ID))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location",
                        "http://localhost:3000/shop/payment/" + ORDER_ID + "/fail?code=9999&message=%EC%82%AC%EC%9A%A9%EC%9E%90%20%EC%B7%A8%EC%86%8C"));

        verify(paymentService).failAuthentication(ORDER_ID, "사용자 취소");
        verify(paymentService, never()).approve(any());
    }

    @Test
    void rejectedApprovalRedirectsToFrontendFailureWithReason() throws Exception {
        when(paymentService.approve(any())).thenThrow(new IllegalStateException("결제 인증 정보를 확인할 수 없습니다"));

        mockMvc.perform(post("/payments/nicepay/return")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("authResultCode", "0000")
                        .param("orderId", ORDER_ID)
                        .param("amount", "3000"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.startsWith("http://localhost:3000/shop/payment/" + ORDER_ID + "/fail?code=APPROVE_FAILED")));
    }

    @Test
    void unexpectedOrderIdIsNeverPutInRedirectPath() throws Exception {
        mockMvc.perform(post("/payments/nicepay/return")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("authResultCode", "0000")
                        .param("orderId", "../../evil")
                        .param("amount", "3000"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.startsWith("http://localhost:3000/shop/payment/unknown/fail")));

        verify(paymentService, never()).approve(any());
        verify(paymentService, never()).failAuthentication(anyString(), any());
    }
}
