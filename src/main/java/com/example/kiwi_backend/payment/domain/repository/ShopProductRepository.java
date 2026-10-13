package com.example.kiwi_backend.payment.domain.repository;

import com.example.kiwi_backend.payment.domain.entity.ShopProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShopProductRepository extends JpaRepository<ShopProduct, Long> {

    /** 판매 중인 상품, 싼 것부터 */
    List<ShopProduct> findByActiveTrueOrderByPriceAscIdAsc();
}
