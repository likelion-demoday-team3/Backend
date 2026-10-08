package com.example.kiwi_backend.wallet.domain.entity;

import com.example.kiwi_backend.contract.dto.GemTransactionType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/**
 * 보석 변동 내역. 한 번 기록하면 절대 수정하지 않는다.
 * 잘못 지급했으면 이 행을 고치지 않고 반대 방향 기록(ADMIN_DEDUCT 등)을 하나 더 남긴다.
 * @Immutable: Hibernate가 이 엔티티의 UPDATE를 아예 실행하지 않는다.
 */
@Entity
@Immutable
@Table(name = "wallet_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalletHistory {

    /** 식별자 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 대상 회원 */
    @Column(nullable = false)
    private Long memberId;

    /** 변동 사유 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GemTransactionType type;

    /** 변동량, 부호로 증감 표시 (+15, -15) */
    @Column(nullable = false)
    private int amount;

    /** 변동 직후 잔액, 정산 추적 */
    @Column(nullable = false)
    private int balanceAfter;

    /** 관련 세션 (참가비·환불·완주 반환) */
    private Long sessionId;

    /** 관련 결제 (보석 구매·구매 환불) */
    private Long paymentId;

    /** 운영자 처리 사유, 사용자에게는 보여주지 않음 */
    private String memo;

    /** 중복 처리 방지 */
    @Column(nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    /** 변동 시각 */
    @CreationTimestamp
    @Column(nullable = false)
    private Instant createdAt;

    public static WalletHistory record(Long memberId,
                                       GemTransactionType type,
                                       int unsignedAmount,
                                       int balanceAfter,
                                       Long sessionId,
                                       Long paymentId,
                                       String memo,
                                       String idempotencyKey) {
        WalletHistory history = new WalletHistory();
        history.memberId = memberId;
        history.type = type;
        history.amount = type.isCredit() ? unsignedAmount : -unsignedAmount;
        history.balanceAfter = balanceAfter;
        history.sessionId = sessionId;
        history.paymentId = paymentId;
        history.memo = memo;
        history.idempotencyKey = idempotencyKey;
        return history;
    }

    /** 같은 멱등 키로 다시 들어온 요청이 처음 요청과 같은 내용인지 */
    public boolean matches(Long memberId, GemTransactionType type, int unsignedAmount) {
        return this.memberId.equals(memberId)
                && this.type == type
                && Math.abs(this.amount) == unsignedAmount;
    }
}
