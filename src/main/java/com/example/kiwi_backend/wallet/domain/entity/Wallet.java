package com.example.kiwi_backend.wallet.domain.entity;

import com.example.kiwi_backend.common.entity.BaseTimeEntity;
import com.example.kiwi_backend.common.exception.InsufficientGemException;
import com.example.kiwi_backend.contract.dto.GemTransactionType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원별 보석 잔액.
 * 잔액은 earn, spend(또는 apply)로만 바뀐다. setter 없음.
 */
@Entity
@Table(name = "wallet")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wallet extends BaseTimeEntity {

    /** 식별자 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 지갑 주인 1:1. 다른 모듈의 엔티티라 연관관계 없이 id만 */
    @Column(nullable = false, unique = true, updatable = false)
    private Long memberId;

    /** 현재 보유 보석 */
    @Column(nullable = false)
    private int gemBalance;

    /** 낙관적 락. 비관적 락으로 순서를 보장하지만, 혹시 락 없이 수정되는 경로가 생겨도 덮어쓰기를 막는 안전장치 */
    @Version
    @Column(nullable = false)
    private Long version;

    public static Wallet create(Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("회원 id는 필수입니다");
        }
        Wallet wallet = new Wallet();
        wallet.memberId = memberId;
        wallet.gemBalance = 0;
        return wallet;
    }

    /** 타입의 방향에 따라 증가 또는 감소 */
    public void apply(GemTransactionType type, int amount) {
        if (type.isCredit()) {
            earn(amount);
        } else {
            spend(amount);
        }
    }

    public void earn(int amount) {
        requirePositive(amount);
        this.gemBalance = Math.addExact(this.gemBalance, amount);   // int 범위를 넘으면 예외
    }

    public void spend(int amount) {
        requirePositive(amount);
        if (this.gemBalance < amount) {
            throw new InsufficientGemException(this.gemBalance, amount);
        }
        this.gemBalance -= amount;
    }

    private static void requirePositive(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("보석 변동량은 1 이상이어야 합니다");
        }
    }
}
