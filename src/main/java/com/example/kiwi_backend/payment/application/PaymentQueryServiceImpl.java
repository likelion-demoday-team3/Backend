package com.example.kiwi_backend.payment.application;

import com.example.kiwi_backend.contract.dto.PaymentSummary;
import com.example.kiwi_backend.contract.query.PaymentQueryService;
import com.example.kiwi_backend.payment.domain.entity.Payment;
import com.example.kiwi_backend.payment.domain.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** wallet의 보석 내역에 주문 번호·결제 금액을 붙이기 위한 조회 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentQueryServiceImpl implements PaymentQueryService {

    private final PaymentRepository paymentRepository;

    @Override
    public Map<Long, PaymentSummary> summaries(Collection<Long> paymentIds) {
        if (paymentIds == null || paymentIds.isEmpty()) {
            return Map.of();
        }
        return paymentRepository.findAllById(paymentIds).stream()
                .map(PaymentQueryServiceImpl::summary)
                .collect(Collectors.toMap(PaymentSummary::paymentId, Function.identity()));
    }

    private static PaymentSummary summary(Payment payment) {
        return new PaymentSummary(payment.getId(), payment.getOrderId(), payment.getAmount());
    }
}
