package com.example.kiwi_backend.contract.query;

import com.example.kiwi_backend.contract.dto.PaymentSummary;

import java.util.Collection;
import java.util.Map;

/** 결제 요약 조회. payment가 구현하고 wallet이 보석 내역(구매·구매 환불)에 붙여 쓴다 */
public interface PaymentQueryService {

    /** paymentId → 요약. 없는 id는 결과에서 빠진다 */
    Map<Long, PaymentSummary> summaries(Collection<Long> paymentIds);
}
