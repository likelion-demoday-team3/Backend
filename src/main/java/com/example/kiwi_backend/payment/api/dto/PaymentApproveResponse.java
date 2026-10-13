package com.example.kiwi_backend.payment.api.dto;

public record PaymentApproveResponse(
        String orderId,
        int grantedGem,
        int gemBalance
) {
}
