package com.example.kiwi_backend.payment.api.dto;

import jakarta.validation.constraints.NotBlank;

/** 결제창에서 돌아온 approval_url의 주문 번호와 pg_token */
public record PaymentApproveRequest(
        @NotBlank String orderId,
        @NotBlank String pgToken
) {
}
