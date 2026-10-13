package com.example.kiwi_backend.payment.api.dto;

import com.example.kiwi_backend.payment.domain.entity.ShopProduct;

/**
 * 상점 상품. 화면에는 "보석 10개 구매"처럼 보석 개수만 보여주고 현금 가격은 내보내지 않는다.
 * gemAmount = 실제 지급량 (기본 + 보너스)
 */
public record ShopProductResponse(
        Long productId,
        String name,
        int gemAmount
) {

    public static ShopProductResponse from(ShopProduct product) {
        return new ShopProductResponse(
                product.getId(),
                product.getName(),
                product.totalGem()
        );
    }
}
