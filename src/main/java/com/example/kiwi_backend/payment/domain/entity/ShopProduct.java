package com.example.kiwi_backend.payment.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * 상점에서 파는 보석 상품.
 * 가격을 바꿀 때는 이 행을 고치지 말고 기존 상품을 비활성화한 뒤 새 상품을 추가한다.
 * (지난 결제가 어떤 조건으로 팔렸는지는 payment에 스냅샷으로 남는다)
 */
@Entity
@Table(name = "shop_product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShopProduct {

    /** 식별자 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 상품명, 토스 결제창에 주문명(orderName)으로 그대로 보인다 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 기본 보석 */
    @Column(nullable = false)
    private int gemAmount;

    /** 보너스 보석 */
    @Column(nullable = false)
    private int bonusGemAmount;

    /** 가격 (원) */
    @Column(nullable = false)
    private int price;

    /** 할인 가격 (원), null이면 할인 없음 */
    private Integer salePrice;

    /** 판매 중 여부 */
    @Column(nullable = false)
    private boolean active;

    /** 등록 시각 */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static ShopProduct create(String name, int gemAmount, int bonusGemAmount, int price) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("상품명은 필수입니다");
        }
        if (gemAmount <= 0 || bonusGemAmount < 0 || price <= 0) {
            throw new IllegalArgumentException("보석 수량과 가격이 올바르지 않습니다");
        }
        ShopProduct product = new ShopProduct();
        product.name = name;
        product.gemAmount = gemAmount;
        product.bonusGemAmount = bonusGemAmount;
        product.price = price;
        product.active = true;
        return product;
    }

    /** 할인 적용. null이면 할인 해제 */
    public void changeSalePrice(Integer salePrice) {
        if (salePrice != null && (salePrice <= 0 || salePrice >= price)) {
            throw new IllegalArgumentException("할인 가격은 0보다 크고 원래 가격보다 작아야 합니다");
        }
        this.salePrice = salePrice;
    }

    public boolean isOnSale() {
        return salePrice != null;
    }

    /** 실제 결제 금액. 상점 표시와 결제 준비가 모두 이 값을 쓴다 */
    public int currentPrice() {
        return isOnSale() ? salePrice : price;
    }

    /** 구매 시 지급하는 보석 (기본 + 보너스) */
    public int totalGem() {
        return Math.addExact(gemAmount, bonusGemAmount);
    }
}
