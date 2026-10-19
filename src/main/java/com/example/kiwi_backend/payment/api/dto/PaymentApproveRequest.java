package com.example.kiwi_backend.payment.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 결제창에서 돌아온 successUrl의 쿼리 값 그대로 (paymentKey, orderId, amount) */
public record PaymentApproveRequest(
        @NotBlank String orderId,
        @NotBlank String paymentKey,
        @NotNull Integer amount
) {
}
