package com.example.kiwi_backend.wallet.api.dto;

import com.example.kiwi_backend.contract.dto.GemTransactionType;
import com.example.kiwi_backend.contract.dto.PaymentSummary;
import com.example.kiwi_backend.wallet.domain.entity.WalletHistory;

import java.time.Instant;

/**
 * 사용자에게 보여주는 보석 내역. 운영자 메모(memo)와 멱등 키는 내보내지 않는다.
 * 보석 구매·구매 환불이면 orderId와 paymentAmount(원)를 함께 내려서, 프론트가 결제 상세와 정확히 연결하게 한다.
 * 결제와 관계없는 내역이면 둘 다 null.
 */
public record WalletHistoryResponse(
        GemTransactionType type,
        String description,
        int amount,
        int balanceAfter,
        Long sessionId,
        Instant createdAt,
        String orderId,
        Integer paymentAmount
) {

    public static WalletHistoryResponse from(WalletHistory history, PaymentSummary payment) {
        return new WalletHistoryResponse(
                history.getType(),
                describe(history.getType()),
                history.getAmount(),
                history.getBalanceAfter(),
                history.getSessionId(),
                history.getCreatedAt(),
                payment == null ? null : payment.orderId(),
                payment == null ? null : payment.amount()
        );
    }

    private static String describe(GemTransactionType type) {
        return switch (type) {
            case HEART_SEND -> "하트 보내기";
            case CONTACT_SEND -> "연락처 보내기";
            case CONTACT_REQUEST -> "연락처 요청 수락";
            case RESULT_PURCHASE -> "매칭 결과 열람";
            case ENTRY_FEE -> "세션 참가비";
            case ENTRY_FEE_REFUND -> "참가비 환불";
            case COMPLETION_REFUND -> "완주 반환";
            case GEM_PURCHASE -> "보석 구매";
            case PURCHASE_REFUND -> "구매 환불";
            case ADMIN_GRANT -> "운영자 지급";
            case ADMIN_DEDUCT -> "운영자 회수";
        };
    }
}
