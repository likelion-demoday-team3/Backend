package com.example.kiwi_backend.payment.api.dto;

import com.example.kiwi_backend.payment.domain.entity.Payment;
import com.example.kiwi_backend.payment.domain.enumerated.PaymentStatus;

import java.time.Instant;

/**
 * 내 결제 내역.
 * refundable은 기간·상태 기준이다. 보석을 이미 써서 회수할 수 없으면 환불 요청 때 거절된다.
 * amount는 실제 결제 금액(원)이다.
 */
public record PaymentHistoryResponse(
        String orderId,
        int amount,
        String productName,
        int grantedGem,
        PaymentStatus status,
        boolean refundable,
        Instant paidAt,
        Instant canceledAt,
        Instant createdAt
) {

    public static PaymentHistoryResponse of(Payment payment, String productName, boolean refundable) {
        return new PaymentHistoryResponse(
                payment.getOrderId(),
                payment.getAmount(),
                productName,
                payment.getGrantedGem(),
                payment.getStatus(),
                refundable,
                payment.getPaidAt(),
                payment.getCanceledAt(),
                payment.getCreatedAt()
        );
    }
}
