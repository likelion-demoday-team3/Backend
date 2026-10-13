package com.example.kiwi_backend.payment.domain.repository;

import com.example.kiwi_backend.payment.domain.entity.Payment;
import com.example.kiwi_backend.payment.domain.enumerated.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * 결제 행을 잠근다. 같은 결제의 승인·취소·환불이 한 번에 하나씩 처리된다.
     * 잠금 순서는 항상 결제 → 지갑.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.orderId = :orderId")
    Optional<Payment> findByOrderIdForUpdate(@Param("orderId") String orderId);

    /** 최신순. 같은 시각이면 id가 큰 것이 먼저 */
    Page<Payment> findByMemberIdAndStatusInOrderByCreatedAtDescIdDesc(Long memberId,
                                                                      Collection<PaymentStatus> statuses,
                                                                      Pageable pageable);
}
