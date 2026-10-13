package com.example.kiwi_backend.payment.api.dto;

import jakarta.validation.constraints.NotNull;

public record PaymentReadyRequest(
        @NotNull Long productId
) {
}
