package com.example.kiwi_backend.payment.api.dto;

public record PaymentRefundResponse(
        String orderId,
        int revokedGem,
        int gemBalance
) {
}
