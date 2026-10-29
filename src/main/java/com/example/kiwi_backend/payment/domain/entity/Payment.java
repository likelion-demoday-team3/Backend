package com.example.kiwi_backend.payment.domain.entity;

import com.example.kiwi_backend.payment.domain.enumerated.PaymentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Duration;
import java.time.Instant;

/**
 * 보석 구매 결제 1건 (나이스페이).
 * 테이블에 updated_at이 없어 BaseTimeEntity를 상속하지 않는다.
 *
 * 금액과 지급 보석은 결제를 시작할 때의 상품 조건을 스냅샷으로 저장한다.
 * 그 뒤에 상품 가격이 바뀌어도 이 결제의 승인·환불 금액은 변하지 않는다.
 */
@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    /** 식별자 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 결제한 회원 */
    @Column(nullable = false, updatable = false)
    private Long memberId;

    /** 구매한 상품 */
    @Column(nullable = false, updatable = false)
    private Long productId;

    /** 우리가 만든 주문 번호. 나이스페이 orderId, 프론트와 주고받는 값 */
    @Column(nullable = false, unique = true, updatable = false, length = 100)
    private String orderId;

    /** 나이스페이 거래 키(tid). 결제창 인증 결과(returnUrl)로 받아 승인할 때 저장한다 */
    @Column(unique = true)
    private String paymentKey;

    /** 결제 금액 (원) */
    @Column(nullable = false, updatable = false)
    private int amount;

    /** 지급할 보석 (기본 + 보너스) */
    @Column(nullable = false, updatable = false)
    private int grantedGem;

    /** 결제 상태. 필드명과 컬럼명이 달라 name 지정 */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus status;

    /** 승인 시각 */
    private Instant paidAt;

    /** 실패·취소·환불 시각 */
    private Instant canceledAt;

    /** 실패·취소·환불 사유 */
    private String cancelReason;

    /** 결제 시작 시각 */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static Payment ready(Long memberId, ShopProduct product, String orderId) {
        if (memberId == null || product == null) {
            throw new IllegalArgumentException("회원과 상품은 필수입니다");
        }
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("주문 번호는 필수입니다");
        }
        Payment payment = new Payment();
        payment.memberId = memberId;
        payment.productId = product.getId();
        payment.orderId = orderId;
        payment.amount = product.currentPrice();
        payment.grantedGem = product.totalGem();
        payment.status = PaymentStatus.READY;
        return payment;
    }

    /** 나이스페이 인증 결과의 tid를 붙인다 */
    public void attachPaymentKey(String paymentKey) {
        if (status != PaymentStatus.READY || this.paymentKey != null) {
            throw new IllegalStateException("결제 대기 중에만 tid를 저장할 수 있습니다");
        }
        if (paymentKey == null || paymentKey.isBlank()) {
            throw new IllegalArgumentException("tid는 필수입니다");
        }
        this.paymentKey = paymentKey;
    }

    /** 승인 완료 */
    public void markPaid(Instant now) {
        if (status != PaymentStatus.READY) {
            throw new IllegalStateException("결제 대기 중인 건만 승인할 수 있습니다");
        }
        this.status = PaymentStatus.PAID;
        this.paidAt = now;
    }

    /** 사용자 취소 또는 승인 실패 */
    public void markFailed(Instant now, String reason) {
        if (status != PaymentStatus.READY) {
            throw new IllegalStateException("결제 대기 중인 건만 실패 처리할 수 있습니다");
        }
        this.status = PaymentStatus.FAILED;
        this.canceledAt = now;
        this.cancelReason = reason;
    }

    /** 환불 완료 */
    public void markRefunded(Instant now, String reason) {
        if (status != PaymentStatus.PAID) {
            throw new IllegalStateException("결제 완료된 건만 환불할 수 있습니다");
        }
        this.status = PaymentStatus.REFUNDED;
        this.canceledAt = now;
        this.cancelReason = reason;
    }

    /** 승인 요청을 받을 수 있는 상태인가 (아직 tid를 받지 않은 대기 중 결제) */
    public boolean isApprovable() {
        return status == PaymentStatus.READY && paymentKey == null;
    }

    /** 환불 기간 안의 완료된 결제인가. 보석을 이미 썼는지는 지갑에서 회수할 때 판단한다 */
    public boolean isRefundable(Instant now, Duration refundWindow) {
        return status == PaymentStatus.PAID && now.isBefore(paidAt.plus(refundWindow));
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }
}
