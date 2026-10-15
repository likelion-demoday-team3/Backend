package com.example.kiwi_backend.payment.api.dto;

import com.example.kiwi_backend.payment.domain.entity.ShopProduct;

/**
 * 상점 상품.
 * gemAmount = 실제 지급량 (기본 + 보너스)
 * price = 원래 가격(원), salePrice = 할인 가격(원, 할인 없으면 null)
 * 실제 결제 금액은 salePrice가 있으면 salePrice, 없으면 price
 */
public record ShopProductResponse(
        Long productId,
        String name,
        int gemAmount,
        int price,
        Integer salePrice
) {

    public static ShopProductResponse from(ShopProduct product) {
        return new ShopProductResponse(
                product.getId(),
                product.getName(),
                product.totalGem(),
                product.getPrice(),
                product.getSalePrice()
        );
    }
}
